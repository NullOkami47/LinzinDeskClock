package com.linzin.linzindeskclock.media

import androidx.core.content.ContextCompat

import com.linzin.linzindeskclock.compat.clockSystemService
import com.linzin.linzindeskclock.compat.isClockDeviceLocked

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

object MediaNotificationAccess {
    fun component(context: Context): ComponentName = ComponentName(context, ClockNotificationListenerService::class.java)

    /** Check this exact service, not merely another listener in the same package. */
    fun isGranted(context: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            context.clockSystemService(NotificationManager::class.java)
                .isNotificationListenerAccessGranted(component(context))
        } else {
            val exactComponent = component(context)
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                .orEmpty().split(':').any { ComponentName.unflattenFromString(it) == exactComponent }
        }
    } catch (_: RuntimeException) { false }

    /** Shared by settings and the media permission action; recheck keyguard at click time. */
    fun openSettings(context: Context): Boolean {
        val keyguard = ContextCompat.getSystemService(context, KeyguardManager::class.java)
        if (keyguard == null || keyguard.isClockDeviceLocked() || keyguard.isKeyguardLocked) return false
        val fallback = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        val intents = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            listOf(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(context).flattenToString()), fallback)
        } else listOf(fallback)
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
                // Some OEM settings applications do not expose the per-listener screen.
            } catch (_: SecurityException) {
                // An OEM may reject the detail screen but allow the general listener list.
            }
        }
        return false
    }
}
