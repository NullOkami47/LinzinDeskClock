package com.linzin.linzindeskclock.autostart

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Display
import androidx.core.content.ContextCompat

class LockStateMonitor(private val context: Context,private val onLock:()->Unit,private val onUnlock:()->Unit,private val onScreenOn:()->Unit) {
    private val power=context.getSystemService(PowerManager::class.java)
    private val keyguard=context.getSystemService(KeyguardManager::class.java)
    private val displays=context.getSystemService(DisplayManager::class.java)
    val nonInteractive:Boolean get()=!power.isInteractive
    val locked:Boolean get()=keyguard.isKeyguardLocked
    val activelyUnlocked:Boolean get()=power.isInteractive && !keyguard.isKeyguardLocked
    private var registered=false
    private var lastDisplayState=Display.STATE_UNKNOWN
    private val displayListener=object:DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId:Int) { }
        override fun onDisplayRemoved(displayId:Int) { }
        override fun onDisplayChanged(displayId:Int) {
            if(displayId!=Display.DEFAULT_DISPLAY) return
            val state=displays.getDisplay(displayId)?.state ?: Display.STATE_UNKNOWN
            val previous=lastDisplayState
            val wasLow=previous in lowPowerStates
            lastDisplayState=state
            if(previous!=Display.STATE_UNKNOWN && !wasLow && state in lowPowerStates && nonInteractive) onLock()
            else if(state==Display.STATE_ON && !nonInteractive) onScreenOn()
        }
    }
    private val receiver=object:BroadcastReceiver() {
        override fun onReceive(context:Context,intent:Intent) {
            when(intent.action) {
                Intent.ACTION_SCREEN_OFF -> if(nonInteractive) onLock()
                Intent.ACTION_USER_PRESENT -> if(activelyUnlocked) onUnlock()
                Intent.ACTION_SCREEN_ON -> onScreenOn()
            }
        }
    }
    fun start() {
        if(registered) return
        // These three actions are protected system broadcasts. USER_PRESENT can originate in
        // System UI's UID, so NOT_EXPORTED would discard legitimate system-component delivery.
        ContextCompat.registerReceiver(context,receiver,IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF);addAction(Intent.ACTION_USER_PRESENT);addAction(Intent.ACTION_SCREEN_ON)
        },ContextCompat.RECEIVER_EXPORTED)
        registered=true
        lastDisplayState=displays.getDisplay(Display.DEFAULT_DISPLAY)?.state ?: Display.STATE_UNKNOWN
        displays.registerDisplayListener(displayListener,Handler(Looper.getMainLooper()))
    }
    fun close() {
        displays.unregisterDisplayListener(displayListener)
        if(registered) context.unregisterReceiver(receiver)
        registered=false
    }
    companion object { private val lowPowerStates=setOf(Display.STATE_OFF,Display.STATE_DOZE,Display.STATE_DOZE_SUSPEND) }
}
