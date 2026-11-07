package com.linzin.linzindeskclock.media

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

object MediaNotificationAccess {
    fun component(context: Context): ComponentName = ComponentName(context, ClockNotificationListenerService::class.java)

    /** Check this exact service, not merely another listener in the same package. */
    fun isGranted(context: Context): Boolean = try {
        context.getSystemService(NotificationManager::class.java)
            ?.isNotificationListenerAccessGranted(component(context)) == true
    } catch (_: RuntimeException) { false }

    /** For the parent's unlocked settings page only. The widget never calls this. */
    fun openSettings(context: Context): Boolean {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        if (keyguard == null || keyguard.isDeviceLocked || keyguard.isKeyguardLocked) return false
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(context).flattenToString())
        val fallback = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        for (intent in listOf(detail, fallback)) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
                // Some OEM settings applications do not expose the per-listener screen.
            } catch (_: SecurityException) {
                return false
            }
        }
        return false
    }
}
