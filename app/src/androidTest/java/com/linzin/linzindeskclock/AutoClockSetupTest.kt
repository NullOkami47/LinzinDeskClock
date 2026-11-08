package com.linzin.linzindeskclock

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class AutoClockSetupTest {
    @get:Rule val compose=createComposeRule()
    @Test fun automaticSetupIsReachableBeforeManualClockBypass() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { MaterialTheme(colorScheme=DeskClockColorScheme) { InitialScreen(hasHingeSensor=false) } }
        compose.onNodeWithText(context.getString(R.string.auto_setup)).performClick()
        compose.onNodeWithText(context.getString(R.string.auto_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.auto_enable)).assertIsDisplayed()
    }
}
