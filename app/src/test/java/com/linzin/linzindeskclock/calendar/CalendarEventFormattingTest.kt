package com.linzin.linzindeskclock.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class CalendarEventFormattingTest {
    private fun millis(value: String) = Instant.parse(value).toEpochMilli()
    private fun event(begin: String, end: String, allDay: Boolean = true) = UpcomingCalendarEvent(
        "Fixture", "Summary", eventId = 42,
        beginMillis = millis(begin), endMillis = millis(end), isAllDay = allDay
    )

    @Test fun oneDayEventEndsOnItsStartingDay() {
        val event = event("2026-11-08T00:00:00Z", "2026-11-09T00:00:00Z")
        assertEquals(LocalDate.of(2026, 11, 8), inclusiveAllDayEnd(event.beginMillis!!, event.endMillis!!))
        val labels = formatCalendarEventDateTimes(event, Locale.UK, ZoneId.of("America/Los_Angeles"))!!
        assertEquals(labels.start, labels.end)
        assertTrue(labels.start.contains("8 November 2026"))
    }

    @Test fun allDayUtcDatesDoNotShiftWithDeviceTimeZone() {
        val event = event("2026-11-08T00:00:00Z", "2026-11-11T00:00:00Z")
        val west = formatCalendarEventDateTimes(event, Locale.UK, ZoneId.of("America/Los_Angeles"))!!
        val east = formatCalendarEventDateTimes(event, Locale.UK, ZoneId.of("Pacific/Kiritimati"))!!
        assertEquals(west, east)
        assertTrue(west.start.contains("8 November 2026"))
        assertTrue(west.end.contains("10 November 2026"))
    }

    @Test fun exclusiveAllDayEndCrossesLeapDayAndYearBoundaries() {
        assertEquals(LocalDate.of(2028, 2, 29), inclusiveAllDayEnd(
            millis("2028-02-28T00:00:00Z"), millis("2028-03-01T00:00:00Z")
        ))
        assertEquals(LocalDate.of(2026, 12, 31), inclusiveAllDayEnd(
            millis("2026-12-30T00:00:00Z"), millis("2027-01-01T00:00:00Z")
        ))
    }

    @Test fun malformedAllDayEndNeverPrecedesStart() {
        val begin = millis("2026-11-08T00:00:00Z")
        assertEquals(LocalDate.of(2026, 11, 8), inclusiveAllDayEnd(begin, begin))
        assertEquals(LocalDate.of(2026, 11, 8), inclusiveAllDayEnd(begin, begin - 86400000L))
    }

    @Test fun timedEventKeepsBothLocalDatesAndHonoursClockFormat() {
        val event = event("2026-11-08T15:30:00Z", "2026-11-08T17:00:00Z", allDay = false)
        val labels = formatCalendarEventDateTimes(event, Locale.UK, ZoneId.of("Asia/Hong_Kong"))!!
        assertTrue(labels.start.contains("8 November 2026"))
        assertTrue(labels.start.contains("23:30"))
        assertTrue(labels.end.contains("9 November 2026"))
        assertTrue(labels.end.contains("01:00"))
        val twelve = formatCalendarEventDateTimes(event, Locale.US, ZoneId.of("UTC"), false)!!
        assertTrue(twelve.start.contains("3:30 PM"))
        assertTrue(twelve.end.contains("5:00 PM"))
    }

    @Test fun daylightSavingOverlapDistinguishesTheTwoOffsets() {
        val labels = formatCalendarEventDateTimes(
            event("2026-11-01T05:30:00Z", "2026-11-01T06:30:00Z", allDay = false),
            Locale.US, ZoneId.of("America/New_York")
        )!!
        assertTrue(labels.start.contains("01:30 EDT"))
        assertTrue(labels.end.contains("01:30 EST"))
    }

    @Test fun legacySummaryDoesNotInventEpochDates() {
        assertNull(formatCalendarEventDateTimes(UpcomingCalendarEvent("Legacy", "Today")))
    }
}
