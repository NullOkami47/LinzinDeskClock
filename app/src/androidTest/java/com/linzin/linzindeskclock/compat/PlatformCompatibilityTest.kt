package com.linzin.linzindeskclock.compat

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.linzin.linzindeskclock.calendar.isCalendarDeviceUnlocked
import com.linzin.linzindeskclock.media.AlbumArtBackground
import com.linzin.linzindeskclock.media.AlbumArtGlassBackground
import com.linzin.linzindeskclock.media.MediaNotificationAccess
import com.linzin.linzindeskclock.power.ScreenOffAccess
import com.linzin.linzindeskclock.settings.BackgroundLaunchAccess
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/** Run on API 21 as well as a current emulator: missing framework methods are fatal errors. */
class PlatformCompatibilityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun permissionChecksRemainUsableOnTheMinimumSdk() {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val unlocked = isCalendarDeviceUnlocked(context)
        if (keyguard.isKeyguardLocked) assertFalse(unlocked)
        MediaNotificationAccess.isGranted(context)
        ScreenOffAccess.isSupported(context)
        BackgroundLaunchAccess.granted(context)
        assertNotNull(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
    }

    @Test fun artworkAndGlassRenderWithoutRequiringAndroid12() {
        val artwork = Bitmap.createBitmap(48, 96, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.MAGENTA)
        }
        compose.setContent {
            Box(Modifier.size(160.dp)) {
                AlbumArtBackground(artwork)
                AlbumArtGlassBackground(artwork, Modifier.size(60.dp))
            }
        }
        compose.waitForIdle()
    }
}
