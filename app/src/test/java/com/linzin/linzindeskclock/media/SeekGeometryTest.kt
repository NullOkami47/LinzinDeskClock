package com.linzin.linzindeskclock.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekGeometryTest {
    @Test fun thumbAndTrackUseTheSameCentreAndInsetEndpoints() {
        val geometry = SeekGeometry(width = 200f, height = 32f, thumbRadius = 4f)
        assertEquals(16f, geometry.centreY, 0f)
        assertEquals(4f, geometry.startX, 0f)
        assertEquals(196f, geometry.endX, 0f)
        assertEquals(52f, geometry.thumbX(0.25f), 0.001f)
        assertEquals(148f, geometry.thumbX(0.75f), 0.001f)
    }

    @Test fun tapsAndPaintAgreeAtEveryFractionAcrossWidthsAndDirections() {
        for (width in listOf(48f, 79f, 200f, 601f)) {
            for (reversed in listOf(false, true)) {
                val geometry = SeekGeometry(width, 40f, 4f)
                for (fraction in listOf(0f, 0.01f, 0.25f, 0.5f, 0.75f, 0.99f, 1f)) {
                    assertEquals(fraction, geometry.fractionAt(geometry.thumbX(fraction, reversed), reversed), 0.00001f)
                }
            }
        }
    }

    @Test fun endpointTapsClampToRealTrackExtentsIncludingRtl() {
        val geometry = SeekGeometry(200f, 32f, 4f)
        assertEquals(0f, geometry.fractionAt(-100f), 0f)
        assertEquals(0f, geometry.fractionAt(4f), 0f)
        assertEquals(1f, geometry.fractionAt(196f), 0f)
        assertEquals(1f, geometry.fractionAt(300f), 0f)
        assertEquals(1f, geometry.fractionAt(0f, reversed = true), 0f)
        assertEquals(0f, geometry.fractionAt(200f, reversed = true), 0f)
    }

    @Test fun tinyAndZeroWidthTracksStayFiniteAndInsideTheirCanvas() {
        for (width in listOf(0f, 1f, 4f, 8f)) {
            val geometry = SeekGeometry(width, 32f, 4f)
            assertTrue(geometry.thumbX(0.5f).isFinite())
            assertTrue(geometry.thumbX(0.5f) in 0f..width)
            assertTrue(geometry.fractionAt(10f).isFinite())
            assertTrue(geometry.thumbRadius <= width / 2f)
        }
    }
}
