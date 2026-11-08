package com.linzin.linzindeskclock

import android.app.Application
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.linzin.linzindeskclock.autostart.ClockCycleStore

/** Passive unlock observation while this process exists; no sensor, timer or background launch. */
class ClockApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(this, object : BroadcastReceiver() {
            override fun onReceive(context: Context,intent: Intent) {
                if(intent.action == Intent.ACTION_SCREEN_OFF) {
                    if(!getSystemService(PowerManager::class.java).isInteractive) ClockCycleStore(this@ClockApplication).noteLock()
                    return
                }
                if(intent.action == Intent.ACTION_USER_PRESENT && getSystemService(PowerManager::class.java).isInteractive && !getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
                    ClockCycleStore(this@ClockApplication).onUnlock()
                    com.linzin.linzindeskclock.autostart.ClockActivationHost.onDeviceUnlocked()
                }
            }
        },IntentFilter(Intent.ACTION_USER_PRESENT).apply { addAction(Intent.ACTION_SCREEN_OFF) },ContextCompat.RECEIVER_EXPORTED)
    }
}
