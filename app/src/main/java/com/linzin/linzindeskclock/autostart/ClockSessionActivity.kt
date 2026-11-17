package com.linzin.linzindeskclock.autostart

import com.linzin.linzindeskclock.compat.clockSystemService
import com.linzin.linzindeskclock.compat.setClockShowWhenLocked
import com.linzin.linzindeskclock.compat.setClockTurnScreenOn

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.linzin.linzindeskclock.display.preferredClockOrientation
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.PowerManager
import com.linzin.linzindeskclock.settings.AutoClockPreferences
import com.linzin.linzindeskclock.settings.BackgroundLaunchAccess
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
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
class ClockSessionActivity : AppCompatActivity() {
    private var registered = false
    private var screenOffRequested = false
    private val screenOffController by lazy {
        com.linzin.linzindeskclock.power.ScreenOffController(this, window,
            prepareHost = {
                screenOffRequested = true
                setClockTurnScreenOn(false)
                setClockShowWhenLocked(false)
            }, finishHost = { finish() })
    }
    private lateinit var exit: ClockExitController
    private lateinit var sessionState: ClockSessionState
    private val unlock = object : BroadcastReceiver() {
        override fun onReceive(context: Context,intent: Intent) {
            if(intent.action==Intent.ACTION_USER_PRESENT && !clockSystemService(KeyguardManager::class.java).isKeyguardLocked) {
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
        val locked = clockSystemService(KeyguardManager::class.java).isKeyguardLocked
        val interactive = clockSystemService(PowerManager::class.java).isInteractive
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
        enableEdgeToEdge(); requestedOrientation = preferredClockOrientation(this, false)
        window.setBackgroundDrawableResource(android.R.color.black)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setClockShowWhenLocked(true); setClockTurnScreenOn(!retained.presented)
        ContextCompat.registerReceiver(this,unlock,IntentFilter(Intent.ACTION_USER_PRESENT),ContextCompat.RECEIVER_EXPORTED)
        registered = true
        onBackPressedDispatcher.addCallback(this,object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { exit.exit() } })
        setContent { MaterialTheme(colorScheme = DeskClockColorScheme) {
            DeskClockScreen(hasHingeSensor = false, entryMode = ClockEntryMode.LOCK_SCREEN, onExit = { exit.exit() },
                onScreenOff = { screenOffController.request() })
        } }
    }
    override fun onResume() {
        super.onResume()
        if(screenOffRequested || isFinishing || !::exit.isInitialized) return
        if(!sessionState.presented) setClockTurnScreenOn(true)
        if(clockSystemService(PowerManager::class.java).isInteractive && !clockSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            ClockCycleStore(this).observeUnlocked(); finish()
        }
    }
    override fun onWindowFocusChanged(hasFocus:Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if(hasFocus && ::exit.isInitialized && !isFinishing && clockSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            sessionState.presented=true
            setClockTurnScreenOn(false)
            AutoClockPreferences(this).lastActivation="SHOWN"
            clockSystemService(NotificationManager::class.java).cancel(AutoClockService.OFFER_ID)
        }
    }
    override fun onUserLeaveHint() {
        if(::exit.isInitialized && !isFinishing) exit.exit()
        super.onUserLeaveHint()
    }
    override fun onPause() {
        super.onPause()
        if(::sessionState.isInitialized && sessionState.presented) setClockTurnScreenOn(false)
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
        setClockShowWhenLocked(false); setClockTurnScreenOn(false)
        super.onDestroy()
    }
}
