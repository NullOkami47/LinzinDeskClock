package com.linzin.linzindeskclock.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitLayoutGeometryTest {
    @Test
    fun `all visible boundaries snap even when percentages fall between pixels`() {
        for (width in listOf(319, 1001, 1281)) {
            for ((ratio, target) in listOf(
                0.25f to 0.3f, 0.35f to 0.3f, 0.45f to 0.5f,
                0.55f to 0.5f, 0.65f to 0.7f, 0.75f to 0.7f
            )) {
                for (side in ClockSide.entries) {
                    val released = SplitLayoutState(ratio, side).releaseAtWidth(width)
                    assertEquals("ratio=$ratio width=$width", target, released.ratio, 0f)
                    assertEquals(side, released.clockSide)
                }
            }
        }
    }
    @Test
    fun `pointer release classifies every visible band boundary at pixel precision`() {
        for ((lower, upper, target) in listOf(
            Triple(0.25f, 0.35f, 0.3f), Triple(0.45f, 0.55f, 0.5f),
            Triple(0.65f, 0.75f, 0.7f)
        )) {
            for (ratio in listOf(java.lang.Math.nextDown(lower), java.lang.Math.nextUp(upper))) {
                val state = SplitLayoutState(ratio)
                assertEquals(target, state.releaseAtWidth(1000).ratio, 0f)
                assertEquals(state.clockSide, state.releaseAtWidth(1000).clockSide)
                assertEquals(ratio, state.ratio, 0f)
            }
        }
    }

    @Test
    fun `pointer release one rendered pixel outside a band does not snap or quantise`() {
        for (ratio in listOf(0.249f, 0.351f, 0.449f, 0.551f, 0.649f, 0.751f, 0.40037f, 0.60037f)) {
            val state = SplitLayoutState(ratio)
            org.junit.Assert.assertSame(state, state.releaseAtWidth(1000))
        }
    }

    @Test
    fun `pixel based centre release retains the side before rounding`() {
        for (side in ClockSide.entries) {
            assertEquals(side, SplitLayoutState(0.49999f, side).releaseAtWidth(1000).clockSide)
            assertEquals(side, SplitLayoutState(0.50001f, side).releaseAtWidth(1000).clockSide)
            for ((ratio, width, target) in listOf(Triple(0.31f, 0, 0.3f), Triple(0.69f, -1, 0.7f))) {
                val released = SplitLayoutState(ratio, side).releaseAtWidth(width)
                assertEquals(target, released.ratio, 0f)
                assertEquals(side, released.clockSide)
            }
        }
    }
    @Test
    fun `hit zone overlays the exact split and does not consume panel width`() {
        val actual = SplitLayoutState(0.7f).geometry(1000, 48)
        assertEquals(SplitLayoutGeometry(700, 0, 700, 700, 300, 676, 48), actual)
    }

    @Test
    fun `an explicitly right clock places widgets on the left`() {
        val actual = SplitLayoutState(0.3f, ClockSide.RIGHT).geometry(1000, 48)
        assertEquals(SplitLayoutGeometry(300, 300, 700, 0, 300, 276, 48), actual)
    }

    @Test
    fun `odd widths have no gap and a centred right clock stays right`() {
        val actual = SplitLayoutState(0.5f, ClockSide.RIGHT).geometry(1001, 48)
        assertEquals(SplitLayoutGeometry(501, 501, 500, 0, 501, 477, 48), actual)
    }

    @Test
    fun `physical widths remain continuous inside the release snap region`() {
        val actual = SplitLayoutState(0.53f).geometry(1000, 48)
        assertEquals(SplitLayoutGeometry(530, 0, 530, 530, 470, 506, 48), actual)
    }

    @Test
    fun `small windows clamp only the hit zone and never shift the actual split`() {
        val actual = SplitLayoutState(0.2f).geometry(80, 48)
        assertEquals(SplitLayoutGeometry(16, 0, 16, 16, 64, 0, 48), actual)
        val mirrored = SplitLayoutState(0.8f).geometry(80, 48)
        assertEquals(SplitLayoutGeometry(64, 0, 64, 64, 16, 32, 48), mirrored)
    }

    @Test
    fun `a container narrower than the hit target is fully covered without negative sizes`() {
        val actual = SplitLayoutState(0.2f).geometry(32, 48)
        assertEquals(SplitLayoutGeometry(6, 0, 6, 6, 26, 0, 32), actual)
        assertEquals(SplitLayoutGeometry(0, 0, 0, 0, 0, 0, 0), SplitLayoutState().geometry(0, 48))
    }

    @Test
    fun `a smaller clock panel stays on its chosen physical side`() {
        assertEquals(SplitLayoutGeometry(300, 0, 300, 300, 701, 276, 48),
            SplitLayoutState(0.3f, ClockSide.LEFT).geometry(1001, 48))
        assertEquals(SplitLayoutGeometry(701, 701, 300, 0, 701, 677, 48),
            SplitLayoutState(0.7f, ClockSide.RIGHT).geometry(1001, 48))
    }

    @Test
    fun `explicit side changes swap content without moving the divider or hit zone`() {
        for (ratio in listOf(0.3f, 0.5f, 0.7f)) {
            val state = SplitLayoutState(ratio, ClockSide.LEFT)
            val left = state.geometry(1000, 48)
            val right = state.withClockSide(ClockSide.RIGHT).geometry(1000, 48)
            assertEquals(left.splitPx, right.splitPx)
            assertEquals(left.hitLeftPx, right.hitLeftPx)
            assertEquals(left.hitWidthPx, right.hitWidthPx)
            assertEquals(left.widgetsLeftPx, right.clockLeftPx)
            assertEquals(left.widgetsWidthPx, right.clockWidthPx)
            assertEquals(left.clockLeftPx, right.widgetsLeftPx)
            assertEquals(left.clockWidthPx, right.widgetsWidthPx)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative container sizes are rejected`() {
        SplitLayoutState().geometry(-1, 48)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative touch target sizes are rejected`() {
        SplitLayoutState().geometry(1000, -1)
    }
}
