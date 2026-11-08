package com.linzin.linzindeskclock.settings

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** This is an explicit system special-access flow; no overlay windows are created by the app. */
object BackgroundLaunchAccess {
    fun granted(context: Context): Boolean = runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)
    fun openSettings(context: Context): Boolean {
        if(context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return false
        val intents = listOf(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:" + context.packageName)),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        )
        for(intent in intents) try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (_: RuntimeException) { }
        return false
    }
}
