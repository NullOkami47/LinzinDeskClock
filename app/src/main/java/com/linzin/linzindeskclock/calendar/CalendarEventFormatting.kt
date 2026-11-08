package com.linzin.linzindeskclock.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class CalendarEventDateTimeLabels(val start: String, val end: String)

/** All-day END is exclusive at UTC midnight, independently of the device's zone. */
internal fun inclusiveAllDayEnd(beginMillis: Long, endMillis: Long): LocalDate {
    val firstDay = Instant.ofEpochMilli(beginMillis).atZone(ZoneOffset.UTC).toLocalDate()
    if (endMillis <= beginMillis) return firstDay
    return Instant.ofEpochMilli(endMillis).atZone(ZoneOffset.UTC).toLocalDate()
        .minusDays(1).coerceAtLeast(firstDay)
}

fun formatCalendarEventDateTimes(
    event: UpcomingCalendarEvent,
    locale: Locale = Locale.getDefault(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    use24Hour: Boolean = true
): CalendarEventDateTimeLabels? {
    val begin = event.beginMillis ?: return null
    val end = event.endMillis ?: return null
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    if (event.isAllDay) {
        val firstDay = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
        return CalendarEventDateTimeLabels(
            dateFormat.format(firstDay),
            dateFormat.format(inclusiveAllDayEnd(begin, end))
        )
    }
    val timeFormat = DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm z" else "h:mm a z", locale)
    fun label(millis: Long): String {
        val dateTime = Instant.ofEpochMilli(millis).atZone(zoneId)
        return "${dateFormat.format(dateTime)} · ${timeFormat.format(dateTime)}"
    }
    return CalendarEventDateTimeLabels(label(begin), label(end))
}
