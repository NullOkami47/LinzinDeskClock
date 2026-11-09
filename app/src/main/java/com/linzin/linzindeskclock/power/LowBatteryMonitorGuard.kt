package com.linzin.linzindeskclock.power

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/** Recheck power before restoring monitoring, even when no clock Composable is mounted. */
fun shouldPauseAutoClockForLowBattery(context: Context): Boolean {
    val preferences = LowBatteryPreferences(context)
    if (preferences.action != LowBatteryAction.EXIT) return false
    val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
    val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    val percentage = if (scale > 0 && level in 0..scale) (level * 100L / scale).toInt() else -1
    val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val powered = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0 ||
        status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    return LowBatteryPolicy.action(percentage, powered, preferences.threshold, preferences.action) == LowBatteryAction.EXIT
}
