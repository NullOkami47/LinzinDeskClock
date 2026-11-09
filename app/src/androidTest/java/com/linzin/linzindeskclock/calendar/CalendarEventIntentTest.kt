package com.linzin.linzindeskclock.calendar

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.CalendarContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.rules.TestRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarEventIntentTest {
    @get:Rule val emulator: TestRule = CalendarEmulatorOnlyRule()
    private val event = UpcomingCalendarEvent("Private fixture title", "Today", 42L, 123000L, 456000L)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun viewIntentUsesEventIdAndUnchangedOccurrenceTimesWithoutPrivateContent() {
        val intent = buildCalendarEventViewIntent(event)!!
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("content://com.android.calendar/events/42", intent.data.toString())
        assertEquals(123000L, intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1))
        assertEquals(456000L, intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1))
        assertFalse(intent.hasExtra(CalendarContract.Events.TITLE))
        assertFalse(intent.hasExtra(CalendarContract.Events.DESCRIPTION))
        assertEquals(2, intent.extras!!.keySet().size)
        assertEquals(456000L, buildCalendarEventViewIntent(event.copy(isAllDay = true))!!
            .getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1))
    }

    @Test fun missingIdentityOrTimesDoesNotInventAnExternalTarget() {
        assertNull(buildCalendarEventViewIntent(event.copy(eventId = null)))
        assertNull(buildCalendarEventViewIntent(event.copy(eventId = -1)))
        assertNull(buildCalendarEventViewIntent(event.copy(beginMillis = null)))
        assertNull(buildCalendarEventViewIntent(event.copy(endMillis = null)))
    }

    @Test fun unknownKeyguardAndRevokedPermissionBlockEvenAnExplicitLaunch() {
        val context = CalendarFixtureContext(instrumentation.targetContext)
        context.keyguardAvailable = false
        instrumentation.runOnMainSync {
            assertFalse(canShowCalendarEventDetails(context))
            assertEquals(CalendarEventOpenResult.LOCKED, openCalendarEvent(context, event))
            assertTrue(context.startedIntents.isEmpty())
        }
        context.keyguardAvailable = true
        assumeTrue(isCalendarDeviceUnlocked(context))
        context.permissionGranted = false
        instrumentation.runOnMainSync {
            assertEquals(CalendarEventOpenResult.PERMISSION_REQUIRED, openCalendarEvent(context, event))
            assertTrue(context.startedIntents.isEmpty())
        }
    }

    @Test fun explicitLaunchHandlesMissingOrDeniedOemCalendarWithoutCrashing() {
        val context = CalendarFixtureContext(instrumentation.targetContext)
        assumeTrue("Emulator must already be unlocked", isCalendarDeviceUnlocked(context))
        instrumentation.runOnMainSync {
            context.startFailure = ActivityNotFoundException("Fixture missing handler")
            assertEquals(CalendarEventOpenResult.UNAVAILABLE, openCalendarEvent(context, event))
            context.startFailure = SecurityException("Fixture denied handler")
            assertEquals(CalendarEventOpenResult.UNAVAILABLE, openCalendarEvent(context, event))
            context.startFailure = null
            assertEquals(CalendarEventOpenResult.OPENED, openCalendarEvent(context, event))
            assertEquals(3, context.startedIntents.size)
        }
        // All startActivity calls are intercepted; no system calendar app is launched.
    }
}
