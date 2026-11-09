package com.linzin.linzindeskclock

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class AutoClockSetupTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun automaticSetupIsReachableBeforeManualClockBypass() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { MaterialTheme(colorScheme=DeskClockColorScheme) { InitialScreen(hasHingeSensor=false) } }
        compose.onNodeWithText(context.getString(R.string.auto_setup)).performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText(context.getString(R.string.auto_title)).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.auto_title)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.auto_enable)).assertIsDisplayed()
    }
}
