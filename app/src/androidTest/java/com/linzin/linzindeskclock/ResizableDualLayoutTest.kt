package com.linzin.linzindeskclock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import com.linzin.linzindeskclock.layout.ClockSide
import com.linzin.linzindeskclock.layout.SplitLayoutState
import com.linzin.linzindeskclock.ui.ResizableDualLayout
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Device tests: no Activity integration or preference IO is needed for this component contract. */
class ResizableDualLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun continuousDragReportsEveryDeltaWithoutSnappingBeforeRelease() {
        val harness = showLayout()
        val path = beginDrag(harness, firstRatio = 0.68f)
        assertRatio(harness, 0.68f)
        root().performTouchInput { moveTo(path.positionFor(0.54f)) }
        assertRatio(harness, 0.54f)
        assertPhysicalSplit(0.54f, ClockSide.LEFT)

        // Hold composition frames as well as batching input: each delta must use the drag
        // accumulator, not a parent-state value waiting for recomposition.
        compose.mainClock.autoAdvance = false
        try {
            root().performTouchInput {
                moveTo(path.positionFor(0.53f))
                moveTo(path.positionFor(0.52f))
                moveTo(path.positionFor(0.49f))
                moveTo(path.positionFor(0.47f))
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        assertRatio(harness, 0.47f)
        assertPhysicalSplit(0.47f, ClockSide.RIGHT)
        compose.runOnIdle {
            for (expected in listOf(0.54f, 0.53f, 0.52f, 0.49f, 0.47f)) {
                assertTrue("Missing continuous update for $expected: ${harness.updates}",
                    harness.updates.any { abs(it.ratio - expected) < RATIO_TOLERANCE })
            }
            assertTrue(harness.finished.isEmpty())
        }
        root().performTouchInput { cancel() }
        assertRatio(harness, 0.47f)
        compose.runOnIdle { assertTrue(harness.finished.isEmpty()) }
    }

    @Test
    fun releaseSnapsUpperCentreRegionAndFinishesAfterReportingTheFinalState() {
        assertReleaseSnap(targetRatio = 0.53f, expectedSide = ClockSide.LEFT)
    }

    @Test
    fun releaseSnapsLowerCentreRegionAndKeepsTheClockOnTheRight() {
        assertReleaseSnap(targetRatio = 0.47f, expectedSide = ClockSide.RIGHT)
    }

    @Test
    fun lockedLayoutIgnoresDraggingAndExposesNoResizeAction() {
        val harness = showLayout(locked = true)
        divider().assertIsNotEnabled()
        assertFalse(divider().fetchSemanticsNode().config.contains(SemanticsActions.SetProgress))
        assertFalse(divider().fetchSemanticsNode().config.contains(SemanticsActions.CustomActions))
        beginDrag(harness, firstRatio = 0.4f)
        root().performTouchInput { up() }
        assertRatio(harness, 0.7f)
        assertPhysicalSplit(0.7f, ClockSide.LEFT)
        compose.runOnIdle {
            assertTrue(harness.updates.isEmpty())
            assertTrue(harness.finished.isEmpty())
        }
    }

    @Test
    fun lockingDuringADragCancelsWithoutSnappingOrCallingFinished() {
        val harness = showLayout()
        val staleAccessibilityAction = requireNotNull(
            divider().fetchSemanticsNode().config[SemanticsActions.SetProgress].action
        )
        val path = beginDrag(harness, firstRatio = 0.53f)
        assertRatio(harness, 0.53f)
        val updatesBeforeLock = compose.runOnIdle {
            harness.locked = true
            harness.updates.size
        }
        divider().assertIsNotEnabled()
        root().performTouchInput {
            moveTo(path.positionFor(0.35f))
            up()
        }
        compose.runOnIdle {
            // Also reject an action retained by an accessibility service before the lock changed.
            assertFalse(staleAccessibilityAction(0.3f))
            assertEquals(updatesBeforeLock, harness.updates.size)
            assertTrue(harness.finished.isEmpty())
        }
        assertRatio(harness, 0.53f)
    }

    @Test
    fun rememberedAndSaveableChildStateSurviveCrossingInBothDirections() {
        val harness = showLayout(statefulChildren = true)
        compose.onNodeWithTag(CLOCK_COUNTER).performClick()
        compose.onNodeWithTag(WIDGET_COUNTER).performClick().performClick()
        val originalClockIdentity = requireNotNull(compose.runOnIdle { harness.clockProbe.identity })
        val originalWidgetIdentity = requireNotNull(compose.runOnIdle { harness.widgetProbe.identity })

        val path = beginDrag(harness, firstRatio = 0.4f)
        assertPhysicalSplit(0.4f, ClockSide.RIGHT)
        assertCounters(clock = 1, widgets = 2)
        root().performTouchInput { moveTo(path.positionFor(0.6f)) }
        assertPhysicalSplit(0.6f, ClockSide.LEFT)
        root().performTouchInput { up() }
        assertCounters(clock = 1, widgets = 2)
        compose.runOnIdle {
            assertSame(originalClockIdentity, harness.clockProbe.identity)
            assertSame(originalWidgetIdentity, harness.widgetProbe.identity)
            assertEquals(1, harness.clockProbe.entries)
            assertEquals(1, harness.widgetProbe.entries)
            assertEquals(0, harness.clockProbe.disposals)
            assertEquals(0, harness.widgetProbe.disposals)
        }
        compose.onNodeWithTag(CLOCK_COUNTER).performClick()
        assertCounters(clock = 2, widgets = 2)
    }

    @Test
    fun verticalWidgetSwipePagesNormallyWithoutResizingTheSplit() {
        val harness = showLayout(widgetPager = true)
        swipeWidgetUp()
        compose.runOnIdle {
            assertEquals(1, harness.pager.currentPage)
            assertFalse(harness.pager.isScrollInProgress)
            assertTrue(harness.updates.isEmpty())
            assertTrue(harness.finished.isEmpty())
        }
        assertRatio(harness, 0.7f)
    }

    @Test
    fun horizontalSwipeStartingInsideWidgetsDoesNotClaimTheDivider() {
        val harness = showLayout(widgetPager = true)
        val rootBounds = root().fetchSemanticsNode().boundsInRoot
        val widgetBounds = compose.onNodeWithTag(WIDGETS).fetchSemanticsNode().boundsInRoot
        root().performTouchInput {
            // Start well inside widgets, then cross the divider into the clock on any screen size.
            swipe(
                start = Offset(widgetBounds.left - rootBounds.left + widgetBounds.width * 0.8f, height * 0.5f),
                end = Offset(width * 0.25f, height * 0.5f),
                durationMillis = 500
            )
        }
        compose.runOnIdle {
            assertEquals(0, harness.pager.currentPage)
            assertTrue(harness.updates.isEmpty())
            assertTrue(harness.finished.isEmpty())
        }
        assertRatio(harness, 0.7f)
    }

    @Test
    fun dividerHitZoneWinsOverWidgetsAndPreservesTheHoistedPagerAcrossCrossing() {
        val harness = showLayout(widgetPager = true)
        swipeWidgetUp()
        compose.runOnIdle { assertEquals(1, harness.pager.currentPage) }
        // The invisible, widget-side portion of the 48dp target must still resize the split.
        beginDrag(harness, firstRatio = 0.4f, widgetSideOfHitZone = true)
        assertRatio(harness, 0.4f)
        assertPhysicalSplit(0.4f, ClockSide.RIGHT)
        root().performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(1, harness.pager.currentPage)
            assertFalse(harness.pager.isScrollInProgress)
            assertEquals(1, harness.finished.size)
            assertEquals(0.4f, harness.finished.single().ratio, RATIO_TOLERANCE)
        }
    }

    @Test
    fun accessibilityCanAdjustAwayFromCentreWithoutBeingTrappedByReleaseSnapping() {
        val harness = showLayout()
        divider().performSemanticsAction(SemanticsActions.SetProgress) { action ->
            assertTrue(action(0.5f))
        }
        divider().performSemanticsAction(SemanticsActions.SetProgress) { action ->
            assertTrue(action(0.53f))
        }
        assertRatio(harness, 0.53f)
        compose.runOnIdle {
            assertEquals(2, harness.finished.size)
            assertEquals(0.53f, harness.finished.last().ratio, RATIO_TOLERANCE)
        }
    }

    private fun assertReleaseSnap(targetRatio: Float, expectedSide: ClockSide) {
        val harness = showLayout()
        beginDrag(harness, firstRatio = targetRatio)
        assertRatio(harness, targetRatio)
        compose.runOnIdle { assertTrue(harness.finished.isEmpty()) }
        root().performTouchInput { up() }
        assertRatio(harness, 0.5f)
        assertPhysicalSplit(0.5f, expectedSide)
        compose.runOnIdle {
            assertEquals(SplitLayoutState(0.5f, expectedSide), harness.updates.last())
            assertEquals(listOf(SplitLayoutState(0.5f, expectedSide)), harness.finished)
        }
    }

    private fun root() = compose.onNodeWithTag(ROOT)

    // Select the real adjustable semantics, independent of the device language; no production tag.
    private fun divider() = compose.onNode(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
        useUnmergedTree = true
    )

    private fun assertRatio(harness: Harness, expected: Float) {
        compose.runOnIdle { assertEquals(expected, harness.state.ratio, RATIO_TOLERANCE) }
    }

    private fun assertPhysicalSplit(ratio: Float, side: ClockSide) {
        val bounds = root().fetchSemanticsNode().boundsInRoot
        val clock = compose.onNodeWithTag(CLOCK).fetchSemanticsNode().boundsInRoot
        val widgets = compose.onNodeWithTag(WIDGETS).fetchSemanticsNode().boundsInRoot
        val expectedSplit = bounds.left + bounds.width * ratio
        val left = if (side == ClockSide.LEFT) clock else widgets
        val right = if (side == ClockSide.LEFT) widgets else clock
        assertEquals(bounds.left, left.left, 1f)
        assertEquals(expectedSplit, left.right, 1f)
        assertEquals(expectedSplit, right.left, 1f)
        assertEquals(bounds.right, right.right, 1f)
        assertEquals(expectedSplit, divider().fetchSemanticsNode().boundsInRoot.center.x, 1f)
    }

    private fun beginDrag(
        harness: Harness,
        firstRatio: Float,
        widgetSideOfHitZone: Boolean = false
    ): DragPath {
        val rootBounds = root().fetchSemanticsNode().boundsInRoot
        val dividerBounds = divider().fetchSemanticsNode().boundsInRoot
        val initialRatio = compose.runOnIdle { harness.state.ratio }
        val offset = if (widgetSideOfHitZone) Offset(dividerBounds.width * 0.3f, 0f) else Offset.Zero
        val path = DragPath(
            start = dividerBounds.center - rootBounds.topLeft + offset,
            width = rootBounds.width,
            initialRatio = initialRatio,
            // The gesture detector consumes touch slop once, then forwards every remaining pixel.
            signedSlop = harness.touchSlop * if (firstRatio < initialRatio) -1f else 1f
        )
        root().performTouchInput {
            down(path.start)
            moveTo(path.positionFor(firstRatio))
        }
        return path
    }

    private fun swipeWidgetUp() {
        compose.onNodeWithTag(WIDGETS).performTouchInput {
            swipeUp(startY = height * 0.8f, endY = height * 0.2f, durationMillis = 500)
        }
    }

    private fun assertCounters(clock: Int, widgets: Int) {
        compose.onNodeWithText("Clock: remembered=$clock, saved=$clock").assertIsDisplayed()
        compose.onNodeWithText("Widgets: remembered=$widgets, saved=$widgets").assertIsDisplayed()
    }

    private fun showLayout(
        locked: Boolean = false,
        statefulChildren: Boolean = false,
        widgetPager: Boolean = false
    ): Harness {
        val harness = Harness(locked)
        compose.setContent {
            MaterialTheme {
                // Intentionally hoisted above the split, just like the real screen's pagers.
                val pager = rememberPagerState { 3 }
                val touchSlop = LocalViewConfiguration.current.touchSlop
                SideEffect {
                    harness.pager = pager
                    harness.touchSlop = touchSlop
                }
                ResizableDualLayout(
                    state = harness.state,
                    onStateChange = { harness.state = it; harness.updates += it },
                    onDragFinished = { harness.finished += harness.state },
                    isUiLocked = harness.locked,
                    clock = {
                        Box(Modifier.fillMaxSize().testTag(CLOCK)) {
                            if (statefulChildren) StatefulPanel("Clock", CLOCK_COUNTER, harness.clockProbe)
                        }
                    },
                    widgets = {
                        Box(Modifier.fillMaxSize().testTag(WIDGETS)) {
                            if (widgetPager) {
                                VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                                    Box(Modifier.fillMaxSize()) { Text("Widget page $page") }
                                }
                            } else if (statefulChildren) {
                                StatefulPanel("Widgets", WIDGET_COUNTER, harness.widgetProbe)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize().testTag(ROOT)
                )
            }
        }
        compose.waitForIdle()
        return harness
    }

    @Composable
    private fun StatefulPanel(label: String, tag: String, probe: PanelProbe) {
        var remembered by remember { mutableIntStateOf(0) }
        var saved by rememberSaveable { mutableIntStateOf(0) }
        val identity = remember { Any() }
        SideEffect { probe.identity = identity }
        DisposableEffect(Unit) {
            probe.entries++
            onDispose { probe.disposals++ }
        }
        Box(Modifier.fillMaxSize().testTag(tag).clickable { remembered++; saved++ }) {
            Text("$label: remembered=$remembered, saved=$saved")
        }
    }

    private class Harness(initialLocked: Boolean) {
        var state by mutableStateOf(SplitLayoutState(0.7f))
        var locked by mutableStateOf(initialLocked)
        val updates = mutableListOf<SplitLayoutState>()
        val finished = mutableListOf<SplitLayoutState>()
        val clockProbe = PanelProbe()
        val widgetProbe = PanelProbe()
        lateinit var pager: PagerState
        var touchSlop = 0f
    }

    private class PanelProbe {
        var identity: Any? = null
        var entries = 0
        var disposals = 0
    }

    private data class DragPath(val start: Offset, val width: Float, val initialRatio: Float, val signedSlop: Float) {
        fun positionFor(ratio: Float) = start + Offset((ratio - initialRatio) * width + signedSlop, 0f)
    }

    private companion object {
        const val ROOT = "test-split-root"
        const val CLOCK = "test-clock-panel"
        const val WIDGETS = "test-widget-panel"
        const val CLOCK_COUNTER = "test-clock-counter"
        const val WIDGET_COUNTER = "test-widget-counter"
        const val RATIO_TOLERANCE = 0.0001f
    }
}
