package com.linzin.linzindeskclock.autostart

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat

class LockStateMonitor(private val context: Context, private val onLock: () -> Unit, private val onUnlock: () -> Unit, private val onScreenOn: () -> Unit) {
    private val power = context.getSystemService(PowerManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    val nonInteractive: Boolean get() = !power.isInteractive
    val locked: Boolean get() = keyguard.isKeyguardLocked
    val activelyUnlocked: Boolean get() = power.isInteractive && !keyguard.isKeyguardLocked
    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> if (nonInteractive) onLock()
                Intent.ACTION_USER_PRESENT -> if (activelyUnlocked) onUnlock()
                Intent.ACTION_SCREEN_ON -> onScreenOn()
            }
        }
    }
    fun start() {
        if (registered) return
        ContextCompat.registerReceiver(context,receiver,IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_SCREEN_ON)
        },ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
    }
    fun close() { if (registered) context.unregisterReceiver(receiver); registered = false }
}
