package com.linzin.linzindeskclock.settings

import android.content.Context
import android.content.SharedPreferences
import com.linzin.linzindeskclock.sensors.ChargingMode

class AutoClockPreferences(context: Context) {
    val preferences: SharedPreferences = context.getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = preferences.getBoolean("auto_clock_enabled", false)
        set(value) { preferences.edit().putBoolean("auto_clock_enabled", value).apply() }
    var chargingMode: ChargingMode
        get() = runCatching { ChargingMode.valueOf(preferences.getString("auto_charging_mode", null) ?: "WIRELESS_ONLY") }.getOrDefault(ChargingMode.WIRELESS_ONLY)
        set(value) { preferences.edit().putString("auto_charging_mode", value.name).apply() }
    var hideMediaWhenLocked: Boolean
        get() = preferences.getBoolean("auto_hide_media", true)
        set(value) { preferences.edit().putBoolean("auto_hide_media", value).apply() }
}
