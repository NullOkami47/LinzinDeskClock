package com.linzin.linzindeskclock.settings

import com.linzin.linzindeskclock.compat.clockSystemService

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** This is an explicit system special-access flow; no overlay windows are created by the app. */
object BackgroundLaunchAccess {
    fun granted(context: Context): Boolean = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.SYSTEM_ALERT_WINDOW) == PackageManager.PERMISSION_GRANTED
    } else runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)
    fun openSettings(context: Context): Boolean {
        if(context.clockSystemService(KeyguardManager::class.java).isKeyguardLocked) return false
        val intents = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) listOf(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:" + context.packageName)),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        ) else listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
        for(intent in intents) try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (_: RuntimeException) { }
        return false
    }
}
