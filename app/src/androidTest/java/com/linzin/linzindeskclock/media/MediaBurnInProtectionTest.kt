package com.linzin.linzindeskclock.media

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Rect as AndroidRect
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.linzin.linzindeskclock.DeskClockColorScheme
import com.linzin.linzindeskclock.autostart.ClockEntryMode
import com.linzin.linzindeskclock.layout.SplitLayoutPreferences
import com.linzin.linzindeskclock.ui.DeskClockScreen
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

/** Real screen + platform MediaSession + PixelCopy: verifies the parent's effect reaches children. */
class MediaBurnInProtectionTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: TestRule = RuleChain.outerRule(DedicatedMediaEmulatorRule()).around(compose)
    private val captureBounds = AtomicReference<AndroidRect?>()
    private val screenshots = mutableListOf<Bitmap>()

    @Test fun idleMasksAllMediaElementsAlternatesPixelsAndTouchRestoresControls() = withScreen(true) { session ->
        compose.mainClock.autoAdvance = false
        val before = capture("before-idle")
        val regions = regions()
        advance(61_000)
        val first = capture("idle-phase-0")
        for ((name, area) in regions) assertMasked(before, first, area, name)

        // Metadata and playback updates must not count as user interaction or remove protection.
        mediaOnMain { session.publishPlayback(positionMs = 10_000L) }
        // Framework callbacks arrive on real time; allow a Compose frame for their snapshot update.
        compose.mainClock.autoAdvance = true
        compose.waitUntil(MEDIA_WAIT_MS) { compose.onAllNodesWithText("0:10").fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.autoAdvance = false
        advance(400)
        val updated = capture("progress-update-still-protected")
        assertMasked(before, updated, regions.getValue("media_play_pause"), "play button after progress update")

        advance(300_000)
        val second = capture("idle-phase-1")
        for ((name, area) in regions.filterKeys { it !in setOf("media_progress_track", "media_elapsed") }) {
            assertAlternated(before, first, second, area, name)
        }

        // Use a real touch, not a semantics click: this exercises the parent Initial pointer pass.
        compose.onNodeWithTag("media_next").performTouchInput { click() }
        advance(100)
        session.awaitEvent(1, FixtureTransport("next"))
        val restored = capture("after-touch")
        assertUnmasked(before, restored, regions.getValue("media_play_pause"), "play button after touch")
        advance(61_000)
        assertMasked(before, capture("idle-after-touch"), regions.getValue("media_play_pause"), "idle resumes")
    }

    @Test fun disabledPreferenceKeepsMediaUnmaskedAfterIdle() = withScreen(false) {
        compose.mainClock.autoAdvance = false
        val before = capture("disabled-before")
        val regions = regions()
        advance(61_000)
        val after = capture("disabled-after-idle")
        for ((name, area) in regions) assertUnmasked(before, after, area, name)
    }

    private fun withScreen(enabled: Boolean, block: (FrameworkMediaSessionFixture) -> Unit) {
        assumeTrue("RuntimeShader protection is available from API 33", Build.VERSION.SDK_INT >= 33)
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences("burn-in-media-test-$name", mode)
        }
        val visible = mutableStateOf(true)
        val prefs = context.getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE)
        assertTrue(prefs.edit().clear().putString("pref_layout_mode", "DUAL_LEFT_LARGE")
            .putFloat(SplitLayoutPreferences.RATIO_KEY, 0.5f).putString(SplitLayoutPreferences.CLOCK_SIDE_KEY, "LEFT")
            .putBoolean("pref_dismiss_gesture_tutorial", true).putBoolean("pref_dismiss_layout_tip", true)
            .putBoolean("pref_burn_in_enabled", enabled).putBoolean("pref_weather_enabled", false)
            .putBoolean("pref_show_calendar_events", false).putLong("pref_timeout_millis", -1L)
            .putString("low_battery_action", "OFF").putInt("pref_main_page", 1).putInt("pref_side_page", 2).commit())
        val cover = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888).apply { eraseColor(AndroidColor.rgb(120, 160, 200)) }
        NotificationListenerAccessLease().use { access ->
            access.setGranted(true)
            val session = mediaOnMain { FrameworkMediaSessionFixture(target, "Burn-in test song", PlaybackState.STATE_PAUSED,
                PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SEEK_TO, cover) }
            try {
                compose.setContent {
                    CompositionLocalProvider(LocalContext provides context, LocalDensity provides Density(1f)) {
                        MaterialTheme(colorScheme = DeskClockColorScheme) {
                            if (visible.value) DeskClockScreen(hasHingeSensor = false, entryMode = ClockEntryMode.LOCK_SCREEN,
                                windowOverride = compose.activity.window,
                                modifier = Modifier.testTag("burn-in-screen").onGloballyPositioned {
                                    val rect = it.boundsInWindow()
                                    captureBounds.set(AndroidRect(rect.left.roundToInt(), rect.top.roundToInt(), rect.right.roundToInt(), rect.bottom.roundToInt()))
                                })
                        }
                    }
                }
                compose.waitUntil(MEDIA_WAIT_MS) { compose.onAllNodesWithText("Burn-in test song").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("media_play_pause").assertExists()
                block(session)
            } finally {
                compose.runOnIdle { visible.value = false }
                compose.mainClock.advanceTimeByFrame()
                compose.mainClock.autoAdvance = true
                compose.waitForIdle()
                mediaOnMain { session.close() }
                screenshots.forEach { it.recycle() }
                screenshots.clear()
                cover.recycle()
                prefs.edit().clear().commit()
            }
        }
    }

    private fun advance(milliseconds: Long) {
        compose.mainClock.advanceTimeBy(milliseconds)
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun regions(): Map<String, Rect> {
        val root = compose.onNodeWithTag("burn-in-screen").fetchSemanticsNode().boundsInRoot
        return listOf("media_artwork", "media_glass_panel", "media_title", "media_artist", "media_elapsed",
            "media_progress_track", "media_previous", "media_play_pause", "media_next").associateWith { tag ->
            compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.translate(-root.left, -root.top)
        }
    }

    private fun brightPixels(before: Bitmap, area: Rect): List<Pair<Int, Int>> = buildList {
        for (y in (ceil(area.top).toInt() + 2).coerceAtLeast(0) until (floor(area.bottom).toInt() - 2).coerceAtMost(before.height)) {
            for (x in (ceil(area.left).toInt() + 2).coerceAtLeast(0) until (floor(area.right).toInt() - 2).coerceAtMost(before.width)) {
                if (brightness(before.getPixel(x, y)) >= 100) add(x to y)
            }
        }
    }
    private fun brightness(pixel: Int) = maxOf(AndroidColor.red(pixel), AndroidColor.green(pixel), AndroidColor.blue(pixel))
    private fun assertMasked(before: Bitmap, after: Bitmap, area: Rect, name: String) {
        val samples = brightPixels(before, area)
        assertTrue("$name must have real visible reference pixels", samples.size > 20)
        val black = samples.count { (x, y) -> brightness(after.getPixel(x, y)) < 8 }.toFloat() / samples.size
        assertTrue("$name should mask half the actual pixels, observed=$black", black in 0.43f..0.57f)
    }
    private fun assertUnmasked(before: Bitmap, after: Bitmap, area: Rect, name: String) {
        val samples = brightPixels(before, area)
        assertTrue("$name must have reference pixels", samples.size > 20)
        val black = samples.count { (x, y) -> brightness(after.getPixel(x, y)) < 8 }.toFloat() / samples.size
        assertTrue("$name should remain visible, black fraction=$black", black < 0.05f)
    }
    private fun assertAlternated(before: Bitmap, first: Bitmap, second: Bitmap, area: Rect, name: String) {
        val samples = brightPixels(before, area)
        val changed = samples.count { (x, y) -> (brightness(first.getPixel(x, y)) < 8) != (brightness(second.getPixel(x, y)) < 8) }.toFloat() / samples.size
        assertTrue("$name masked pixels must alternate after five minutes, changed=$changed", changed > 0.90f)
    }

    private fun capture(name: String): Bitmap {
        compose.waitForIdle()
        val decor = compose.activity.window.decorView
        compose.waitUntil(MEDIA_WAIT_MS) { mediaOnMain { decor.hasWindowFocus() } }
        val committed = CountDownLatch(1)
        val callback = Runnable { committed.countDown() }
        mediaOnMain {
            check(decor.isHardwareAccelerated)
            decor.viewTreeObserver.registerFrameCommitCallback(callback)
            decor.postInvalidateOnAnimation()
        }
        try { assertTrue(committed.await(MEDIA_WAIT_MS, TimeUnit.MILLISECONDS)) }
        finally { mediaOnMain { if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.unregisterFrameCommitCallback(callback) } }
        val rect = AndroidRect(checkNotNull(captureBounds.get()))
        val bitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
        screenshots += bitmap
        val finished = CountDownLatch(1)
        val result = AtomicInteger(-1)
        mediaOnMain { PixelCopy.request(compose.activity.window, rect, bitmap, { result.set(it); finished.countDown() }, Handler(Looper.getMainLooper())) }
        assertTrue(finished.await(MEDIA_WAIT_MS, TimeUnit.MILLISECONDS))
        assertEquals(PixelCopy.SUCCESS, result.get())
        val directory = File(checkNotNull(InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir), "burn-in-verification")
        check(directory.isDirectory || directory.mkdirs())
        val output = File(directory, "$name.png")
        output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("burn_in_capture", output.absolutePath)
            putString("burn_in_frame", "${bitmap.width}x${bitmap.height}; PixelCopy; actual DeskClockScreen; framework MediaSession; Compose virtual time")
        })
        return bitmap
    }
}
