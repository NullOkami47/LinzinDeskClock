package com.linzin.linzindeskclock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.linzin.linzindeskclock.ui.ClockExitOverlay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ClockExitOverlayTest {
    @get:Rule val compose = createComposeRule()
    private fun show(exit: () -> Unit) = compose.setContent {
        Box(Modifier.fillMaxSize().testTag("clock")) { ClockExitOverlay(exit) }
    }
    @Test fun bottomStripAndHandleStayThin() {
        show { }
        compose.onNodeWithTag("clock_exit_edge").assertHeightIsEqualTo(12.dp)
        compose.onNodeWithTag("clock_exit_handle").assertHeightIsEqualTo(2.dp)
    }
    @Test fun topRightButtonExits() {
        var exits=0; show { exits++ }
        compose.onNodeWithTag("clock_exit_button").performClick()
        compose.runOnIdle { assertEquals(1,exits) }
    }
    @Test fun longBottomEdgeSwipeExits() {
        var exits=0; show { exits++ }
        compose.onNodeWithTag("clock_exit_edge").performTouchInput {
            swipe(Offset(center.x,height-2f),Offset(center.x,-400f),500)
        }
        compose.runOnIdle { assertEquals(1,exits) }
    }
    @Test fun accidentalBottomMovementDoesNotExit() {
        var exits=0; show { exits++ }
        compose.onNodeWithTag("clock_exit_edge").performTouchInput { swipe(center,center-Offset(0f,8f),150) }
        compose.runOnIdle { assertEquals(0,exits) }
    }
    @Test fun widgetSwipeAwayFromEdgeDoesNotExit() {
        var exits=0; show { exits++ }
        compose.onNodeWithTag("clock").performTouchInput { swipe(Offset(width*.3f,height*.6f),Offset(width*.3f,height*.2f),500) }
        compose.runOnIdle { assertEquals(0,exits) }
    }
}
