package com.linzin.linzindeskclock.localization

import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class ClockFormatsTest {
    private val time = Instant.parse("2026-11-09T12:00:00Z").toEpochMilli()
    private val utc = TimeZone.getTimeZone("UTC")
    @Test fun chineseAndJapaneseDatesUseMonthDayAndWeekday() {
        assertEquals("星期一, 11月9日", formatClockDate(time, Locale.TAIWAN, utc))
        assertEquals("月曜日, 11月9日", formatClockDate(time, Locale.JAPAN, utc))
    }
    @Test fun westernDateRetainsTheFullWeekdayAndMonth() {
        assertEquals("Monday, November 9", formatClockDate(time, Locale.UK, utc))
    }
    @Test fun forecastHoursFollowTheClockFormatIncludingNoonAndMidnight() {
        assertEquals("00:00", formatForecastHour(0, true, Locale.UK))
        assertEquals("13:00", formatForecastHour(13, true, Locale.UK))
        assertEquals("12AM", formatForecastHour(0, false, Locale.UK))
        assertEquals("12PM", formatForecastHour(12, false, Locale.UK))
        assertEquals("1PM", formatForecastHour(13, false, Locale.UK))
    }
}
