package com.linzin.linzindeskclock.calendar

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The summary never changes the original occurrence timestamps used by details and intents. */
fun formatCalendarEventSummary(
    begin: Long,
    end: Long,
    allDay: Boolean,
    now: Long,
    locale: Locale,
    zone: ZoneId,
    todayLabel: String,
    allDayLabel: String
): String? {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val start = Instant.ofEpochMilli(begin).atZone(if (allDay) ZoneOffset.UTC else zone)
    val dayFormat = DateTimeFormatter.ofPattern("M/d", locale)
    if (allDay) {
        if (inclusiveAllDayEnd(begin, end).isBefore(today)) return null
        return if (!start.toLocalDate().isAfter(today)) todayLabel
        else "${start.format(dayFormat)} $allDayLabel"
    }
    if (end <= now) return null
    if (start.toLocalDate().isBefore(today)) return todayLabel
    val time = start.format(DateTimeFormatter.ofPattern("H:mm", locale))
    return if (start.toLocalDate() == today) time else "${start.format(dayFormat)} $time"
}
