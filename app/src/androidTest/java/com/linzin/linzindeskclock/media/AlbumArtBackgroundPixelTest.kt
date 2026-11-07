package com.linzin.linzindeskclock.media

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.Closeable
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/** PixelCopy reads the real hardware window, not Compose's software bitmap fallback. */
@RunWith(AndroidJUnit4::class)
class AlbumArtBackgroundPixelTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: TestRule = RuleChain.outerRule(DedicatedMediaEmulatorRule()).around(compose)
    private val outerBounds = AtomicReference<Rect?>()
    private val artworkBounds = AtomicReference<Rect?>()
    private val hardwareCanvas = AtomicBoolean(false)

    @Test fun portraitCoverIsSharpAndSideExtensionsAreMirroredBlurredAndClipped() {
        show(mutableStateOf(pattern(transpose = false)), 320, 240)
        capture("portrait-fit-mirror-blur").use { frame ->
            assertPattern(frame, transpose = false)
            assertGuard(frame)
        }
    }

    @Test fun landscapeCoverIsSharpAndTopBottomExtensionsAreMirroredBlurredAndClipped() {
        show(mutableStateOf(pattern(transpose = true)), 240, 320)
        capture("landscape-fit-mirror-blur").use { frame ->
            assertPattern(frame, transpose = true)
            assertGuard(frame)
        }
    }

    @Test fun hardwareCrossfadeShowsBothCoversThenNullRemovesOldPixels() {
        val cover = mutableStateOf<Bitmap?>(solid(AndroidColor.RED))
        show(cover, 320, 240)
        capture("crossfade-before").use { assertColour(AndroidColor.RED, it.panelPixel(0.5f, 0.5f), "initial cover") }
        compose.runOnIdle { cover.value = solid(AndroidColor.BLUE) }
        // Drive Compose's animation clock explicitly; the render-thread wait is a frame fence.
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(96)
        compose.waitForIdle()
        capture("crossfade-middle").use {
            val pixel = it.panelPixel(0.5f, 0.5f)
            assertTrue("Outgoing red is visible mid-crossfade", AndroidColor.red(pixel) > 10)
            assertTrue("Incoming blue is visible mid-crossfade", AndroidColor.blue(pixel) > 10)
            assertTrue("No unrelated colour enters the crossfade", AndroidColor.green(pixel) < 15)
        }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        capture("crossfade-after").use {
            assertColour(AndroidColor.BLUE, it.panelPixel(0.5f, 0.5f), "replacement cover")
            assertColour(AndroidColor.BLUE, it.panelPixel(0.05f, 0.5f), "replacement margin")
        }
        compose.runOnIdle { cover.value = null }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        capture("missing-artwork-cleared").use {
            for (x in listOf(0.05f, 0.5f, 0.95f)) {
                assertColour(AndroidColor.BLACK, it.panelPixel(x, 0.5f), "removed artwork at $x")
            }
            assertGuard(it)
        }
    }

    private fun show(cover: MutableState<Bitmap?>, widthDp: Int, heightDp: Int) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.size((widthDp + 16).dp, (heightDp + 16).dp)
                    .background(Color.Magenta)
                    .onGloballyPositioned { outerBounds.set(it.boundsInWindow().pixelRect()) }
                    .drawWithContent {
                        hardwareCanvas.set(drawContext.canvas.nativeCanvas.isHardwareAccelerated)
                        drawContent()
                    }, contentAlignment = Alignment.Center) {
                    AlbumArtBackground(cover.value, Modifier.size(widthDp.dp, heightDp.dp)
                        .onGloballyPositioned { artworkBounds.set(it.boundsInWindow().pixelRect()) })
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.waitUntil(MEDIA_WAIT_MS) { outerBounds.get() != null && artworkBounds.get() != null }
        assertTrue("Dedicated emulator requires a hardware-accelerated Compose canvas", hardwareCanvas.get())
        val outer = checkNotNull(outerBounds.get())
        val panel = checkNotNull(artworkBounds.get())
        assertTrue("Test panel must fit wholly in the emulator window", outer.contains(panel))
        assertEquals("Panel aspect ratio must not be constrained by the display", widthDp.toFloat() / heightDp,
            panel.width().toFloat() / panel.height(), 0.015f)
        assertTrue("Test requires an unclipped guard around all edges", panel.left > outer.left && panel.top > outer.top &&
            panel.right < outer.right && panel.bottom < outer.bottom)
    }

    private fun capture(name: String): PixelFrame {
        requireDedicatedMediaEmulator()
        commitHardwareFrame()
        val outer = Rect(checkNotNull(outerBounds.get()))
        val panel = Rect(checkNotNull(artworkBounds.get())).apply { offset(-outer.left, -outer.top) }
        assertTrue("Capture is bounded to 2048 pixels per edge", outer.width() in 1..2048 && outer.height() in 1..2048)
        val bitmap = Bitmap.createBitmap(outer.width(), outer.height(), Bitmap.Config.ARGB_8888)
        val finished = CountDownLatch(1)
        val status = AtomicInteger(-1)
        mediaOnMain {
            val window = compose.activity.window
            assertTrue("Window hardware acceleration is required", window.decorView.isHardwareAccelerated)
            assertTrue("Capture must be inside the real window", outer.left >= 0 && outer.top >= 0 &&
                outer.right <= window.decorView.width && outer.bottom <= window.decorView.height)
            PixelCopy.request(window, outer, bitmap, { result -> status.set(result); finished.countDown() }, Handler(Looper.getMainLooper()))
        }
        assertTrue("PixelCopy did not complete within its deadline", finished.await(MEDIA_WAIT_MS, TimeUnit.MILLISECONDS))
        assertEquals("PixelCopy must succeed; software drawing is not a substitute", PixelCopy.SUCCESS, status.get())
        val frame = PixelFrame(bitmap, panel)
        try {
            savePng(name, bitmap)
            return frame
        } catch (failure: Throwable) {
            frame.close()
            throw failure
        }
    }

    private fun commitHardwareFrame() {
        val committed = CountDownLatch(1)
        val callback = Runnable { committed.countDown() }
        val decor = compose.activity.window.decorView
        mediaOnMain {
            assertTrue("Test host must be visible and focused", decor.hasWindowFocus())
            decor.viewTreeObserver.registerFrameCommitCallback(callback)
            decor.postInvalidateOnAnimation()
        }
        try {
            assertTrue("No committed hardware frame", committed.await(MEDIA_WAIT_MS, TimeUnit.MILLISECONDS))
        } finally {
            mediaOnMain { if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.unregisterFrameCommitCallback(callback) }
        }
    }

    private fun savePng(name: String, bitmap: Bitmap) {
        check(name.matches(Regex("[a-z-]+")))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(checkNotNull(context.externalCacheDir), "media-instrumentation")
        check(directory.isDirectory || directory.mkdirs())
        val output = File(directory, "$name.png")
        output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("media_artwork_capture", output.absolutePath)
        })
    }

    /**
     * Hand-derived expected positions: 120x240 artwork in a 320x240 panel occupies x=100..220.
     * Transposing both makes the second case exercise top/bottom extensions independently of
     * the production geometry helper. Broad colour tolerances allow GPU rounding, not cropping.
     */
    private fun assertPattern(frame: PixelFrame, transpose: Boolean) {
        fun pixel(x: Float, y: Float): Int = if (transpose) frame.panelPixel(y / 240f, x / 320f)
            else frame.panelPixel(x / 320f, y / 240f)
        assertColour(AndroidColor.GREEN, pixel(160f, 10f), "whole artwork includes its leading edge")
        assertColour(AndroidColor.YELLOW, pixel(160f, 230f), "whole artwork includes its trailing edge")
        val sharpRed = pixel(158f, 120f)
        val sharpBlue = pixel(162f, 120f)
        assertColour(AndroidColor.RED, sharpRed, "original remains sharp on red side")
        assertColour(AndroidColor.BLUE, sharpBlue, "original remains sharp on blue side")
        assertTrue("Mirror near first edge", AndroidColor.red(pixel(90f, 120f)) > AndroidColor.blue(pixel(90f, 120f)) + 75)
        assertTrue("Mirror repeats far first edge, not clamp/repeat", AndroidColor.blue(pixel(10f, 120f)) > AndroidColor.red(pixel(10f, 120f)) + 75)
        assertTrue("Mirror near opposite edge", AndroidColor.blue(pixel(230f, 120f)) > AndroidColor.red(pixel(230f, 120f)) + 75)
        assertTrue("Mirror repeats far opposite edge", AndroidColor.red(pixel(310f, 120f)) > AndroidColor.blue(pixel(310f, 120f)) + 75)
        val blurredBoundary = pixel(40f, 120f)
        assertTrue("Extension blends both source colours", AndroidColor.red(blurredBoundary) > 30 && AndroidColor.blue(blurredBoundary) > 30)
        val sharpContrast = colourDistance(sharpRed, sharpBlue)
        val extensionContrast = colourDistance(pixel(38f, 120f), pixel(42f, 120f))
        assertTrue("Only the extension has a softened edge", sharpContrast > 400 && extensionContrast < sharpContrast * 0.45)
    }

    private fun assertGuard(frame: PixelFrame) {
        val panel = frame.panel
        assertColour(AndroidColor.MAGENTA, frame.bitmap.getPixel(panel.left / 2, frame.bitmap.height / 2), "left clip boundary")
        assertColour(AndroidColor.MAGENTA, frame.bitmap.getPixel((panel.right + frame.bitmap.width) / 2, frame.bitmap.height / 2), "right clip boundary")
        assertColour(AndroidColor.MAGENTA, frame.bitmap.getPixel(frame.bitmap.width / 2, panel.top / 2), "top clip boundary")
        assertColour(AndroidColor.MAGENTA, frame.bitmap.getPixel(frame.bitmap.width / 2, (panel.bottom + frame.bitmap.height) / 2), "bottom clip boundary")
    }

    private fun pattern(transpose: Boolean): Bitmap {
        val width = if (transpose) 240 else 120
        val height = if (transpose) 120 else 240
        val pixels = IntArray(width * height) { index ->
            val x = if (transpose) index / width else index % width
            val y = if (transpose) index % width else index / width
            when {
                y < 24 -> AndroidColor.GREEN
                y >= 216 -> AndroidColor.YELLOW
                x < 60 -> AndroidColor.RED
                else -> AndroidColor.BLUE
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun solid(colour: Int): Bitmap = Bitmap.createBitmap(120, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(colour) }

    private fun assertColour(expected: Int, actual: Int, label: String) {
        assertTrue("$label: expected RGB $expected, actual $actual", abs(AndroidColor.red(expected) - AndroidColor.red(actual)) <= 24 &&
            abs(AndroidColor.green(expected) - AndroidColor.green(actual)) <= 24 && abs(AndroidColor.blue(expected) - AndroidColor.blue(actual)) <= 24)
    }

    private fun colourDistance(a: Int, b: Int): Int = abs(AndroidColor.red(a) - AndroidColor.red(b)) +
        abs(AndroidColor.green(a) - AndroidColor.green(b)) + abs(AndroidColor.blue(a) - AndroidColor.blue(b))
}

private fun androidx.compose.ui.geometry.Rect.pixelRect() = Rect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())

private class PixelFrame(val bitmap: Bitmap, val panel: Rect) : Closeable {
    fun panelPixel(x: Float, y: Float): Int = bitmap.getPixel(
        (panel.left + x * panel.width()).roundToInt().coerceIn(panel.left, panel.right - 1),
        (panel.top + y * panel.height()).roundToInt().coerceIn(panel.top, panel.bottom - 1),
    )
    override fun close() { bitmap.recycle() }
}
