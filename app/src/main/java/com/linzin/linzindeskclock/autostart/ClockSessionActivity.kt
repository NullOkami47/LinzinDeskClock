package com.linzin.linzindeskclock.autostart

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.core.content.ContextCompat
import com.linzin.linzindeskclock.DeskClockColorScheme
import com.linzin.linzindeskclock.ui.DeskClockScreen
import com.linzin.linzindeskclock.MainActivity
import com.linzin.linzindeskclock.service.AutoClockService

/** Unexported user-notification destination. A short-lived single-use offer is required. */
class ClockSessionActivity : ComponentActivity() {
    private var registered = false
    private lateinit var exit: ClockExitController
    private val unlock = object : BroadcastReceiver() {
        override fun onReceive(context: Context,intent: Intent) {
            if(!getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
                ClockCycleStore(this@ClockSessionActivity).onUnlock(); finish()
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(!getSystemService(KeyguardManager::class.java).isKeyguardLocked || ClockCycleStore(this).suppressed ||
            !ClockCycleStore(this).consume(intent.getStringExtra("offer"))) {
            finish(); return
        }
        exit = ClockExitController(this) { finish() }
        enableEdgeToEdge(); requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.setBackgroundDrawableResource(android.R.color.black)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setShowWhenLocked(true); setTurnScreenOn(true)
        getSystemService(NotificationManager::class.java).cancel(AutoClockService.OFFER_ID)
        ContextCompat.registerReceiver(this,unlock,IntentFilter(Intent.ACTION_USER_PRESENT),ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
        onBackPressedDispatcher.addCallback(this,object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { exit.exit() } })
        setContent { MaterialTheme(colorScheme = DeskClockColorScheme) {
            DeskClockScreen(hasHingeSensor = false, entryMode = ClockEntryMode.LOCK_SCREEN, onExit = { exit.exit() })
        } }
    }
    override fun onResume() { super.onResume(); if(!getSystemService(KeyguardManager::class.java).isKeyguardLocked) finish() }
    override fun onPause() { super.onPause(); setTurnScreenOn(false) }
    override fun onStop() {
        super.onStop()
        if(!isChangingConfigurations && !isFinishing && ::exit.isInitialized) exit.exit()
    }
    override fun onDestroy() {
        if(registered) unregisterReceiver(unlock)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SECURE)
        window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        setShowWhenLocked(false); setTurnScreenOn(false)
        super.onDestroy()
    }
}
