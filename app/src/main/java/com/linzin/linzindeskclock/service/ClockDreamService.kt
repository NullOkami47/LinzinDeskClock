package com.linzin.linzindeskclock.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorManager
import android.service.dreams.DreamService
import android.view.KeyEvent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.linzin.linzindeskclock.DeskClockColorScheme
import com.linzin.linzindeskclock.ui.DeskClockScreen
import com.linzin.linzindeskclock.MainActivity
import com.linzin.linzindeskclock.autostart.*

/** Explicit system screen-saver selection is a separate entry mode, not an AOD replacement. */
class ClockDreamService : DreamService(), PendingLockScan.Waiter {
    private val deadlineHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var owner: DreamOwner? = null
    private var compose: ComposeView? = null
    private var scan: AutoClockTriggerController? = null
    private var accepted = false
    private var lockWindow: ClockLockWindow? = null
    private var ending = false
    private fun eligibleLockContext(): Boolean = !ending &&
        !(getSystemService(android.os.PowerManager::class.java).isInteractive && !getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)
    private var registered = false
    private lateinit var exit: ClockExitController
    private val unlock = object : BroadcastReceiver() {
        override fun onReceive(context: Context,intent: Intent) {
            ending = true; deadlineHandler.removeCallbacksAndMessages(null); scan?.cancel()
            ClockCycleStore(this@ClockDreamService).onUnlock(); finish()
        }
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true; isFullscreen = true; isScreenBright = false
        window.attributes = window.attributes.apply { screenOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
        window.setBackgroundDrawableResource(android.R.color.black)
        MainActivity.setImmersiveMode(window,true)
        exit = ClockExitController(this) { scan?.manualExit(); finish() }
        owner = DreamOwner()
        compose = ComposeView(this).also { view ->
            view.setViewTreeLifecycleOwner(owner)
            view.setViewTreeViewModelStoreOwner(owner)
            view.setViewTreeSavedStateRegistryOwner(owner)
            setContentView(view)
        }
        ContextCompat.registerReceiver(this,unlock,IntentFilter(Intent.ACTION_USER_PRESENT),ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
    }
    override fun onDreamingStarted() {
        super.onDreamingStarted()
        ending = false
        accepted = false
        exit = ClockExitController(this) { scan?.manualExit(); finish() }
        val cycle = ClockCycleStore(this)
        val window = cycle.activeWindow()
        val remaining = cycle.remainingWindowMs()
        val foldable = getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null ||
            packageManager.hasSystemFeature("android.hardware.sensor.hinge_angle")
        if(foldable || !eligibleLockContext() || window == null || remaining <= 0) { finish(); return }
        owner?.registry?.currentState = Lifecycle.State.RESUMED
        lockWindow = window
        deadlineHandler.postDelayed({ if(!accepted) { ending = true; scan?.cancel(); finish() } },remaining)
        // The foreground monitor may already own this lock's scan. Wait for its one verdict.
        if(!cycle.claimAttempt()) {
            if(!ClockDreamBridge.scans.subscribe(window,this)) scanUnavailable()
            return
        }
        scan = AutoClockTriggerController(this,false,
            mayScan = { eligibleLockContext() && cycle.isCurrentWindow(window) },
            onEligible = { if(eligibleLockContext() && !cycle.suppressed && cycle.remainingWindowMs() > 0) renderClock() },
            onFinished = { if(!accepted) finish() }
        ).also { it.onUnlock(); if(!it.onLock(deadlineElapsedMs=window.deadline)) finish() }
    }
    override fun showEligibleClock(token: String): Boolean {
        val cycle = ClockCycleStore(this)
        val window = lockWindow ?: return false
        if(accepted || !eligibleLockContext() || !cycle.isCurrentWindow(window) || !cycle.consume(token) || !cycle.isCurrentWindow(window)) return false
        renderClock()
        return accepted
    }
    override fun scanUnavailable() {
        if(accepted) return
        ending = true
        deadlineHandler.removeCallbacksAndMessages(null)
        finish()
    }
    private fun renderClock() {
        val view = compose ?: return
        accepted = true
        deadlineHandler.removeCallbacksAndMessages(null)
        isScreenBright = true
        view.setContent { MaterialTheme(colorScheme=DeskClockColorScheme) {
            DeskClockScreen(hasHingeSensor=false,entryMode=ClockEntryMode.DREAM,windowOverride=window,onExit={ exit.exit() })
        } }
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if(event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) { exit.exit(); return true }
        return super.dispatchKeyEvent(event)
    }
    override fun onWakeUp() {
        ending=true; deadlineHandler.removeCallbacksAndMessages(null); scan?.cancel()
        val unlocked = getSystemService(android.os.PowerManager::class.java).isInteractive &&
            !getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
        if(unlocked) { ClockCycleStore(this).onUnlock(); finish() }
        else if(::exit.isInitialized) exit.exit() else finish()
    }
    override fun onDreamingStopped() { ending=true; ClockDreamBridge.scans.detach(this); deadlineHandler.removeCallbacksAndMessages(null); scan?.cancel(); scan=null; compose?.disposeComposition(); owner?.registry?.currentState=Lifecycle.State.CREATED; super.onDreamingStopped() }
    override fun onDetachedFromWindow() {
        ending=true; ClockDreamBridge.scans.detach(this); deadlineHandler.removeCallbacksAndMessages(null)
        scan?.cancel(); scan=null
        compose?.disposeComposition(); compose=null
        owner?.close(); owner=null
        if(registered) unregisterReceiver(unlock); registered=false
        super.onDetachedFromWindow()
    }
    private class DreamOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val viewModelStore = ViewModelStore()
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
        init { saved.performAttach(); saved.performRestore(null); registry.currentState=Lifecycle.State.CREATED }
        fun close() { registry.currentState=Lifecycle.State.DESTROYED; viewModelStore.clear() }
    }
}
