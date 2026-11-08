package com.linzin.linzindeskclock.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.linzin.linzindeskclock.settings.AutoClockPreferences

/** Resume only an explicitly enabled event monitor. Boot never launches or scans a clock. */
class ClockMonitorRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if(intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val settings=AutoClockPreferences(context)
        if(!settings.enabled) return
        try {
            ContextCompat.startForegroundService(context,Intent(context,AutoClockService::class.java))
        } catch (_: RuntimeException) { settings.lastActivation="MONITOR_STOPPED" }
    }
}
