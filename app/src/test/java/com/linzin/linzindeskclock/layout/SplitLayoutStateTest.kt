package com.linzin.linzindeskclock.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SplitLayoutStateTest {
    @Test
    fun `finite ratios clamp at both limits and choose the larger clock panel`() {
        assertState(0.2f, ClockSide.RIGHT, SplitLayoutState(-2f))
        assertState(0.8f, ClockSide.LEFT, SplitLayoutState(2f, ClockSide.RIGHT))
        assertState(0.2f, ClockSide.RIGHT, SplitLayoutState(0.2f))
        assertState(0.8f, ClockSide.LEFT, SplitLayoutState(0.8f))
    }

    @Test
    fun `non-finite initial ratios recover to the requested side's legacy ratio`() {
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertState(0.7f, ClockSide.LEFT, SplitLayoutState(invalid, ClockSide.LEFT))
            assertState(0.3f, ClockSide.RIGHT, SplitLayoutState(invalid, ClockSide.RIGHT))
        }
    }

    @Test
    fun `clock always follows the larger physical panel except at the centre`() {
        assertState(0.49f, ClockSide.RIGHT, SplitLayoutState(0.49f, ClockSide.LEFT))
        assertState(0.51f, ClockSide.LEFT, SplitLayoutState(0.51f, ClockSide.RIGHT))
        assertState(0.5f, ClockSide.LEFT, SplitLayoutState(0.5f, ClockSide.LEFT))
        assertState(0.5f, ClockSide.RIGHT, SplitLayoutState(0.5f, ClockSide.RIGHT))
    }

    @Test
    fun `continuous drag preserves every ratio in the snap region until release`() {
        var state = SplitLayoutState(0.6f)
        for (target in listOf(0.55f, 0.53f, 0.51f, 0.5f, 0.49f, 0.47f, 0.45f)) {
            state = state.withRatio(target)
            assertEquals(target, state.ratio, 0f)
        }
        assertState(0.5f, ClockSide.RIGHT, state.release())
    }

    @Test
    fun `pixel deltas accumulate continuously using the full container width`() {
        val initial = SplitLayoutState(0.7f)
        val first = initial.dragBy(-160f, 1000f)
        val second = first.dragBy(-20f, 1000f)
        assertState(0.54f, ClockSide.LEFT, first)
        assertState(0.52f, ClockSide.LEFT, second)
        assertState(0.5f, ClockSide.LEFT, second.release())
        assertState(0.7f, ClockSide.LEFT, initial)
    }

    @Test
    fun `drag clamps and immediately moves away from an endpoint on reversal`() {
        val clamped = SplitLayoutState(0.7f).dragBy(1000f, 1000f)
        assertState(0.8f, ClockSide.LEFT, clamped)
        assertState(0.79f, ClockSide.LEFT, clamped.dragBy(-10f, 1000f))
        assertState(0.2f, ClockSide.RIGHT, clamped.dragBy(-1000f, 1000f))
    }

    @Test
    fun `non-finite updates and unmeasured containers leave the current state intact`() {
        val state = SplitLayoutState(0.61f)
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertSame(state, state.withRatio(invalid))
            assertSame(state, state.dragBy(invalid, 1000f))
            assertSame(state, state.dragBy(50f, invalid))
        }
        assertSame(state, state.dragBy(50f, 0f))
        assertSame(state, state.dragBy(50f, -1f))
    }

    @Test
    fun `finite delta overflow still clamps at the correct endpoint`() {
        assertState(0.8f, ClockSide.LEFT, SplitLayoutState().dragBy(Float.MAX_VALUE, Float.MIN_VALUE))
        assertState(0.2f, ClockSide.RIGHT, SplitLayoutState().dragBy(-Float.MAX_VALUE, Float.MIN_VALUE))
    }

    @Test
    fun `release snaps inclusively at both exact boundaries`() {
        assertState(0.5f, ClockSide.RIGHT, SplitLayoutState(0.45f).release())
        assertState(0.5f, ClockSide.LEFT, SplitLayoutState(0.55f).release())
    }

    @Test
    fun `release does not snap values immediately outside the boundaries`() {
        val below = java.lang.Math.nextDown(0.45f)
        val above = java.lang.Math.nextUp(0.55f)
        assertState(below, ClockSide.RIGHT, SplitLayoutState(below).release())
        assertState(above, ClockSide.LEFT, SplitLayoutState(above).release())
    }

    @Test
    fun `arriving at centre retains the side from the most recent non-centre position`() {
        assertState(0.5f, ClockSide.LEFT, SplitLayoutState(0.6f).withRatio(0.5f))
        assertState(0.5f, ClockSide.RIGHT, SplitLayoutState(0.4f).withRatio(0.5f))
        val crossed = SplitLayoutState(0.6f).withRatio(0.49f).withRatio(0.5f)
        assertState(0.5f, ClockSide.RIGHT, crossed.release())
        val returned = crossed.withRatio(0.51f).withRatio(0.5f)
        assertState(0.5f, ClockSide.LEFT, returned.release())
    }

    @Test
    fun `repeated release retains a centred right clock`() {
        val state = SplitLayoutState(0.48f).release()
        assertState(0.5f, ClockSide.RIGHT, state.release().release())
    }

    @Test
    fun `legacy names migrate to the original seventy thirty layouts`() {
        assertState(0.7f, ClockSide.LEFT, SplitLayoutState.fromLegacyMode("DUAL_LEFT_LARGE"))
        assertState(0.3f, ClockSide.RIGHT, SplitLayoutState.fromLegacyMode("DUAL_RIGHT_LARGE"))
        for (mode in listOf(null, "SINGLE", "unknown")) {
            assertState(0.7f, ClockSide.LEFT, SplitLayoutState.fromLegacyMode(mode))
        }
    }

    @Test
    fun `normalised values have structural equality for hoisted Compose state`() {
        val first = SplitLayoutState(10f, ClockSide.RIGHT)
        val second = SplitLayoutState(0.8f, ClockSide.LEFT)
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    private fun assertState(ratio: Float, side: ClockSide, actual: SplitLayoutState) {
        assertEquals(ratio, actual.ratio, 0.000001f)
        assertEquals(side, actual.clockSide)
    }
}
