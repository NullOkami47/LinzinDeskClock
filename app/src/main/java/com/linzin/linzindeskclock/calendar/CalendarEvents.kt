package com.linzin.linzindeskclock.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.linzin.linzindeskclock.R
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Occurrence times come from Instances, including recurring events. */
data class UpcomingCalendarEvent(
    val title: String,
    val timeLabel: String,
    val eventId: Long? = null,
    val beginMillis: Long? = null,
    val endMillis: Long? = null,
    val isAllDay: Boolean = false,
    val location: String? = null,
    val description: String? = null,
    val calendarName: String? = null
)

fun hasCalendarReadPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED

/** Read at most three live occurrences in the existing 48-hour window. */
fun fetchUpcomingCalendarEvents(context: Context): List<UpcomingCalendarEvent> =
    queryUpcomingCalendarEvents(context, System.currentTimeMillis()) { uri, projection, order ->
        context.contentResolver.query(uri, projection, null, null, order)
    }

/** The query boundary permits fixture cursors without touching a user's calendar. */
internal fun queryUpcomingCalendarEvents(
    context: Context,
    nowMillis: Long,
    query: (Uri, Array<String>, String) -> Cursor?
): List<UpcomingCalendarEvent> {
    if (!hasCalendarReadPermission(context)) return emptyList()

    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
        ContentUris.appendId(this, nowMillis)
        ContentUris.appendId(this, nowMillis + 48 * 60 * 60 * 1000L)
    }.build()
    // Instances exposes these inherited EventsColumns and CalendarColumns.
    val projection = arrayOf(
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.EVENT_LOCATION,
        CalendarContract.Instances.DESCRIPTION,
        CalendarContract.Instances.CALENDAR_DISPLAY_NAME
    )
    return try {
        val events = mutableListOf<UpcomingCalendarEvent>()
        query(uri, projection, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
            val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val allDayIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            val idIndex = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
            val today = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            val timeFormat = SimpleDateFormat("H:mm", Locale.US)
            val dayFormat = SimpleDateFormat("M/d", Locale.US)

            while (events.size < 3 && cursor.moveToNext()) {
                // Permission may be revoked while a remote provider is returning rows.
                if (!hasCalendarReadPermission(context)) return emptyList()
                if (cursor.isNull(beginIndex) || cursor.isNull(endIndex)) continue
                val begin = cursor.getLong(beginIndex)
                val end = cursor.getLong(endIndex)
                val allDay = cursor.getInt(allDayIndex) == 1
                val eventDate = Instant.ofEpochMilli(begin)
                    .atZone(if (allDay) ZoneOffset.UTC else ZoneId.systemDefault()).toLocalDate()
                val label = if (allDay) {
                    if (inclusiveAllDayEnd(begin, end).isBefore(today)) continue
                    if (!eventDate.isAfter(today)) {
                        context.getString(R.string.event_all_day_today)
                    } else {
                        dayFormat.timeZone = TimeZone.getTimeZone("UTC")
                        "${dayFormat.format(Date(begin))} ${context.getString(R.string.event_all_day)}"
                    }
                } else {
                    if (end <= nowMillis) continue
                    dayFormat.timeZone = TimeZone.getDefault()
                    if (eventDate == today) timeFormat.format(Date(begin))
                    else "${dayFormat.format(Date(begin))} ${timeFormat.format(Date(begin))}"
                }
                events += UpcomingCalendarEvent(
                    title = cursor.optionalText(CalendarContract.Instances.TITLE)
                        ?: context.getString(R.string.event_untitled),
                    timeLabel = label,
                    eventId = if (idIndex >= 0 && !cursor.isNull(idIndex)) cursor.getLong(idIndex) else null,
                    beginMillis = begin,
                    endMillis = end,
                    isAllDay = allDay,
                    location = cursor.optionalText(CalendarContract.Instances.EVENT_LOCATION),
                    description = cursor.optionalText(CalendarContract.Instances.DESCRIPTION),
                    calendarName = cursor.optionalText(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)
                )
            }
        }
        if (hasCalendarReadPermission(context)) events else emptyList()
    } catch (_: SecurityException) {
        emptyList()
    } catch (_: RuntimeException) {
        // Unavailable/OEM providers must not crash the clock or log private cursor data.
        emptyList()
    }
}

private fun Cursor.optionalText(column: String): String? {
    val index = getColumnIndex(column)
    return if (index >= 0 && !isNull(index)) getString(index)?.takeIf { it.isNotBlank() } else null
}
