package com.linzin.linzindeskclock.autostart

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.linzin.linzindeskclock.settings.AutoClockPreferences
import com.linzin.linzindeskclock.settings.BackgroundLaunchAccess

/** One standard Activity start per eligible lock; no overlays, full-screen intent or retry loop. */
class BackgroundClockLauncher(private val context: Context) {
    fun launch(token: String, window: ClockLockWindow): Boolean {
        val settings = AutoClockPreferences(context)
        val cycle = ClockCycleStore(context)
        if(token.isBlank() || !BackgroundClockLaunchPolicy.canLaunch(
            settings.enabled,settings.automaticLaunch,BackgroundLaunchAccess.granted(context),
            cycle.isCurrentWindow(window),context.getSystemService(PowerManager::class.java).isInteractive,
            context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        )) return false
        return try {
            context.startActivity(Intent(context,ClockSessionActivity::class.java)
                .putExtra("offer",token).putExtra("automatic",true)
                .putExtra("lock_boot",window.boot).putExtra("lock_at",window.lockedAt)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION))
            settings.lastActivation="REQUESTED"
            true
        } catch (_: RuntimeException) {
            settings.lastActivation="BLOCKED"
            false
        }
    }
}
