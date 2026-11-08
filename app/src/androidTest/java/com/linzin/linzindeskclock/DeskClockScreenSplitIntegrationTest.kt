package com.linzin.linzindeskclock

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.linzin.linzindeskclock.layout.ClockSide
import com.linzin.linzindeskclock.layout.SplitLayoutPreferences
import com.linzin.linzindeskclock.layout.SplitLayoutState
import com.linzin.linzindeskclock.ui.DeskClockScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Calls the production screen, including its parent observer, dual branch, pagers and callbacks. */
class DeskClockScreenSplitIntegrationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun screenSnapsEachAnchorAndPersistsGeometryAcrossBothDualModeChanges() {
        val prefs = showScreen()
        for ((raw, expected) in listOf(
            0.53f to 0.5f, 0.47f to 0.5f, 0.31f to 0.3f,
            0.69f to 0.7f, 0.27f to 0.3f, 0.73f to 0.7f
        )) {
            dragAndRelease(prefs, raw, expected)
        }
    }

    @Test
    fun screenCommitsBatchedDragOnReleaseOutsideTheOriginalHitTarget() {
        val prefs = showScreen()
        val bounds = root().fetchSemanticsNode().boundsInRoot
        val start = divider().fetchSemanticsNode().boundsInRoot.center - bounds.topLeft
        compose.mainClock.autoAdvance = false
        try {
            root().performTouchInput {
                down(start)
                for (raw in listOf(0.67f, 0.53f, 0.47f, 0.33f)) {
                    moveTo(start + Offset((raw - 0.7f) * bounds.width - touchSlop, 0f))
                }
                up()
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        assertGeometry(0.3f)
        assertSaved(prefs, 0.3f, ClockSide.RIGHT)
    }

    private var touchSlop = 0f

    private fun showScreen(): SharedPreferences {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        // Keep settings, tutorials and paging deterministic without changing the user's preferences.
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences("split-screen-test-$name", mode)
        }
        val prefs = context.getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear()
            .putString("pref_layout_mode", "DUAL_LEFT_LARGE")
            .putFloat(SplitLayoutPreferences.RATIO_KEY, 0.7f)
            .putString(SplitLayoutPreferences.CLOCK_SIDE_KEY, ClockSide.LEFT.name)
            .putBoolean("pref_dismiss_gesture_tutorial", true)
            .putBoolean("pref_dismiss_layout_tip", true)
            .putBoolean("pref_burn_in_enabled", false)
            .putBoolean("pref_weather_enabled", false)
            .putBoolean("pref_show_calendar_events", false)
            .putLong("pref_timeout_millis", -1L)
            .putInt("pref_main_page", 1)
            .putInt("pref_side_page", 1)
            .commit()
        compose.setContent {
            MaterialTheme {
                val slop = LocalViewConfiguration.current.touchSlop
                SideEffect { touchSlop = slop }
                CompositionLocalProvider(LocalContext provides context) {
                    DeskClockScreen(hasHingeSensor = false, modifier = Modifier.testTag(ROOT))
                }
            }
        }
        compose.waitForIdle()
        assertGeometry(0.7f)
        return prefs
    }

    private fun dragAndRelease(prefs: SharedPreferences, raw: Float, expected: Float) {
        val before = divider().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        val savedBefore = compose.runOnIdle { SplitLayoutPreferences.load(prefs) }
        val bounds = root().fetchSemanticsNode().boundsInRoot
        val hit = divider().fetchSemanticsNode().boundsInRoot
        // Exercise the invisible widget-side part of the actual 48dp target.
        val side = if (before >= 0.5f) 1f else -1f
        val start = hit.center - bounds.topLeft + Offset(side * hit.width * 0.3f, 0f)
        val direction = if (raw < before) -1f else 1f
        root().performTouchInput {
            down(start)
            moveTo(start + Offset((raw - before) * bounds.width + direction * touchSlop, 0f))
        }
        assertGeometry(raw)
        compose.runOnIdle { assertEquals(savedBefore, SplitLayoutPreferences.load(prefs)) }
        root().performTouchInput { up() }
        assertGeometry(expected)
        assertSaved(prefs, expected, if (raw < 0.5f) ClockSide.RIGHT else ClockSide.LEFT)
    }

    private fun assertGeometry(ratio: Float) {
        val root = root().fetchSemanticsNode().boundsInRoot
        val divider = divider().fetchSemanticsNode()
        assertEquals(ratio, divider.config[SemanticsProperties.ProgressBarRangeInfo].current, 0.0001f)
        assertEquals(root.left + root.width * ratio, divider.boundsInRoot.center.x, 1f)
    }

    private fun assertSaved(prefs: SharedPreferences, ratio: Float, side: ClockSide) {
        compose.runOnIdle {
            assertEquals(SplitLayoutState(ratio, side), SplitLayoutPreferences.load(prefs))
            assertEquals(if (side == ClockSide.LEFT) "DUAL_LEFT_LARGE" else "DUAL_RIGHT_LARGE",
                prefs.getString("pref_layout_mode", null))
            assertEquals(1, prefs.getInt("pref_main_page", -1))
            assertEquals(1, prefs.getInt("pref_side_page", -1))
        }
    }

    private fun root() = compose.onNodeWithTag(ROOT)
    private fun divider() = compose.onNode(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
        useUnmergedTree = true
    )

    private companion object {
        const val ROOT = "actual-desk-clock-screen-split"
    }
}
