package com.linzin.linzindeskclock.media

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linzin.linzindeskclock.ui.MediaSeekBar
import com.linzin.linzindeskclock.ui.MediaWidgetContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/** Standalone regression coverage; the outer guard runs before any Activity can be launched. */
@RunWith(AndroidJUnit4::class)
class MediaPlayerGeometryTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: TestRule = RuleChain.outerRule(DedicatedMediaEmulatorRule()).around(compose)

    @Test fun normalHeightsFitEveryControlAndWideLayoutsKeepABottomPanel() {
        val dimensions = mutableStateOf(320 to 300)
        val session = fixture()
        show {
            val (width, height) = dimensions.value
            MediaWidgetContent(MediaWidgetState(MediaAccess.READY, session), isLocked = false,
                onOpenNotificationAccess = {}, onCommand = { _, _ -> }, modifier = Modifier.size(width.dp, height.dp))
        }
        for ((width, height) in listOf(320 to 300, 400 to 320, 320 to 350, 320 to 360, 320 to 400, 568 to 320, 360 to 220)) {
            compose.runOnIdle { dimensions.value = width to height }
            val widget = bounds("media_widget")
            val artwork = bounds("media_artwork")
            val panel = bounds("media_glass_panel")
            assertEquals("The requested viewport must fit the emulator", height.toFloat(), widget.height, 1f)
            assertTrue("Panel height must stay 30–40% at ${width}x$height", panel.height / widget.height in 0.30f..0.40f)
            assertEquals(artwork.bottom, panel.top, 1f)
            assertEquals(widget.left, panel.left, 1f)
            assertEquals(widget.right, panel.right, 1f)
            assertEquals(widget.bottom, panel.bottom, 1f)
            if (height >= 300) {
                for (tag in listOf("media_title", "media_artist", "media_seek", "media_previous", "media_play_pause", "media_next")) {
                    compose.onNodeWithTag(tag).assertIsDisplayed()
                    val control = bounds(tag)
                    assertTrue("$tag must fit without scrolling at ${width}x$height", control.top >= panel.top && control.bottom <= panel.bottom + 1f)
                }
                for (tag in listOf("media_previous", "media_play_pause", "media_next")) {
                    val control = bounds(tag)
                    assertTrue("$tag keeps its full 48 dp hit target", control.width >= 47.5f && control.height >= 47.5f)
                }
                assertTrue("Seek keeps its full 32 dp hit target", bounds("media_seek").height >= 31.5f)
            }
        }
    }

    @Test fun internalPanelCornersContainArtworkInsteadOfDarkCutouts() {
        val session = fixture()
        show {
            MediaWidgetContent(MediaWidgetState(MediaAccess.READY, session), isLocked = false,
                onOpenNotificationAccess = {}, onCommand = { _, _ -> }, modifier = Modifier.size(320.dp, 400.dp))
        }
        val pixels = compose.onNodeWithTag("media_glass_panel").captureToImage().toPixelMap()
        // These are inside the cover/panel seam, well away from the sole outer rounded clip.
        for (x in listOf(2, 5, pixels.width - 6, pixels.width - 3)) {
            for (y in 2..5) {
                val colour = pixels[x, y]
                assertTrue("The top inner corner ($x,$y) must retain the blue artwork glass: $colour",
                    colour.blue > 0.40f && colour.green > 0.25f)
            }
        }
    }

    @Test fun paintedThumbAndTrackCentresAgreeAcrossFractionsAndHitHeights() {
        val fraction = mutableStateOf(0.25f)
        val height = mutableStateOf(32)
        show {
            MediaSeekBar(fraction.value, enabled = true, description = "Seek", onSeek = {},
                modifier = Modifier.size(240.dp, height.value.dp))
        }
        for (hitHeight in listOf(32, 40)) {
            for (value in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                compose.runOnIdle { fraction.value = value; height.value = hitHeight }
                val pixels = compose.onNodeWithTag("media_seek").captureToImage().toPixelMap()
                val expectedX = 4f + (pixels.width - 8f) * value
                val thumbColumn = expectedX.toInt().coerceIn(0, pixels.width - 1)
                val thumb = paintedRows(pixels, thumbColumn, 0.70f)
                assertTrue("The 8 dp thumb must be visibly taller than the 4 dp track", thumb.size >= 7)
                assertEquals(hitHeight / 2f, (thumb.first() + thumb.last() + 1) / 2f, 0.5f)
                val trackColumn = if (value < 0.5f) pixels.width - 16 else 16
                val track = paintedRows(pixels, trackColumn, 0.12f)
                assertTrue("The track stays 4 dp thick", track.size in 4..5)
                assertEquals("Thumb and track share one vertical centre", (thumb.first() + thumb.last() + 1) / 2f,
                    (track.first() + track.last() + 1) / 2f, 0.5f)
                val thumbPixels = (0 until pixels.width).filter { pixels[it, hitHeight / 2 - 3].red > 0.70f }
                assertTrue("The thumb is actually painted", thumbPixels.isNotEmpty())
                assertEquals("Painted x uses the same inset track endpoints as gestures", expectedX,
                    (thumbPixels.first() + thumbPixels.last() + 1) / 2f, 1f)
            }
        }
    }

    @Test fun tapsDragAndSetProgressDispatchRealSeekFractions() {
        val value = mutableStateOf(0f)
        val committed = mutableListOf<Float>()
        show {
            MediaSeekBar(value.value, enabled = true, description = "Seek",
                onSeek = { committed += it; value.value = it }, modifier = Modifier.size(240.dp, 32.dp))
        }
        val seek = compose.onNodeWithTag("media_seek")
        // 62 px is 25% of the painted 4..236 track, not 25% of the full hit box.
        seek.performTouchInput { click(Offset(62f, 16f)) }
        compose.runOnIdle { assertEquals(0.25f, committed.single(), 0.001f) }
        seek.performTouchInput { swipe(Offset(62f, 16f), Offset(178f, 16f), durationMillis = 320) }
        compose.runOnIdle {
            assertEquals("A drag commits once on release", 2, committed.size)
            assertEquals(0.75f, committed.last(), 0.001f)
        }
        seek.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(0.5f)) }
        compose.runOnIdle { assertEquals(0.5f, committed.last(), 0f) }
        seek.performSemanticsAction(SemanticsActions.SetProgress) { assertFalse(it(Float.NaN)) }
        seek.performTouchInput { click(Offset(0f, 16f)) }
        compose.runOnIdle { assertEquals(0f, committed.last(), 0f) }
        seek.performTouchInput { click(Offset(239f, 16f)) }
        compose.runOnIdle { assertEquals(1f, committed.last(), 0f) }
    }

    @Test fun uiLockCancelsAnActiveDragAndRemovesTheProgressAction() {
        val enabled = mutableStateOf(true)
        val committed = mutableListOf<Float>()
        val preview = mutableStateOf<Float?>(null)
        show {
            MediaSeekBar(0.25f, enabled.value, description = "Seek", onSeek = { committed += it },
                onSeeking = { preview.value = it }, modifier = Modifier.size(240.dp, 32.dp))
        }
        val seek = compose.onNodeWithTag("media_seek")
        seek.performTouchInput { down(Offset(62f, 16f)); moveTo(Offset(178f, 16f), delayMillis = 32) }
        compose.runOnIdle {
            assertTrue("The pointer really started a seek gesture", preview.value != null)
            enabled.value = false
        }
        seek.assertIsNotEnabled()
        seek.performTouchInput { up() }
        seek.performTouchInput { click(Offset(180f, 16f)) }
        assertFalse(seek.fetchSemanticsNode().config.contains(SemanticsActions.SetProgress))
        compose.runOnIdle {
            assertTrue("Locking and releasing must not seek", committed.isEmpty())
            assertEquals(null, preview.value)
            enabled.value = true
        }
        seek.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(0.5f)) }
        compose.runOnIdle { assertEquals(listOf(0.5f), committed) }
    }

    @Test fun metadataReplacementCancelsSeekAgainstTheOldDuration() {
        val session = mutableStateOf(fixture())
        val committed = mutableListOf<Long?>()
        show {
            MediaWidgetContent(MediaWidgetState(MediaAccess.READY, session.value), isLocked = true,
                controlsEnabled = true, onOpenNotificationAccess = {},
                onCommand = { command, position -> if (command == MediaCommand.SEEK) committed += position },
                modifier = Modifier.size(320.dp, 400.dp))
        }
        val seek = compose.onNodeWithTag("media_seek")
        seek.performTouchInput { down(Offset(width * 0.2f, center.y)); moveTo(Offset(width * 0.8f, center.y), delayMillis = 32) }
        compose.runOnIdle { session.value = session.value.copy(metadataRevision = 8L, durationMs = 200_000L) }
        seek.performTouchInput { up() }
        compose.runOnIdle { assertTrue("Replacing a track must cancel its pending seek", committed.isEmpty()) }
        seek.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(0.5f)) }
        compose.runOnIdle { assertEquals(listOf(100_000L), committed) }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                MaterialTheme {
                    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { content() }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun fixture() = MediaSessionSnapshot(
        sessionId = 41L, metadataRevision = 7L, packageName = "com.linzin.geometry.fixture",
        title = "唯有追趕風的方向", artist = "知更鳥 / HOYO-MiX / Chevy", album = "Geometry",
        durationMs = 123_000L, playback = PlaybackClock(positionMs = 30_000L, phase = PlaybackPhase.PAUSED),
        actions = SupportedActions(play = true, pause = true, previous = true, next = true, seek = true),
        artwork = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF4080C0.toInt()) },
    )

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun paintedRows(pixels: PixelMap, x: Int, minimumRed: Float) =
        (0 until pixels.height).filter { pixels[x, it].red > minimumRed }
}
