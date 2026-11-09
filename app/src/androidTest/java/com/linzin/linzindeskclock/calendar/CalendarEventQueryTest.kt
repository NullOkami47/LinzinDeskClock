package com.linzin.linzindeskclock.calendar

import android.database.MatrixCursor
import android.provider.CalendarContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import org.junit.Assert.*
import org.junit.rules.TestRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarEventQueryTest {
    @get:Rule val emulator: TestRule = CalendarEmulatorOnlyRule()
    private val context get() = CalendarFixtureContext(InstrumentationRegistry.getInstrumentation().targetContext)
    private val now = Instant.parse("2026-11-08T12:00:00Z").toEpochMilli()

    @Test fun queryUsesInstanceIdentityAndFullDetailsWithin48HoursAndThreeLiveRows() {
        var returnedCursor: MatrixCursor? = null
        val result = queryUpcomingCalendarEvents(context, now) { uri, projection, order ->
            assertEquals("content://com.android.calendar/instances/when/$now/${now + 172800000L}", uri.toString())
            assertEquals("${CalendarContract.Instances.BEGIN} ASC", order)
            assertArrayEquals(arrayOf("event_id", "title", "begin", "end", "allDay",
                "eventLocation", "description", "calendar_displayName"), projection)
            MatrixCursor(projection).also { cursor ->
                returnedCursor = cursor
                cursor.addRow(arrayOf(1L, "Expired", now - 2000, now - 1, 0, null, null, null))
                repeat(4) { index ->
                    cursor.addRow(arrayOf(42L + index, "Full fixture title $index", now + index * 60000L,
                        now + 3600000, 0, "Room 4", "Line one\nLine two", "Fixture calendar"))
                }
            }
        }
        assertEquals(3, result.size)
        assertEquals(42L, result.first().eventId)
        assertEquals(now, result.first().beginMillis)
        assertEquals(now + 3600000, result.first().endMillis)
        assertEquals("Room 4", result.first().location)
        assertEquals("Line one\nLine two", result.first().description)
        assertEquals("Fixture calendar", result.first().calendarName)
        assertTrue(returnedCursor!!.isClosed)
    }

    @Test fun deniedPermissionNeverInvokesProvider() {
        val context = context.apply { permissionGranted = false }
        assertTrue(queryUpcomingCalendarEvents(context, now) { _, _, _ ->
            error("Must not query without READ_CALENDAR")
        }.isEmpty())
    }

    @Test fun revokedPermissionDuringQueryDiscardsPrivateResultsAndClosesCursor() {
        val context = context
        var cursor: MatrixCursor? = null
        val result = queryUpcomingCalendarEvents(context, now) { _, projection, _ ->
            context.permissionGranted = false
            MatrixCursor(projection).also {
                cursor = it
                it.addRow(arrayOf(42L, "Fixture", now, now + 60000, 0, null, null, null))
            }
        }
        assertTrue(result.isEmpty())
        assertTrue(cursor!!.isClosed)
    }

    @Test fun absentOptionalColumnsStillReturnSummaryAndOccurrenceTimes() {
        val result = queryUpcomingCalendarEvents(context, now) { _, _, _ ->
            MatrixCursor(arrayOf("event_id", "title", "begin", "end", "allDay")).apply {
                addRow(arrayOf(42L, null, now, now + 60000, 0))
            }
        }.single()
        assertTrue(result.title.isNotBlank())
        assertNull(result.location)
        assertNull(result.description)
        assertNull(result.calendarName)
        assertEquals(42L, result.eventId)
    }

    @Test fun ongoingAllDayEventIsRetainedAndPastExclusiveEndIsFiltered() {
        val result = queryUpcomingCalendarEvents(context, now) { _, projection, _ ->
            MatrixCursor(projection).apply {
                addRow(arrayOf(1L, "Past", Instant.parse("2026-11-05T00:00:00Z").toEpochMilli(),
                    Instant.parse("2026-11-07T00:00:00Z").toEpochMilli(), 1, null, null, null))
                addRow(arrayOf(2L, "Ongoing", Instant.parse("2026-11-07T00:00:00Z").toEpochMilli(),
                    Instant.parse("2026-11-11T00:00:00Z").toEpochMilli(), 1, null, null, null))
            }
        }.single()
        assertEquals("Ongoing", result.title)
        assertTrue(result.isAllDay)
        assertEquals(Instant.parse("2026-11-11T00:00:00Z").toEpochMilli(), result.endMillis)
    }

    @Test fun providerSecurityOrOemFailureReturnsNoPartialData() {
        assertTrue(queryUpcomingCalendarEvents(context, now) { _, _, _ -> throw SecurityException() }.isEmpty())
        assertTrue(queryUpcomingCalendarEvents(context, now) { _, _, _ -> throw IllegalArgumentException() }.isEmpty())
        assertTrue(queryUpcomingCalendarEvents(context, now) { _, _, _ -> null }.isEmpty())
    }
}
