package com.linzin.linzindeskclock.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyArtworkBlurTest {
    @Test fun blurSoftensAnEdgeWithoutChangingTheSource() {
        val source = intArrayOf(0xff000000.toInt(), 0xff000000.toInt(), 0xffffffff.toInt(), 0xffffffff.toInt())
        val original = source.copyOf()
        val blurred = blurArtworkPixels(source, 4, 1, 1)
        assertArrayEquals(original, source)
        assertEquals(0xff000000.toInt(), blurred.first())
        assertEquals(0xffffffff.toInt(), blurred.last())
        assertTrue((blurred[1] and 255) in 1..254)
        assertTrue((blurred[2] and 255) > (blurred[1] and 255))
    }

    @Test fun solidArtRetainsItsColourAtEveryEdge() {
        val source = IntArray(12) { 0xff8652a7.toInt() }
        assertArrayEquals(source, blurArtworkPixels(source, 3, 4, 3))
    }

    @Test fun verticalEdgesAreBlurredToo() {
        val blurred = blurArtworkPixels(intArrayOf(0xff000000.toInt(), 0xffffffff.toInt()), 1, 2, 1)
        assertEquals(0xff555555.toInt(), blurred[0])
        assertEquals(0xffaaaaaa.toInt(), blurred[1])
    }

    @Test fun singlePixelArtRemainsValidAtTheMaximumRadius() {
        assertArrayEquals(intArrayOf(0x80554433.toInt()), blurArtworkPixels(intArrayOf(0x80554433.toInt()), 1, 1, 12))
    }
}
