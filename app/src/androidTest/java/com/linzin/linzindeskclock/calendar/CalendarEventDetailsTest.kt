package com.linzin.linzindeskclock.calendar

import android.content.ActivityNotFoundException
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linzin.linzindeskclock.ui.SideDateWidget
import java.time.Instant
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarEventDetailsTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: TestRule = RuleChain.outerRule(CalendarEmulatorOnlyRule()).around(compose)
    private val enabled = mutableStateOf(true)
    private val events = mutableStateOf(listOf(fixture()))
    private lateinit var context: CalendarFixtureContext

    private fun fixture() = UpcomingCalendarEvent(
        title = "完整行程名稱：與團隊討論下個月的活動安排及所有細節，這是原本摘要無法完整顯示的標題",
        timeLabel = "10:00", eventId = 42,
        beginMillis = Instant.parse("2026-11-08T02:00:00Z").toEpochMilli(),
        endMillis = Instant.parse("2026-11-08T03:00:00Z").toEpochMilli(),
        location = "會議室 A，二樓", calendarName = "測試行事曆",
        description = (1..35).joinToString("\n") { "第 $it 行：完整說明與活動內容。" }
    )

    private fun show(largeText: Boolean = false) {
        context = CalendarFixtureContext(compose.activity)
        assumeTrue("The emulator must already be unlocked", isCalendarDeviceUnlocked(context))
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                if (largeText) screenHeightDp = 240
            }
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, if (largeText) 2f else 1f)
            ) {
                MaterialTheme {
                    Box(Modifier.size(240.dp, 400.dp)) {
                        SideDateWidget(calendarEvents = events.value, interactionsEnabled = enabled.value)
                    }
                }
            }
        }
    }

    @Test fun tappingRowOpensFullTextWithAccessibleTargetAndNoExternalLaunch() {
        show()
        compose.onNodeWithTag("calendar_event_details").assertDoesNotExist()
        compose.onNodeWithTag("calendar_event_row_0").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("calendar_event_details").assertIsDisplayed()
        compose.onNodeWithTag("calendar_detail_title").assertTextEquals(fixture().title)
        val titleLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("calendar_detail_title").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(titleLayouts)
        }
        assertFalse(titleLayouts.single().hasVisualOverflow)
        compose.onNodeWithTag("calendar_detail_start").assertExists()
        compose.onNodeWithTag("calendar_detail_end").assertExists()
        compose.onNodeWithTag("calendar_detail_location").assertTextEquals(fixture().location!!)
        compose.onNodeWithTag("calendar_detail_calendar").assertTextEquals(fixture().calendarName!!)
        compose.onNodeWithTag("calendar_detail_description").assertTextEquals(fixture().description!!)
        compose.runOnIdle { assertTrue(context.startedIntents.isEmpty()) }
        compose.onNodeWithTag("calendar_details_close").performScrollTo().performClick()
        compose.onNodeWithTag("calendar_event_details").assertDoesNotExist()
    }

    @Test fun uiLockDismissesDetailsAndDoesNotReopenWhenReenabled() {
        show()
        compose.onNodeWithTag("calendar_event_row_0").performClick()
        compose.onNodeWithTag("calendar_event_details").assertIsDisplayed()
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithTag("calendar_event_details").assertDoesNotExist()
        compose.onNodeWithTag("calendar_event_row_0").assertIsNotEnabled()
        compose.runOnIdle { enabled.value = true }
        compose.onNodeWithTag("calendar_event_details").assertDoesNotExist()
    }

    @Test fun clearedCalendarEventsCloseTheSelectedDetails() {
        show()
        compose.onNodeWithTag("calendar_event_row_0").performClick()
        compose.runOnIdle { events.value = emptyList() }
        compose.onNodeWithTag("calendar_event_details").assertDoesNotExist()
        compose.onNodeWithTag("calendar_event_row_0").assertDoesNotExist()
    }

    @Test fun permissionAndKeyguardAreCheckedAtClickRatherThanFromAStaleRender() {
        show()
        compose.runOnIdle { context.permissionGranted = false }
        compose.onNodeWithTag("calendar_event_row_0").performClick()
        compose.onNodeWithTag("calendar_event_details").assertDoesNotExist()
        compose.runOnIdle {
            context.permissionGranted = true
            context.keyguardAvailable = false
        }
        compose.onNodeWithTag("calendar_event_row_0").performClick()
        compose.onNodeWithTag("calendar_event_details").assertDoesNotExist()
        compose.runOnIdle { assertTrue(context.startedIntents.isEmpty()) }
    }

    @Test fun largeTextAndLongDescriptionKeepActionsReachableWithMissingOemCalendar() {
        show(largeText = true)
        context.startFailure = ActivityNotFoundException("Fixture missing calendar")
        compose.onNodeWithTag("calendar_event_row_0").performClick()
        compose.onNodeWithTag("calendar_detail_description").assertTextEquals(fixture().description!!)
        compose.onNodeWithTag("calendar_open_in_app").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, context.startedIntents.size) }
        compose.onNodeWithTag("calendar_event_details").assertIsDisplayed()
        compose.onNodeWithTag("calendar_details_close").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("calendar_event_details").assertDoesNotExist()
    }
}
