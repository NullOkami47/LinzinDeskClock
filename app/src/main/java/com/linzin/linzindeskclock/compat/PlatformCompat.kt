package com.linzin.linzindeskclock.compat

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.core.content.ContextCompat

/** The framework's class-based lookup only exists from API 23; AndroidX maps older services. */
fun <T> Context.clockSystemService(serviceClass: Class<T>): T =
    requireNotNull(ContextCompat.getSystemService(this, serviceClass))

fun KeyguardManager.isClockDeviceLocked(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) isDeviceLocked
    else isKeyguardLocked

/** Keep the same keyguard policy on API 21-26 without requesting any keyguard dismissal. */
fun Activity.setClockShowWhenLocked(enabled: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) setShowWhenLocked(enabled)
    else if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
    else window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
}

fun Activity.setClockTurnScreenOn(enabled: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) setTurnScreenOn(enabled)
    else if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
    else window.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
}
