package com.linzin.linzindeskclock

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.isDisplayed
import androidx.core.os.ConfigurationCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.linzin.linzindeskclock.calendar.UpcomingCalendarEvent
import com.linzin.linzindeskclock.calendar.formatCalendarEventDateTimes
import com.linzin.linzindeskclock.localization.getCurrentLanguageTag
import com.linzin.linzindeskclock.localization.setAppLanguage
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real launcher Activity and its existing content, without installing test content. */
@RunWith(AndroidJUnit4::class)
class MainActivityV11SmokeTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val preferences get() = context.getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE)
    private lateinit var savedPreferences: Map<String, *>
    private lateinit var savedLanguage: String
    private var activityScenario: ActivityScenario<MainActivity>? = null

    @Before fun prepareLauncher() {
        savedPreferences = preferences.all.toMap()
        instrumentation.runOnMainSync { savedLanguage = getCurrentLanguageTag(context) }
        // Exercise the manual entry path while avoiding network, calendar and background service work.
        preferences.edit().clear()
            .putInt("pref_non_fold_bypass_count", 0)
            .putBoolean("pref_dismiss_gesture_tutorial", true)
            .putBoolean("pref_dismiss_layout_tip", true)
            .putBoolean("pref_dismiss_clamshell_notice", true)
            .putBoolean("pref_weather_enabled", false)
            .putBoolean("pref_show_calendar_events", false)
            .putBoolean("auto_clock_enabled", false)
            .putString("pref_layout_mode", ClockLayoutMode.DUAL_LEFT_LARGE.name)
            .putFloat("pref_split_ratio", 0.5f)
            .putString("pref_clock_side", "LEFT")
            .putInt("pref_main_page", 0)
            .putInt("pref_side_page", 0)
            .commit()
        instrumentation.runOnMainSync { setAppLanguage(context, "zh-TW") }
        instrumentation.waitForIdleSync()
    }

    @After fun restoreLauncher() {
        activityScenario?.close()
        activityScenario = null
        if (::savedLanguage.isInitialized) {
            instrumentation.runOnMainSync { setAppLanguage(context, savedLanguage) }
            instrumentation.waitForIdleSync()
        }
        if (::savedPreferences.isInitialized) {
            val editor = preferences.edit().clear()
            savedPreferences.forEach { (key, value) -> editor.restore(key, value) }
            editor.commit()
        }
    }

    @Test fun selectedLanguageSurvivesColdActivityLaunchRotationAndRecreation() {
        val scenario = launch()
        dismissLegacyNotice()
        assertTraditionalChinese(scenario)
        compose.onNodeWithText(string(R.string.auto_setup)).assertIsDisplayed()

        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        waitForOrientation(scenario, Configuration.ORIENTATION_LANDSCAPE)
        assertTraditionalChinese(scenario)
        compose.onNodeWithText(string(R.string.auto_setup)).assertIsDisplayed()

        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        waitForOrientation(scenario, Configuration.ORIENTATION_PORTRAIT)
        assertTraditionalChinese(scenario)

        scenario.recreate()
        compose.waitForIdle()
        assertTraditionalChinese(scenario)
        compose.onNodeWithText(string(R.string.auto_setup)).assertIsDisplayed()
    }

    @Test fun manualClockEntryRendersCalendarAndOpensLocalisedSettings() {
        val scenario = launch()
        dismissLegacyNotice()
        compose.onNodeWithText(string(R.string.btn_not_foldable)).performClick()
        compose.onNodeWithText(string(R.string.non_fold_btn_accept)).performScrollTo().performClick()

        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(SimpleDateFormat("MMMM", Locale.US).format(Date()).uppercase(Locale.US))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("clock_panel").assertIsDisplayed()
        compose.onNodeWithTag("widget_panel").assertIsDisplayed()
        compose.onNodeWithText(SimpleDateFormat("MMMM", Locale.US).format(Date()).uppercase(Locale.US)).assertIsDisplayed()
        assertTraditionalChinese(scenario)

        // Run the production event formatter on the device too: API 21 requires the desugared java.time path.
        val begin = 1_700_000_000_000L
        val labels = formatCalendarEventDateTimes(
            UpcomingCalendarEvent("Synthetic event", "", beginMillis = begin, endMillis = begin + 3_600_000L),
            locale = Locale.TAIWAN,
            zoneId = ZoneId.of("UTC"),
            use24Hour = true
        )
        assertNotNull(labels)
        assertTrue(labels!!.start.isNotBlank())
        assertTrue(labels.end.isNotBlank())

        compose.onNodeWithTag("clock_panel").performTouchInput { click(center) }
        compose.onNodeWithTag("clock_settings").performClick()
        compose.waitUntil(5_000) { compose.onNodeWithText(string(R.string.settings_title)).isDisplayed() }
        compose.onNodeWithText(string(R.string.settings_title)).assertIsDisplayed()
        assertTraditionalChinese(scenario)
    }

    private fun launch(): ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java).also {
        activityScenario = it
        compose.waitForIdle()
    }

    private fun dismissLegacyNotice() {
        val title = string(R.string.legacy_android_dialog_title)
        if (compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText(string(R.string.legacy_android_dialog_btn_ok)).performScrollTo().performClick()
        }
        compose.waitForIdle()
    }

    private fun string(resourceId: Int): String {
        var value = ""
        activityScenario!!.onActivity { value = it.getString(resourceId) }
        return value
    }

    private fun assertTraditionalChinese(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity {
            val locale = ConfigurationCompat.getLocales(it.resources.configuration)[0]
            assertNotNull(locale)
            assertEquals("zh", locale!!.language)
            assertEquals("TW", locale.country)
            assertEquals("zh-TW", getCurrentLanguageTag(it))
            assertEquals("zh-TW", preferences.getString("pref_app_language", null))
        }
    }

    private fun waitForOrientation(scenario: ActivityScenario<MainActivity>, expected: Int) {
        compose.waitUntil(10_000) {
            var actual = Configuration.ORIENTATION_UNDEFINED
            scenario.onActivity { actual = it.resources.configuration.orientation }
            actual == expected
        }
        compose.waitForIdle()
    }

    private fun SharedPreferences.Editor.restore(key: String, value: Any?) {
        when (value) {
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is String -> putString(key, value)
            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }
}
