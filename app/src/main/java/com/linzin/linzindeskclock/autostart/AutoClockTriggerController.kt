package com.linzin.linzindeskclock.autostart

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import com.linzin.linzindeskclock.sensors.ActivationEligibility
import com.linzin.linzindeskclock.sensors.ChargingMonitor
import com.linzin.linzindeskclock.sensors.TiltDetector
import com.linzin.linzindeskclock.settings.AutoClockPreferences

/** All calls and sensor callbacks are on the main looper; the deadline also bounds stale streams. */
class AutoClockTriggerController(
    private val context: Context,
    private val isFoldable: Boolean,
    private val mayScan: () -> Boolean,
    private val onEligible: () -> Unit,
    private val onFinished: () -> Unit = {}
) {
    private val handler = Handler(Looper.getMainLooper())
    private val gate = ClockActivationStateMachine()
    private val settings = AutoClockPreferences(context)
    private val wake = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"LinzinDeskClock:lock-scan").apply { setReferenceCounted(false) }
    private var running = false
    private val detector = TiltDetector(context) { tilt ->
        if (running) {
            try {
                if (!mayScan()) cancel() else {
                    val eligible = ActivationEligibility.accepts(isFoldable,tilt,ChargingMonitor.plugged(context),settings.chargingMode)
                    if (gate.sample(SystemClock.elapsedRealtime(),eligible)) {
                        stopResources()
                        try { onEligible() } finally { onFinished() }
                    }
                }
            } catch (error: RuntimeException) {
                // A denied sticky broadcast or vendor sensor failure must still release this scan.
                android.util.Log.w("AutoDeskClock","Lock scan cancelled",error)
                cancel()
            }
        }
    }
    private val timeout = Runnable { gate.expire(SystemClock.elapsedRealtime()); cancel() }
    fun onUnlock() { cancel(); gate.onUnlock() }
    fun onLock(remainingMs: Long = ClockActivationStateMachine.WINDOW_MS, deadlineElapsedMs: Long? = null): Boolean {
        val now = SystemClock.elapsedRealtime()
        val duration = (deadlineElapsedMs?.minus(now) ?: remainingMs).coerceAtMost(ClockActivationStateMachine.WINDOW_MS)
        if (isFoldable || !mayScan() || !gate.onLock(now,duration)) return false
        running = true
        return try {
            wake.acquire(duration)
            handler.postDelayed(timeout,duration)
            if (!detector.start()) { cancel(); false } else true
        } catch (_: RuntimeException) { cancel(); false }
    }
    fun manualExit() { stopResources(); gate.manualExit() }
    fun cancel() { val wasRunning = running; stopResources(); gate.cancel(); if(wasRunning) onFinished() }
    private fun stopResources() {
        running = false; handler.removeCallbacks(timeout)
        try { detector.stop() } finally { if (wake.isHeld) wake.release() }
    }
}
