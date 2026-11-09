package com.linzin.linzindeskclock.sensors

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

object ChargingMonitor {
    fun plugged(context: Context): Int = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
}
