package com.linzin.linzindeskclock.power

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.linzin.linzindeskclock.autostart.ClockEntryMode
import com.linzin.linzindeskclock.media.DedicatedMediaEmulatorRule
import com.linzin.linzindeskclock.media.mediaOnMain
import com.linzin.linzindeskclock.ui.DeskClockScreen
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

class ScreenOffTimeoutTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: TestRule = RuleChain.outerRule(DedicatedMediaEmulatorRule()).around(compose)
    private val exits = AtomicInteger()

    private fun show(timeout: Long, mode: ClockEntryMode = ClockEntryMode.LOCK_SCREEN) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences("screen-off-test-$name", mode)
        }
        context.getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear()
            .putLong("pref_timeout_millis", timeout)
            .putBoolean("pref_manual_brightness", true).putFloat("pref_brightness_val", .6f)
            .putBoolean("pref_dismiss_gesture_tutorial", true).putBoolean("pref_dismiss_layout_tip", true)
            .putBoolean("pref_burn_in_enabled", false).putString("low_battery_action", "OFF").commit()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context,
                androidx.activity.compose.LocalActivityResultRegistryOwner provides compose.activity) {
                MaterialTheme {
                    DeskClockScreen(hasHingeSensor = false, entryMode = mode,
                        windowOverride = compose.activity.window, onExit = { exits.incrementAndGet() })
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    @Test fun shorterThanSystemTimeoutLeavesClockInsteadOfPaintingFakeSleep() {
        show(1_000L)
        compose.mainClock.advanceTimeBy(1_100L)
        compose.waitForIdle()
        assertEquals("An expired screen-off timer must exit the clock, not fake sleeping", 1, exits.get())
        assertEquals("Timeout must never simulate power-off by forcing brightness to zero", .6f,
            mediaOnMain { compose.activity.window.attributes.screenBrightness }, .001f)
        assertFalse(keepsScreenOn())
        compose.mainClock.advanceTimeBy(2_000L)
        assertEquals(1, exits.get())
    }

    @Test fun fullTimeoutRunsEvenWhenLongerThanSystemTimeout() {
        val previous = screenOffShell("settings get system screen_off_timeout").trim()
        try {
            screenOffShell("settings put system screen_off_timeout 5000")
            show(6_000L)
            compose.mainClock.advanceTimeBy(5_000L)
            assertEquals(0, exits.get())
            assertTrue(keepsScreenOn())
            compose.mainClock.advanceTimeBy(1_100L)
            compose.waitForIdle()
            assertEquals(1, exits.get())
        } finally {
            screenOffShell("settings put system screen_off_timeout $previous")
        }
    }

    @Test fun neverDoesNotRequestScreenOff() {
        show(-1)
        compose.mainClock.advanceTimeBy(10_000L)
        assertEquals(0, exits.get())
        assertTrue(keepsScreenOn())
    }

    @Test fun touchingClockRestartsWholeIdleWait() {
        show(1_000L)
        compose.mainClock.advanceTimeBy(600L)
        compose.onRoot().performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(600L)
        assertEquals(0, exits.get())
        compose.mainClock.advanceTimeBy(500L)
        compose.waitForIdle()
        assertEquals(1, exits.get())
    }

    @Test fun settingsDialogPausesTimeout() {
        show(1_000L, ClockEntryMode.MANUAL)
        compose.onRoot().performTouchInput { click(center) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("clock_settings").performClick()
        compose.mainClock.advanceTimeBy(2_000L)
        compose.waitForIdle()
        assertEquals(0, exits.get())
    }

    @Test fun backgroundCancelsTimeoutAndResumeStartsFreshWait() {
        show(1_000L)
        compose.mainClock.advanceTimeBy(600L)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.mainClock.advanceTimeBy(2_000L)
        assertEquals(0, exits.get())
        assertFalse(keepsScreenOn())
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.mainClock.advanceTimeBy(600L)
        assertEquals(0, exits.get())
        compose.mainClock.advanceTimeBy(500L)
        compose.waitForIdle()
        assertEquals(1, exits.get())
    }

    private fun keepsScreenOn(): Boolean = mediaOnMain {
        compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
    }
}
