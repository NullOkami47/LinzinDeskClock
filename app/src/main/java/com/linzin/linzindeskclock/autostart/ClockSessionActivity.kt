package com.linzin.linzindeskclock.autostart

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.PowerManager
import com.linzin.linzindeskclock.settings.AutoClockPreferences
import com.linzin.linzindeskclock.settings.BackgroundLaunchAccess
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.linzin.linzindeskclock.DeskClockColorScheme
import com.linzin.linzindeskclock.ui.DeskClockScreen
import com.linzin.linzindeskclock.MainActivity
import com.linzin.linzindeskclock.service.AutoClockService

/** Unexported user-notification destination. A short-lived single-use offer is required. */
class ClockSessionActivity : ComponentActivity() {
    private var registered = false
    private lateinit var exit: ClockExitController
    private lateinit var sessionState: ClockSessionState
    private val unlock = object : BroadcastReceiver() {
        override fun onReceive(context: Context,intent: Intent) {
            if(intent.action==Intent.ACTION_USER_PRESENT && !getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
                ClockCycleStore(this@ClockSessionActivity).onUnlock(); finish()
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cycle = ClockCycleStore(this)
        val retained=ViewModelProvider(this)[ClockSessionState::class.java]
        sessionState=retained
        val automatic = retained.authorisedCycle?.let { retained.automatic } ?: intent.getBooleanExtra("automatic",false)
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val interactive = getSystemService(PowerManager::class.java).isInteractive
        val settings = AutoClockPreferences(this)
        val retainedCycle=retained.authorisedCycle
        val accepted = if(retainedCycle != null) {
            locked && settings.enabled && cycle.currentCycle()==retainedCycle &&
                (!automatic || (settings.automaticLaunch && BackgroundLaunchAccess.granted(this)))
        } else {
            val automaticAllowed = if(automatic) BackgroundClockLaunchPolicy.canLaunch(
                settings.enabled,settings.automaticLaunch,BackgroundLaunchAccess.granted(this),
                cycle.isCurrentWindow(ClockLockWindow(intent.getIntExtra("lock_boot",-1),intent.getLongExtra("lock_at",-1))),
                interactive,locked
            ) else locked
            automaticAllowed && !cycle.suppressed && cycle.consume(intent.getStringExtra("offer"))
        }
        if(!accepted) { finish(); return }
        retained.authorisedCycle=cycle.currentCycle()
        retained.automatic=automatic
        exit = ClockExitController(this) { finish() }
        enableEdgeToEdge(); requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.setBackgroundDrawableResource(android.R.color.black)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setShowWhenLocked(true); setTurnScreenOn(!retained.presented)
        ContextCompat.registerReceiver(this,unlock,IntentFilter(Intent.ACTION_USER_PRESENT),ContextCompat.RECEIVER_EXPORTED)
        registered = true
        onBackPressedDispatcher.addCallback(this,object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { exit.exit() } })
        setContent { MaterialTheme(colorScheme = DeskClockColorScheme) {
            DeskClockScreen(hasHingeSensor = false, entryMode = ClockEntryMode.LOCK_SCREEN, onExit = { exit.exit() })
        } }
    }
    override fun onResume() {
        super.onResume()
        if(!::exit.isInitialized) return
        if(!sessionState.presented) setTurnScreenOn(true)
        if(!getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            ClockCycleStore(this).onUnlock(); finish()
        }
    }
    override fun onWindowFocusChanged(hasFocus:Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if(hasFocus && ::exit.isInitialized && !isFinishing && getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            sessionState.presented=true
            setTurnScreenOn(false)
            AutoClockPreferences(this).lastActivation="SHOWN"
            getSystemService(NotificationManager::class.java).cancel(AutoClockService.OFFER_ID)
        }
    }
    override fun onUserLeaveHint() {
        if(::exit.isInitialized && !isFinishing) exit.exit()
        super.onUserLeaveHint()
    }
    override fun onPause() {
        super.onPause()
        if(::sessionState.isInitialized && sessionState.presented) setTurnScreenOn(false)
    }
    override fun onStop() {
        super.onStop()
        // Keyguard and display transitions can temporarily stop an Activity while it is opening.
        // Only explicit exit/Home/unlock ends the session; a transient stop must not suppress it.
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    override fun onDestroy() {
        if(registered) unregisterReceiver(unlock)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SECURE)
        window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        setShowWhenLocked(false); setTurnScreenOn(false)
        super.onDestroy()
    }
}
