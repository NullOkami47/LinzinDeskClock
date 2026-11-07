package com.linzin.linzindeskclock

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.linzin.linzindeskclock.autostart.ClockEntryMode
import com.linzin.linzindeskclock.ui.DeskClockScreen
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DeskClockIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun resetDisplayPreferences() {
        context.getSharedPreferences("desk_clock_prefs",Context.MODE_PRIVATE).edit().clear()
            .putBoolean("pref_dismiss_gesture_tutorial",true)
            .putBoolean("pref_dismiss_layout_tip",true).commit()
    }
    private fun show(page: Int, mode: ClockLayoutMode = ClockLayoutMode.SINGLE, onExit: () -> Unit = {}) {
        context.getSharedPreferences("desk_clock_prefs",Context.MODE_PRIVATE).edit()
            .putInt("pref_main_page",page).putString("pref_layout_mode",mode.name).commit()
        compose.setContent {
            MaterialTheme(colorScheme=DeskClockColorScheme) {
                Box(Modifier.fillMaxSize().testTag("clock_root")) {
                    DeskClockScreen(hasHingeSensor=false,entryMode=ClockEntryMode.LOCK_SCREEN,onExit=onExit)
                }
            }
        }
    }
    @Test fun digitalClockRetainsAnExitEvenAfterUiLock() {
        var exits=0; show(0,onExit={ exits++ })
        compose.onNodeWithTag("clock_root").performTouchInput { click(center) }
        compose.onNodeWithTag("clock_ui_lock").performClick()
        compose.onNodeWithTag("clock_settings").assertDoesNotExist()
        compose.onNodeWithTag("clock_exit_button").performClick()
        compose.runOnIdle { assertEquals(1,exits) }
    }
    @Test fun analogueClockAndDualPanelRenderWithExit() {
        show(1,ClockLayoutMode.DUAL_LEFT_LARGE)
        compose.onNodeWithTag("clock_exit_button").assertIsDisplayed()
        compose.onNodeWithTag("clock_exit_edge").assertIsDisplayed()
    }
    @Test fun flipClockAndRightPanelRenderWithExit() {
        show(2,ClockLayoutMode.DUAL_RIGHT_LARGE)
        compose.onNodeWithTag("clock_exit_button").assertIsDisplayed()
        compose.onNodeWithTag("clock_exit_edge").assertIsDisplayed()
    }
}
