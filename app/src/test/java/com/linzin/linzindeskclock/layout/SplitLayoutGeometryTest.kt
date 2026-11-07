package com.linzin.linzindeskclock.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitLayoutGeometryTest {
    @Test
    fun `hit zone overlays the exact split and does not consume panel width`() {
        val actual = SplitLayoutState(0.7f).geometry(1000, 48)
        assertEquals(SplitLayoutGeometry(700, 0, 700, 700, 300, 676, 48), actual)
    }

    @Test
    fun `clock and widgets swap physical placement while preserving their slots`() {
        val actual = SplitLayoutState(0.3f).geometry(1000, 48)
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
        assertEquals(SplitLayoutGeometry(16, 16, 64, 0, 16, 0, 48), actual)
        val mirrored = SplitLayoutState(0.8f).geometry(80, 48)
        assertEquals(SplitLayoutGeometry(64, 0, 64, 64, 16, 32, 48), mirrored)
    }

    @Test
    fun `a container narrower than the hit target is fully covered without negative sizes`() {
        val actual = SplitLayoutState(0.2f).geometry(32, 48)
        assertEquals(SplitLayoutGeometry(6, 6, 26, 0, 6, 0, 32), actual)
        assertEquals(SplitLayoutGeometry(0, 0, 0, 0, 0, 0, 0), SplitLayoutState().geometry(0, 48))
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
