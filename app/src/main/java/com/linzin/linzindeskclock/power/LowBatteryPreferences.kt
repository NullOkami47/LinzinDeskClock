package com.linzin.linzindeskclock.power

import android.content.Context

class LowBatteryPreferences(context:Context) {
    val storage=context.getSharedPreferences("desk_clock_prefs",Context.MODE_PRIVATE)
    var threshold:Int
        get()=storage.getInt("low_battery_threshold",20).coerceIn(5,50)
        set(value) { storage.edit().putInt("low_battery_threshold",value.coerceIn(5,50)).apply() }
    var action:LowBatteryAction
        get()=runCatching { LowBatteryAction.valueOf(storage.getString("low_battery_action","DIM") ?: "DIM") }.getOrDefault(LowBatteryAction.DIM)
        set(value) { storage.edit().putString("low_battery_action",value.name).apply() }
}
