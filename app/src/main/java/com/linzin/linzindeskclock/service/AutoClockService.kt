package com.linzin.linzindeskclock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.linzin.linzindeskclock.MainActivity
import com.linzin.linzindeskclock.R
import com.linzin.linzindeskclock.autostart.*
import com.linzin.linzindeskclock.power.shouldPauseAutoClockForLowBattery
import com.linzin.linzindeskclock.settings.AutoClockPreferences
import java.lang.ref.WeakReference

/** User-enabled event monitor. System restart/boot restores listening, never synthesises a lock. */
class AutoClockService : Service() {
    private lateinit var monitor: LockStateMonitor
    private lateinit var scan: AutoClockTriggerController
    private lateinit var cycle: ClockCycleStore
    private val handler = Handler(Looper.getMainLooper())
    private var scanWindow: ClockLockWindow? = null
    private var scanProducedOffer = false
    private var candidate = WeakReference<ClockActivationHost.Host>(null)
    private val notifications get() = getSystemService(NotificationManager::class.java)
    override fun onCreate() {
        super.onCreate()
        cycle = ClockCycleStore(this)
        notifications.createNotificationChannel(NotificationChannel(MONITOR_CHANNEL,getString(R.string.auto_monitor_channel),NotificationManager.IMPORTANCE_LOW))
        notifications.createNotificationChannel(NotificationChannel(OFFER_CHANNEL,getString(R.string.auto_offer_channel),NotificationManager.IMPORTANCE_DEFAULT))
        val foldable = getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null ||
            packageManager.hasSystemFeature("android.hardware.sensor.hinge_angle")
        monitor = LockStateMonitor(this,onLock = {
            cycle.noteLock()
            val window = cycle.activeWindow()
            if(window != null && cycle.claimAttempt()) {
                candidate = WeakReference(ClockActivationHost.current())
                cancelOffer()
                scanWindow = window
                scanProducedOffer = false
                AutoClockPreferences(this).lastActivation="SCANNING"
                ClockDreamBridge.scans.begin(window)
                if(!scan.onLock(deadlineElapsedMs=window.deadline)) ClockDreamBridge.scans.cancel(window)
            }
        },onUnlock = {
            cycle.onUnlock(); cancelOffer(); scan.onUnlock(); candidate.clear()
        },onScreenOn = { if(monitor.activelyUnlocked) { cycle.onUnlock(); cancelOffer(); scan.onUnlock() } })
        scan = AutoClockTriggerController(this,foldable,
            mayScan = { AutoClockPreferences(this).enabled && !monitor.activelyUnlocked && scanWindow?.let(cycle::isCurrentWindow) == true },
            onEligible = {
                val window = scanWindow
                if(window != null && validWindow(window)) {
                    scanProducedOffer = true
                    val token = cycle.offer()
                    when(ClockDreamBridge.scans.deliver(window,token) { validWindow(window) }) {
                        PendingLockScan.Delivery.ACCEPTED -> Unit
                        PendingLockScan.Delivery.ABORTED -> cycle.invalidateOffer()
                        PendingLockScan.Delivery.FALLBACK -> {
                            if(validWindow(window)) {
                                showOffer(token)
                                val settings = AutoClockPreferences(this)
                                settings.lastActivation="NOTIFICATION"
                                val launched = BackgroundClockLauncher(this).launch(token,window)
                                if(!launched) {
                                    val host = candidate.get()
                                    if(host != null && ClockActivationHost.current() === host && validWindow(window)) host.enterLockedClock()
                                }
                                // startActivity may be silently blocked by an OEM. Only Activity.onResume marks SHOWN.
                                if(launched) handler.postDelayed({
                                    if(settings.lastActivation == "REQUESTED") settings.lastActivation="BLOCKED"
                                },2500L)
                            } else cycle.invalidateOffer()
                        }
                    }
                }
            },
            onFinished = {
                if(!scanProducedOffer && scanWindow != null) AutoClockPreferences(this).lastActivation="NO_MATCH"
                scanWindow?.let(ClockDreamBridge.scans::cancel); scanWindow = null
            })
    }
    private fun validWindow(window: ClockLockWindow) = AutoClockPreferences(this).enabled && !monitor.activelyUnlocked && cycle.isCurrentWindow(window)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action == STOP) {
            AutoClockPreferences(this).enabled = false; stopSelf(); return START_NOT_STICKY
        }
        val settings = AutoClockPreferences(this)
        if(!settings.enabled || shouldPauseAutoClockForLowBattery(this)) {
            running = false
            settings.lastActivation = "MONITOR_STOPPED"
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            if(Build.VERSION.SDK_INT >= 34) startForeground(MONITOR_ID, monitoringNotification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(MONITOR_ID, monitoringNotification())
            running = true
            monitor.start()
            if(monitor.activelyUnlocked) { cycle.onUnlock(); scan.onUnlock() }
        } catch (_: RuntimeException) {
            running = false; AutoClockPreferences(this).lastActivation="MONITOR_STOPPED"; stopSelf(); return START_NOT_STICKY
        }
        return START_STICKY
    }
    private fun monitoringNotification(): Notification {
        val open = PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this,1,Intent(this,AutoClockService::class.java).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this,MONITOR_CHANNEL).setSmallIcon(R.drawable.ic_clock_notification)
            .setContentTitle(getString(R.string.auto_monitor_title)).setContentText(getString(R.string.auto_monitor_body))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(0,getString(R.string.auto_disable),stop).build()
    }
    private fun showOffer(token: String) {
        val open = PendingIntent.getActivity(this,2,Intent(this,ClockSessionActivity::class.java).putExtra("offer",token),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this,OFFER_CHANNEL).setSmallIcon(R.drawable.ic_clock_notification)
            .setContentTitle(getString(R.string.auto_offer_title)).setContentText(getString(R.string.auto_offer_body))
            .setContentIntent(open).setAutoCancel(true).setTimeoutAfter(30_000L).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).build()
        try { notifications.notify(OFFER_ID,notification) } catch (_: SecurityException) { /* Status is shown in settings; no intrusive alternative. */ }
        handler.postDelayed({ cycle.invalidateOffer(); notifications.cancel(OFFER_ID) },30_000L)
    }
    private fun cancelOffer() { handler.removeCallbacksAndMessages(null); cycle.invalidateOffer(); notifications.cancel(OFFER_ID) }
    override fun onDestroy() {
        running = false
        scan.cancel(); monitor.close(); cancelOffer(); candidate.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        const val MONITOR_CHANNEL = "clock_monitor"
        const val OFFER_CHANNEL = "clock_offer"
        const val MONITOR_ID = 7101
        const val OFFER_ID = 7102
        const val STOP = "com.linzin.linzindeskclock.STOP_MONITOR"
        @Volatile var running = false; private set
    }
}
