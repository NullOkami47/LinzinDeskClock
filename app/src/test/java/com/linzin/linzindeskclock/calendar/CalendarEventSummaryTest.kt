package com.linzin.linzindeskclock.calendar

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class CalendarEventSummaryTest {
    private fun at(value: String) = Instant.parse(value).toEpochMilli()
    private val now = at("2026-11-09T04:00:00Z")
    private val zone = ZoneId.of("Asia/Hong_Kong")
    private fun summary(begin: String, end: String, allDay: Boolean) = formatCalendarEventSummary(
        at(begin), at(end), allDay, now, Locale.UK, zone, "Today", "All day"
    )
    @Test fun ongoingOvernightEventUsesTodayWithoutChangingOccurrenceTimes() {
        assertEquals("Today", summary("2026-11-08T15:30:00Z", "2026-11-09T05:00:00Z", false))
        assertEquals("13:00", summary("2026-11-09T05:00:00Z", "2026-11-09T06:00:00Z", false))
        assertNull(summary("2026-11-08T15:30:00Z", "2026-11-09T04:00:00Z", false))
    }
    @Test fun multiDayAllDayEventsUseExclusiveUtcEndDates() {
        assertEquals("Today", summary("2026-11-07T00:00:00Z", "2026-11-10T00:00:00Z", true))
        assertNull(summary("2026-11-07T00:00:00Z", "2026-11-09T00:00:00Z", true))
        assertEquals("11/10 All day", summary("2026-11-10T00:00:00Z", "2026-11-11T00:00:00Z", true))
    }
}
