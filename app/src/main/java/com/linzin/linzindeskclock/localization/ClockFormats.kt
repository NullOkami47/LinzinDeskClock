package com.linzin.linzindeskclock.localization

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun formatClockDate(timeMillis: Long, locale: Locale, zone: TimeZone = TimeZone.getDefault()): String {
    val pattern = if (locale.language in listOf("zh", "ja")) "EEEE, M月d日" else "EEEE, MMMM d"
    return SimpleDateFormat(pattern, locale).apply { timeZone = zone }.format(Date(timeMillis))
}

fun formatForecastHour(hour: Int, is24Hour: Boolean, locale: Locale): String =
    if (is24Hour) String.format(locale, "%02d:00", hour)
    else "${if (hour % 12 == 0) 12 else hour % 12}${if (hour < 12) "AM" else "PM"}"
