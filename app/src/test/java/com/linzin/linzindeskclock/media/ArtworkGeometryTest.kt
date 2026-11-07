package com.linzin.linzindeskclock.media

import org.junit.Assert.*
import org.junit.Test

class ArtworkGeometryTest {
    @Test fun portraitCoverStaysWholeWithOnlySideExtensions() {
        val layout = fitArtwork(100, 200, 400, 300)!!
        assertEquals(ArtworkRect(125f, 0f, 275f, 300f), layout.artwork)
        assertEquals(listOf(ArtworkRect(0f, 0f, 125f, 300f), ArtworkRect(275f, 0f, 400f, 300f)), layout.extensions)
        assertEquals(1.5f, layout.scale, 0.0001f)
    }

    @Test fun landscapeCoverStaysWholeWithOnlyTopAndBottomExtensions() {
        val layout = fitArtwork(400, 200, 300, 400)!!
        assertEquals(ArtworkRect(0f, 125f, 300f, 275f), layout.artwork)
        assertEquals(listOf(ArtworkRect(0f, 0f, 300f, 125f), ArtworkRect(0f, 275f, 300f, 400f)), layout.extensions)
    }

    @Test fun matchingAspectHasNoBlurredRectangles() {
        assertTrue(fitArtwork(120, 80, 600, 400)!!.extensions.isEmpty())
        assertNull(fitArtwork(0, 80, 600, 400))
        assertNull(fitArtwork(120, 80, -1, 400))
    }

    @Test fun extensionsNeverOverlapClearCoverAndFillTheContainer() {
        for ((width, height) in listOf(1 to 900, 997 to 11, 317 to 317, 213 to 451)) {
            val layout = fitArtwork(width, height, 731, 389)!!
            val area = layout.artwork.width * layout.artwork.height + layout.extensions.sumOf { (it.width * it.height).toDouble() }
            assertEquals(284_359.0, area, 0.1)
            assertEquals(width.toFloat() / height, layout.artwork.width / layout.artwork.height, 0.01f)
            for (rect in layout.extensions) {
                assertTrue(rect.right <= layout.artwork.left || rect.left >= layout.artwork.right || rect.bottom <= layout.artwork.top || rect.top >= layout.artwork.bottom)
            }
        }
    }

    @Test fun downsampleCapsMemoryWithoutUpscalingOrSquashing() {
        assertEquals(ArtworkSize(1024, 512), downsampleSize(4000, 2000, 1024))
        assertEquals(ArtworkSize(64, 128), downsampleSize(64, 128, 1024))
        assertEquals(ArtworkSize(1, 1024), downsampleSize(1, 20_000, 1024))
        assertNull(downsampleSize(0, 100, 1024))
        assertNull(downsampleSize(100, 100, 0))
    }
}
