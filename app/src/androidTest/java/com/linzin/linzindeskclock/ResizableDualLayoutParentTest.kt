package com.linzin.linzindeskclock

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.linzin.linzindeskclock.layout.ClockSide
import com.linzin.linzindeskclock.layout.SplitLayoutPreferences
import com.linzin.linzindeskclock.layout.SplitLayoutState
import com.linzin.linzindeskclock.ui.ResizableDualLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Exercises the actual split inside DeskClockScreen's parent structure: an Initial-pass observer,
 * a shared dual-mode branch, hoisted pagers, and save-then-mode-update completion callbacks.
 * No Activity, service or real-phone launch is needed to describe this integration contract.
 */
class ResizableDualLayoutParentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun equalPixelMovesAcrossLayoutFramesReachTheFingerBeforeRelease() {
        val harness = showParent()
        val bounds = root().fetchSemanticsNode().boundsInRoot
        val start = divider().fetchSemanticsNode().boundsInRoot.center - bounds.topLeft
        val step = 12f
        root().performTouchInput { down(start) }
        // Equal steps and a real remeasure between each are essential: batching all moves in
        // one frame hides the moving hit-node's duplicate-local-position event suppression.
        repeat(8) { index ->
            val travel = step * (index + 1)
            root().performTouchInput {
                moveTo(start - Offset(harness.touchSlop + travel, 0f))
            }
            assertSplit(harness, 0.7f - travel / bounds.width)
            compose.runOnIdle { assertTrue(harness.finished.isEmpty()) }
        }
        root().performTouchInput { cancel() }
        compose.runOnIdle { assertTrue(harness.finished.isEmpty()) }
    }

    @Test
    fun releasesAtThirtyFiftyAndSeventyKeepActualGeometryAndPersistOnce() {
        val harness = showParent()
        for ((raw, expected) in listOf(0.53f to 0.5f, 0.47f to 0.5f, 0.31f to 0.3f, 0.69f to 0.7f)) {
            val before = compose.runOnIdle { harness.state.ratio }
            val count = compose.runOnIdle { harness.finished.size }
            val bounds = root().fetchSemanticsNode().boundsInRoot
            val start = divider().fetchSemanticsNode().boundsInRoot.center - bounds.topLeft
            val direction = if (raw < before) -1f else 1f
            root().performTouchInput {
                down(start)
                moveTo(start + Offset((raw - before) * bounds.width + direction * harness.touchSlop, 0f))
            }
            assertSplit(harness, raw)
            compose.runOnIdle { assertEquals(count, harness.finished.size) }
            root().performTouchInput { up() }
            assertSplit(harness, expected)
            compose.runOnIdle {
                assertEquals(count + 1, harness.finished.size)
                assertEquals(harness.state, harness.finished.last())
                assertEquals(harness.state, SplitLayoutPreferences.load(harness.prefs))
            }
        }
    }
    @Test
    fun releaseAtEachBandBoundaryUsesTheRenderedDividerAndPersistsIt() {
        val harness = showParent()
        for ((raw, expected) in listOf(
            0.25f to 0.3f, 0.35f to 0.3f,
            0.45f to 0.5f, 0.55f to 0.5f,
            0.65f to 0.7f, 0.75f to 0.7f
        )) {
            releaseAt(harness, raw, expected)
        }
    }

    @Test
    fun jumpFromEitherEdgeOfTheHitZoneReleasesFarOutsideTheOriginalNode() {
        val harness = showParent()
        for ((raw, expected, edge) in listOf(
            Triple(0.31f, 0.3f, 1f), Triple(0.69f, 0.7f, -1f),
            Triple(0.47f, 0.5f, 1f), Triple(0.73f, 0.7f, -1f)
        )) {
            releaseAt(harness, raw, expected, edge)
        }
    }

    @Test
    fun batchedMovesThenReleaseUseTheLastDragStateBeforeParentRecomposition() {
        val harness = showParent()
        val bounds = root().fetchSemanticsNode().boundsInRoot
        val start = divider().fetchSemanticsNode().boundsInRoot.center - bounds.topLeft
        compose.mainClock.autoAdvance = false
        try {
            root().performTouchInput {
                down(start)
                for (raw in listOf(0.67f, 0.53f, 0.47f, 0.33f)) {
                    moveTo(start + Offset((raw - 0.7f) * bounds.width - harness.touchSlop, 0f))
                }
                up()
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        assertSplit(harness, 0.3f)
        compose.runOnIdle {
            for (raw in listOf(0.67f, 0.53f, 0.47f, 0.33f)) {
                assertTrue("Missing unsnapped drag update $raw: ${harness.updates}",
                    harness.updates.any { kotlin.math.abs(it.ratio - raw) < 0.0001f })
            }
            assertEquals(listOf(SplitLayoutState(0.3f)), harness.finished)
            assertEquals(harness.state, SplitLayoutPreferences.load(harness.prefs))
        }
    }

    @Test
    fun crossingBothDualModesKeepsPagerStateAndCentreClockSide() {
        val harness = showParent()
        val clockPager = compose.runOnIdle { harness.clockPager }
        val widgetPager = compose.runOnIdle { harness.widgetPager }
        compose.onNodeWithTag(WIDGETS).performTouchInput {
            swipeUp(startY = height * 0.8f, endY = height * 0.2f, durationMillis = 500)
        }
        compose.runOnIdle {
            assertEquals(1, harness.widgetPager.currentPage)
            // Choosing RIGHT must retain the divider even while the clock panel is smaller.
            harness.selectClockSide(ClockSide.RIGHT)
            assertEquals(0.7f, harness.state.ratio, 0f)
            assertEquals(Mode.DUAL_RIGHT, harness.mode)
            assertEquals(ClockSide.RIGHT, harness.state.clockSide)
            assertEquals(SplitLayoutState(0.7f, ClockSide.RIGHT), SplitLayoutPreferences.load(harness.prefs))
        }
        assertSplit(harness, 0.7f)
        releaseAt(harness, 0.47f, 0.5f)
        compose.runOnIdle {
            assertEquals(Mode.DUAL_RIGHT, harness.mode)
            assertEquals(ClockSide.RIGHT, harness.state.clockSide)
            assertEquals(SplitLayoutState(0.5f, ClockSide.RIGHT), SplitLayoutPreferences.load(harness.prefs))
        }
        releaseAt(harness, 0.31f, 0.3f)
        compose.runOnIdle {
            assertEquals(Mode.DUAL_RIGHT, harness.mode)
            assertEquals(ClockSide.RIGHT, harness.state.clockSide)
            // Only an explicit selection changes side; LEFT keeps the existing smaller panel.
            harness.selectClockSide(ClockSide.LEFT)
            assertEquals(0.3f, harness.state.ratio, 0f)
            assertEquals(Mode.DUAL_LEFT, harness.mode)
            assertEquals(ClockSide.LEFT, harness.state.clockSide)
            assertEquals(SplitLayoutState(0.3f, ClockSide.LEFT), SplitLayoutPreferences.load(harness.prefs))
        }
        assertSplit(harness, 0.3f)
        releaseAt(harness, 0.53f, 0.5f)
        compose.runOnIdle {
            assertEquals(Mode.DUAL_LEFT, harness.mode)
            assertEquals(ClockSide.LEFT, harness.state.clockSide)
            assertEquals(SplitLayoutState(0.5f, ClockSide.LEFT), SplitLayoutPreferences.load(harness.prefs))
            assertSame(clockPager, harness.clockPager)
            assertSame(widgetPager, harness.widgetPager)
            assertEquals(1, harness.widgetPager.currentPage)
            assertEquals(0, harness.clockPager.currentPage)
            assertFalse(harness.widgetPager.isScrollInProgress)
            assertEquals(1, harness.clockEntries)
            assertEquals(1, harness.widgetEntries)
            assertEquals(0, harness.disposals)
            assertTrue(harness.renderedInteractions > 0)
        }
    }

    private fun releaseAt(harness: Harness, raw: Float, expected: Float, edge: Float = 0f) {
        val before = compose.runOnIdle { harness.state.ratio }
        val chosenSide = compose.runOnIdle { harness.state.clockSide }
        val count = compose.runOnIdle { harness.finished.size }
        val bounds = root().fetchSemanticsNode().boundsInRoot
        val hit = divider().fetchSemanticsNode().boundsInRoot
        val start = hit.center - bounds.topLeft + Offset(edge * (hit.width / 2f - 1f), 0f)
        val direction = if (raw < before) -1f else 1f
        root().performTouchInput {
            down(start)
            moveTo(start + Offset((raw - before) * bounds.width + direction * harness.touchSlop, 0f))
        }
        assertSplit(harness, raw)
        compose.runOnIdle {
            assertEquals(chosenSide, harness.state.clockSide)
            assertEquals(count, harness.finished.size)
        }
        root().performTouchInput { up() }
        assertSplit(harness, expected)
        compose.runOnIdle {
            assertEquals(chosenSide, harness.state.clockSide)
            assertEquals(count + 1, harness.finished.size)
            assertEquals(harness.state, harness.finished.last())
            assertEquals(harness.state, SplitLayoutPreferences.load(harness.prefs))
            assertEquals(chosenSide, SplitLayoutPreferences.load(harness.prefs).clockSide)
        }
    }
    private fun showParent(): Harness {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("split-parent-regression", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val harness = Harness(prefs)
        compose.setContent {
            MaterialTheme {
                val clockPager = rememberPagerState { 3 }
                val widgetPager = rememberPagerState { 3 }
                val interactions = harness.interactions
                val touchSlop = LocalViewConfiguration.current.touchSlop
                SideEffect {
                    harness.clockPager = clockPager
                    harness.widgetPager = widgetPager
                    harness.touchSlop = touchSlop
                    harness.renderedInteractions = interactions
                }
                Box(Modifier.fillMaxSize().testTag(PARENT).pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.pressed || it.previousPressed != it.pressed }) {
                                harness.interactions++
                            }
                        }
                    }
                }) {
                    when (harness.mode) {
                        Mode.SINGLE -> Box(Modifier.fillMaxSize())
                        Mode.DUAL_LEFT, Mode.DUAL_RIGHT -> ResizableDualLayout(
                            state = harness.state,
                            onStateChange = { harness.state = it; harness.updates += it },
                            onDragFinished = {
                                SplitLayoutPreferences.save(prefs, harness.state)
                                harness.mode = if (harness.state.clockSide == ClockSide.LEFT) {
                                    Mode.DUAL_LEFT
                                } else {
                                    Mode.DUAL_RIGHT
                                }
                                harness.finished += SplitLayoutPreferences.load(prefs)
                            },
                            isUiLocked = harness.locked,
                            clock = {
                                DisposableEffect(Unit) {
                                    harness.clockEntries++
                                    onDispose { harness.disposals++ }
                                }
                                Box(Modifier.fillMaxSize().testTag(CLOCK)) {
                                    VerticalPager(clockPager, Modifier.fillMaxSize()) { Text("Clock $it") }
                                }
                            },
                            widgets = {
                                DisposableEffect(Unit) {
                                    harness.widgetEntries++
                                    onDispose { harness.disposals++ }
                                }
                                Box(Modifier.fillMaxSize().testTag(WIDGETS)) {
                                    VerticalPager(widgetPager, Modifier.fillMaxSize()) { Text("Widget $it") }
                                }
                            },
                            modifier = Modifier.fillMaxSize().testTag(ROOT)
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        return harness
    }

    private fun assertSplit(harness: Harness, ratio: Float) {
        compose.runOnIdle { assertEquals(ratio, harness.state.ratio, 0.0001f) }
        val rootBounds = root().fetchSemanticsNode().boundsInRoot
        val clockBounds = compose.onNodeWithTag(CLOCK).fetchSemanticsNode().boundsInRoot
        val widgetBounds = compose.onNodeWithTag(WIDGETS).fetchSemanticsNode().boundsInRoot
        val splitX = rootBounds.left + rootBounds.width * ratio
        val clockOnLeft = compose.runOnIdle { harness.state.clockSide == ClockSide.LEFT }
        assertEquals(splitX, if (clockOnLeft) clockBounds.right else clockBounds.left, 1f)
        assertEquals(splitX, if (clockOnLeft) widgetBounds.left else widgetBounds.right, 1f)
        assertEquals(splitX, divider().fetchSemanticsNode().boundsInRoot.center.x, 1f)
    }

    private fun root() = compose.onNodeWithTag(ROOT)
    private fun divider() = compose.onNode(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
        useUnmergedTree = true
    )

    private enum class Mode { SINGLE, DUAL_LEFT, DUAL_RIGHT }

    private class Harness(val prefs: SharedPreferences) {
        var state by mutableStateOf(SplitLayoutState(0.7f))
        var locked by mutableStateOf(false)
        var mode by mutableStateOf(Mode.DUAL_LEFT)
        var interactions by mutableIntStateOf(0)
        var renderedInteractions = 0
        var clockEntries = 0
        var widgetEntries = 0
        var disposals = 0
        var touchSlop = 0f
        lateinit var clockPager: PagerState
        lateinit var widgetPager: PagerState
        val updates = mutableListOf<SplitLayoutState>()
        val finished = mutableListOf<SplitLayoutState>()

        fun selectClockSide(side: ClockSide) {
            mode = if (side == ClockSide.LEFT) Mode.DUAL_LEFT else Mode.DUAL_RIGHT
            if (state.clockSide != side) {
                state = state.withClockSide(side)
                SplitLayoutPreferences.save(prefs, state)
            }
        }
    }

    private companion object {
        const val PARENT = "parent-split-observer"
        const val ROOT = "parent-split-root"
        const val CLOCK = "parent-clock"
        const val WIDGETS = "parent-widgets"
    }
}
