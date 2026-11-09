package com.linzin.linzindeskclock.calendar

import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import com.linzin.linzindeskclock.R

/** Read the real device state at each user action; never request a keyguard dismissal. */
fun isCalendarDeviceUnlocked(context: Context): Boolean {
    val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return false
    return !keyguard.isKeyguardLocked && !keyguard.isDeviceLocked
}

fun canShowCalendarEventDetails(context: Context): Boolean =
    isCalendarDeviceUnlocked(context) && hasCalendarReadPermission(context)

fun buildCalendarEventViewIntent(event: UpcomingCalendarEvent): Intent? {
    val eventId = event.eventId?.takeIf { it >= 0 } ?: return null
    val begin = event.beginMillis ?: return null
    val end = event.endMillis ?: return null
    return Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

enum class CalendarEventOpenResult { OPENED, LOCKED, PERMISSION_REQUIRED, UNAVAILABLE }

/** Called only by the details dialog's explicit button, never by loading or selecting a row. */
fun openCalendarEvent(context: Context, event: UpcomingCalendarEvent): CalendarEventOpenResult {
    val result = when {
        !isCalendarDeviceUnlocked(context) -> CalendarEventOpenResult.LOCKED
        !hasCalendarReadPermission(context) -> CalendarEventOpenResult.PERMISSION_REQUIRED
        else -> {
            val intent = buildCalendarEventViewIntent(event)
            if (intent == null) CalendarEventOpenResult.UNAVAILABLE
            else try {
                // Do not rely on resolveActivity: package visibility can hide installed handlers.
                context.startActivity(intent)
                CalendarEventOpenResult.OPENED
            } catch (_: ActivityNotFoundException) {
                CalendarEventOpenResult.UNAVAILABLE
            } catch (_: SecurityException) {
                CalendarEventOpenResult.UNAVAILABLE
            }
        }
    }
    val message = when (result) {
        CalendarEventOpenResult.OPENED -> null
        CalendarEventOpenResult.LOCKED -> R.string.calendar_unlock_to_view
        CalendarEventOpenResult.PERMISSION_REQUIRED -> R.string.calendar_permission_required
        CalendarEventOpenResult.UNAVAILABLE -> R.string.calendar_app_unavailable
    }
    if (message != null) Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    return result
}
