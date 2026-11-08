package com.linzin.linzindeskclock.media

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Rect as AndroidRect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.linzin.linzindeskclock.DeskClockColorScheme
import com.linzin.linzindeskclock.ui.MediaWidgetContent
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

/** Actual Compose layout and hardware pixels; this suite never starts a host on a physical phone. */
@RunWith(AndroidJUnit4::class)
class MediaWidgetPresentationTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: TestRule = RuleChain.outerRule(DedicatedMediaEmulatorRule()).around(compose)
    private val captureBounds = AtomicReference<AndroidRect?>()
    private val commands = mutableListOf<MediaCommand>()
    private val seekPositions = mutableListOf<Long?>()
    private var settingsClicks = 0
    private var pixelsPerDp = 1f

    @Test fun missingAccessShowsRealTraditionalChineseSettingsButton() {
        show(MediaWidgetState(MediaAccess.NOT_GRANTED))
        compose.onNodeWithText("前往通知存取權").assertIsDisplayed()
        val button = compose.onNodeWithTag("media_permission_button")
        button.performClick()
        compose.runOnIdle { assertEquals("A real action is wired to the button", 1, settingsClicks) }
        val bounds = bounds("media_permission_button")
        assertTrue("Permission target is at least 48 dp high", bounds.height >= 48f * pixelsPerDp - 1f)
        capture("permission-button") { bitmap ->
            assertTrue("The permission action is visibly painted", colourRange(bitmap, bounds) > 70)
        }
    }

    @Test fun permissionButtonOpensThisUsersExactListenerUsingThePresentationContext() {
        val intents = mutableListOf<Intent>()
        var buttonContext: Context? = null
        show(MediaWidgetState(MediaAccess.NOT_GRANTED), settingsAction = { context ->
            buttonContext = context
            val recordingContext = object : ContextWrapper(context) {
                override fun startActivity(intent: Intent) { intents += Intent(intent) }
            }
            assertTrue("The dedicated emulator must be unlocked for this setup action", MediaNotificationAccess.openSettings(recordingContext))
        })
        compose.onNodeWithTag("media_permission_button").performClick()
        compose.runOnIdle {
            assertNotNull(buttonContext)
            assertNotSame("Settings use the UI context, not its application context", buttonContext!!.applicationContext, buttonContext)
            assertEquals(1, settingsClicks)
            assertEquals(1, intents.size)
            val intent = intents.single()
            assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS, intent.action)
            assertEquals(ComponentName(compose.activity, ClockNotificationListenerService::class.java).flattenToString(),
                intent.getStringExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME))
            assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        }
        // startActivity is intercepted; the real settings application is never launched by this test.
        capture("permission-listener-intent")
    }

    @Test fun permissionButtonFallsBackForMissingOrDeniedOemDetailScreen() {
        val intents = mutableListOf<Intent>()
        var detailDenied = false
        show(MediaWidgetState(MediaAccess.NOT_GRANTED), settingsAction = { context ->
            val recordingContext = object : ContextWrapper(context) {
                override fun startActivity(intent: Intent) {
                    intents += Intent(intent)
                    if (intent.action == Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS) {
                        if (detailDenied) throw SecurityException("Fixture OEM detail screen denied")
                        else throw ActivityNotFoundException("Fixture OEM detail screen missing")
                    }
                }
            }
            assertTrue(MediaNotificationAccess.openSettings(recordingContext))
        })
        repeat(2) { attempt ->
            compose.runOnIdle { detailDenied = attempt == 1; intents.clear() }
            compose.onNodeWithTag("media_permission_button").performClick()
            compose.runOnIdle {
                assertEquals(listOf(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                    intents.map { it.action })
                assertTrue(intents.all { it.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0 })
            }
        }
        capture("permission-oem-fallback")
    }

    @Test fun keyguardReplacesPermissionActionWithUnlockHint() {
        val locked = mutableStateOf(false)
        show(MediaWidgetState(MediaAccess.NOT_GRANTED), locked = locked, hideWhenLocked = true)
        compose.runOnIdle { locked.value = true }
        compose.onNodeWithTag("media_permission_button").assertDoesNotExist()
        compose.onNodeWithText("請先解鎖裝置，再開啟通知存取權。").assertIsDisplayed()
        compose.runOnIdle { assertEquals("Locking must not launch settings", 0, settingsClicks) }
        capture("permission-locked-hint")
    }

    @Test fun uiLockAlsoBlocksPermissionNavigation() {
        show(MediaWidgetState(MediaAccess.NOT_GRANTED), controlsEnabled = false)
        compose.onNodeWithTag("media_permission_button").assertDoesNotExist()
        compose.onNodeWithText("請先解除介面鎖定，再開啟通知存取權。").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, settingsClicks) }
        capture("permission-ui-locked-hint")
    }

    @Test fun portraitHasWholeSharpCoverAboveColouredGlassAndCentredTransport() {
        show(fixture())
        val widget = bounds("media_widget")
        val art = bounds("media_artwork")
        val glass = bounds("media_glass_panel")
        assertTrue("Artwork owns about the upper 70%", art.height / widget.height in 0.65f..0.75f)
        assertEquals("The control panel does not obscure the fitted cover", art.bottom, glass.top, 1f)
        assertEquals(widget.bottom, glass.bottom, 1f)
        assertEquals(glass.center.x, bounds("media_title").center.x, 1f)
        assertEquals(glass.center.x, bounds("media_artist").center.x, 1f)
        assertCentredText("media_title")
        assertCentredText("media_artist")
        val previous = bounds("media_previous")
        val primary = bounds("media_play_pause")
        val next = bounds("media_next")
        assertTrue(previous.right <= primary.left && primary.right <= next.left)
        assertEquals("The main circular control stays centred", glass.center.x, primary.center.x, 1f)
        assertEquals(previous.center.y, primary.center.y, 1f)
        assertEquals(next.center.y, primary.center.y, 1f)
        assertTrue("The primary control is larger, without filling the panel", primary.width > previous.width && primary.width <= 64f * pixelsPerDp + 1f)
        val elapsed = bounds("media_elapsed")
        val seek = bounds("media_seek")
        val duration = bounds("media_duration")
        assertTrue(elapsed.right <= seek.left && seek.right <= duration.left)
        assertEquals(elapsed.center.y, duration.center.y, 1f)
        compose.onNodeWithText("唯有追趕風的方向").assertIsDisplayed()
        compose.onNodeWithText("知更鳥 / HOYO-MiX / Chevy").assertIsDisplayed()
        compose.onNodeWithText("0:30").assertIsDisplayed()
        compose.onNodeWithText("2:03").assertIsDisplayed()
        capture("portrait-artwork-glass") { bitmap ->
            assertWholeCover(bitmap, art)
            // The empty top rim sits above the title; only the background copy contributes here.
            val glassPixel = pixel(bitmap, glass.center.x, glass.top + 5f * pixelsPerDp)
            assertTrue("The actual control panel contains blurred artwork colour", AndroidColor.red(glassPixel) > 65 &&
                AndroidColor.blue(glassPixel) > 65 && AndroidColor.green(glassPixel) < 65)
        }
        compose.onNodeWithTag("media_previous").performClick()
        compose.onNodeWithTag("media_play_pause").performClick()
        compose.onNodeWithTag("media_next").performClick()
        compose.runOnIdle { assertEquals(listOf(MediaCommand.PREVIOUS, MediaCommand.PAUSE, MediaCommand.NEXT), commands) }
    }

    @Test fun seekActionUsesTheFixtureDurationAndIsNotJustAPaintedTrack() {
        show(fixture())
        compose.onNodeWithTag("media_seek").performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(0.5f)) }
        compose.runOnIdle {
            assertEquals(listOf(MediaCommand.SEEK), commands)
            assertEquals(listOf(61_500L), seekPositions)
        }
        capture("seek-action")
    }

    @Test fun twentyPercentPanelKeepsControlsReachableAtLargeFontScale() {
        show(fixture(), width = 80, height = 560, fontScale = 1.6f)
        val widget = bounds("media_widget")
        for (tag in listOf("media_previous", "media_play_pause", "media_next")) {
            compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            val button = bounds(tag)
            assertTrue("$tag fits inside a 20% portrait column", button.left >= widget.left && button.right <= widget.right)
            assertTrue("$tag preserves the 48 dp touch target", button.width >= 48f * pixelsPerDp - 1f && button.height >= 48f * pixelsPerDp - 1f)
            compose.onNodeWithTag(tag).performClick()
        }
        compose.runOnIdle { assertEquals(listOf(MediaCommand.PREVIOUS, MediaCommand.PAUSE, MediaCommand.NEXT), commands) }
        capture("narrow-large-text")
    }

    @Test fun shortWidePanelKeepsWholeCoverBesideGlass() {
        show(fixture(), width = 360, height = 220)
        val art = bounds("media_artwork")
        val glass = bounds("media_glass_panel")
        assertEquals("Landscape reserves an independent artwork area", art.right, glass.left, 1f)
        assertEquals(art.top, glass.top, 1f)
        assertEquals(art.bottom, glass.bottom, 1f)
        compose.onNodeWithTag("media_play_pause").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(MediaCommand.PAUSE), commands) }
        capture("landscape-artwork-glass") { bitmap -> assertWholeCover(bitmap, art) }
    }

    @Test fun privacyRemovesMetadataAndBothArtworkLayersWithoutOutgoingFade() {
        val locked = mutableStateOf(false)
        show(fixture(), locked = locked, hideWhenLocked = true)
        compose.runOnIdle { locked.value = true }
        compose.onNodeWithTag("media_artwork").assertDoesNotExist()
        compose.onNodeWithTag("media_glass_panel").assertDoesNotExist()
        compose.onNodeWithText("唯有追趕風的方向").assertDoesNotExist()
        compose.onNodeWithText("鎖定時隱藏媒體資訊").assertIsDisplayed()
        capture("privacy-hidden")
    }

    @Test fun uiLockedPlaybackRetainsArtButDisablesTransport() {
        show(fixture(), controlsEnabled = false)
        compose.onNodeWithTag("media_artwork").assertIsDisplayed()
        for (tag in listOf("media_previous", "media_play_pause", "media_next", "media_seek")) {
            compose.onNodeWithTag(tag).assertIsNotEnabled()
        }
        compose.onNodeWithTag("media_play_pause").performClick()
        compose.runOnIdle { assertTrue(commands.isEmpty()) }
        capture("playback-ui-locked")
    }

    @Test fun unsupportedNeighboursAreAbsentAndPlayRemainsCentred() {
        val state = fixture()
        show(state.copy(session = state.session!!.copy(actions = SupportedActions(pause = true))))
        compose.onNodeWithTag("media_previous").assertDoesNotExist()
        compose.onNodeWithTag("media_next").assertDoesNotExist()
        assertEquals(bounds("media_glass_panel").center.x, bounds("media_play_pause").center.x, 1f)
        compose.onNodeWithTag("media_play_pause").performClick()
        compose.runOnIdle { assertEquals(listOf(MediaCommand.PAUSE), commands) }
        capture("supported-primary-only")
    }

    private fun show(
        state: MediaWidgetState,
        width: Int = 320,
        height: Int = 560,
        locked: MutableState<Boolean> = mutableStateOf(false),
        hideWhenLocked: Boolean = false,
        controlsEnabled: Boolean = true,
        fontScale: Float = 1f,
        settingsAction: ((Context) -> Unit)? = null,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val sourceContext = LocalContext.current
            val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.TAIWAN) }
            val context = sourceContext.createConfigurationContext(configuration)
            val density = LocalDensity.current.density
            pixelsPerDp = density
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration,
                LocalDensity provides Density(density, fontScale)) {
                MaterialTheme(colorScheme = DeskClockColorScheme) {
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black), contentAlignment = Alignment.Center) {
                        MediaWidgetContent(state, isLocked = locked.value, hideWhenLocked = hideWhenLocked,
                            controlsEnabled = controlsEnabled, onOpenNotificationAccess = { settingsClicks++; settingsAction?.invoke(context) },
                            onCommand = { command, position -> commands += command; seekPositions += position },
                            modifier = Modifier.size(width.dp, height.dp).onGloballyPositioned {
                                val rect = it.boundsInWindow()
                                captureBounds.set(AndroidRect(rect.left.roundToInt(), rect.top.roundToInt(), rect.right.roundToInt(), rect.bottom.roundToInt()))
                            })
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        val rect = checkNotNull(captureBounds.get())
        assertEquals("The emulator must fit the requested presentation width", width * pixelsPerDp, rect.width().toFloat(), 1.5f)
        assertEquals("The emulator must fit the requested presentation height", height * pixelsPerDp, rect.height().toFloat(), 1.5f)
    }

    private fun fixture(): MediaWidgetState {
        // Red/blue split with green/yellow edges detects cropping, mirroring and accidental blur.
        val pixels = IntArray(240 * 320) { index ->
            val x = index % 240
            val y = index / 240
            when {
                y < 24 -> AndroidColor.GREEN
                y >= 296 -> AndroidColor.YELLOW
                x < 120 -> AndroidColor.RED
                else -> AndroidColor.BLUE
            }
        }
        return MediaWidgetState(MediaAccess.READY, MediaSessionSnapshot(
            sessionId = 41L, metadataRevision = 7L, packageName = "com.linzin.presentation.fixture",
            title = "唯有追趕風的方向", artist = "知更鳥 / HOYO-MiX / Chevy", album = "Fixture album",
            durationMs = 123_000L,
            // A fixed position with an active phase exercises the actual pause presentation.
            playback = PlaybackClock(positionMs = 30_000L, phase = PlaybackPhase.PLAYING),
            actions = SupportedActions(play = true, pause = true, previous = true, next = true, seek = true),
            artwork = Bitmap.createBitmap(pixels, 240, 320, Bitmap.Config.ARGB_8888),
        ))
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun assertCentredText(tag: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val text = layouts.single()
        for (line in 0 until text.lineCount) {
            assertEquals("$tag centres actual glyphs, not only its container", text.size.width / 2f,
                (text.getLineLeft(line) + text.getLineRight(line)) / 2f, 1.5f)
        }
    }

    private fun assertWholeCover(bitmap: Bitmap, area: Rect) {
        val scale = minOf(area.width / 240f, area.height / 320f)
        val x = area.center.x
        val y = area.center.y
        assertColour(AndroidColor.GREEN, pixel(bitmap, x, y - 148f * scale))
        assertColour(AndroidColor.YELLOW, pixel(bitmap, x, y + 148f * scale))
        assertColour(AndroidColor.RED, pixel(bitmap, x - 4f * scale, y))
        assertColour(AndroidColor.BLUE, pixel(bitmap, x + 4f * scale, y))
    }

    private fun pixel(bitmap: Bitmap, x: Float, y: Float): Int {
        val root = bounds("media_widget")
        return bitmap.getPixel((x - root.left).roundToInt().coerceIn(0, bitmap.width - 1),
            (y - root.top).roundToInt().coerceIn(0, bitmap.height - 1))
    }

    private fun assertColour(expected: Int, actual: Int) {
        assertTrue("The entire cover must stay sharp and retain its original colours", abs(AndroidColor.red(expected) - AndroidColor.red(actual)) <= 24 &&
            abs(AndroidColor.green(expected) - AndroidColor.green(actual)) <= 24 && abs(AndroidColor.blue(expected) - AndroidColor.blue(actual)) <= 24)
    }

    private fun colourRange(bitmap: Bitmap, area: Rect): Int {
        val values = (2..8).flatMap { row ->
            (1..19).map { column -> pixel(bitmap, area.left + area.width * column / 20f, area.top + area.height * row / 10f) }
        }
        return values.maxOf { AndroidColor.red(it) } - values.minOf { AndroidColor.red(it) }
    }

    private fun capture(name: String, checkPixels: (Bitmap) -> Unit = {}) {
        requireDedicatedMediaEmulator()
        compose.waitForIdle()
        val committed = CountDownLatch(1)
        val frameCallback = Runnable { committed.countDown() }
        val decor = compose.activity.window.decorView
        mediaOnMain {
            assertTrue("A real hardware window is required for glass screenshots", decor.isHardwareAccelerated && decor.hasWindowFocus())
            decor.viewTreeObserver.registerFrameCommitCallback(frameCallback)
            decor.postInvalidateOnAnimation()
        }
        try {
            assertTrue("A hardware frame must be committed", committed.await(MEDIA_WAIT_MS, TimeUnit.MILLISECONDS))
        } finally {
            mediaOnMain { if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.unregisterFrameCommitCallback(frameCallback) }
        }
        val rect = AndroidRect(checkNotNull(captureBounds.get()))
        assertTrue(rect.width() in 1..2048 && rect.height() in 1..2048)
        val bitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
        try {
            val finished = CountDownLatch(1)
            val result = AtomicInteger(-1)
            mediaOnMain {
                assertTrue("The entire presentation must be inside the real window", rect.left >= 0 && rect.top >= 0 && rect.right <= decor.width && rect.bottom <= decor.height)
                PixelCopy.request(compose.activity.window, rect, bitmap, { result.set(it); finished.countDown() }, Handler(Looper.getMainLooper()))
            }
            assertTrue(finished.await(MEDIA_WAIT_MS, TimeUnit.MILLISECONDS))
            assertEquals(PixelCopy.SUCCESS, result.get())
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val directory = File(checkNotNull(context.externalCacheDir), "media-instrumentation")
            check(directory.isDirectory || directory.mkdirs())
            val output = File(directory, "presentation-$name.png")
            output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                putString("media_presentation_capture", output.absolutePath)
                putString("media_presentation_frame", "${rect.width()}x${rect.height()}; density=$pixelsPerDp; locale=zh-TW; fixture=240x320; PixelCopy")
            })
            checkPixels(bitmap)
        } finally { bitmap.recycle() }
    }
}
