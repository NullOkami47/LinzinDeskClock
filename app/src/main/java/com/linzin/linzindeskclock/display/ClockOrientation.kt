package com.linzin.linzindeskclock.display

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build

fun preferredClockOrientation(context: Context, hasHingeSensor: Boolean): Int {
    val prefs = context.getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE)
    val manufacturer = Build.MANUFACTURER.lowercase()
    val brand = Build.BRAND.lowercase()
    val isMoto = manufacturer.contains("motorola") || manufacturer.contains("lenovo") ||
        brand.contains("motorola") || brand.contains("moto")
    val metrics = context.resources.displayMetrics
    val shortest = minOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)
    val tall = maxOf(metrics.widthPixels, metrics.heightPixels).toFloat() / shortest >= 2.05f
    val saved = prefs.getString("pref_flip_orientation", null)?.let {
        runCatching { LandscapeOrientation.valueOf(it) }.getOrNull()
    }
    return when (clockOrientation(hasHingeSensor, isMoto, tall, prefs.getBoolean("pref_flip_screen_180", false), saved)) {
        LandscapeOrientation.NORMAL -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        LandscapeOrientation.REVERSE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        LandscapeOrientation.SENSOR -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
}

fun saveClockFlip(context: Context, enabled: Boolean, displayRotation: Int) {
    context.getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit()
        .putBoolean("pref_flip_screen_180", enabled)
        .putString("pref_flip_orientation", if (enabled) oppositeLandscapeOrientation(displayRotation).name else null)
        .apply()
}
