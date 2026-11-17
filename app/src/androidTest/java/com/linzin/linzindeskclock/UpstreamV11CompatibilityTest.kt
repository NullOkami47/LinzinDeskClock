package com.linzin.linzindeskclock

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpstreamV11CompatibilityTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun enabledWeatherWithoutDataOffersRefresh() {
        var refreshes = 0
        val context = compose.activity
        compose.setContent {
            MaterialTheme {
                SideWeatherWidget(
                    weatherEnabled = true,
                    weatherInfo = null,
                    isFahrenheit = false,
                    onRefresh = { refreshes++ }
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.weather_unavailable_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.weather_unavailable_desc)).performClick()
        compose.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test
    fun refreshingWeatherCannotStartAnotherRequest() {
        var refreshes = 0
        val context = compose.activity
        compose.setContent {
            MaterialTheme {
                SideWeatherWidget(
                    weatherEnabled = true,
                    weatherInfo = null,
                    isFahrenheit = false,
                    isRefreshing = true,
                    onRefresh = { refreshes++ }
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.weather_refreshing_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.weather_refreshing_title)).performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, refreshes) }
    }

    @Test
    fun cutoutPreferenceUsesSupportedWindowMode() {
        compose.runOnUiThread {
            val window = compose.activity.window
            val original = if (Build.VERSION.SDK_INT >= 28) window.attributes.layoutInDisplayCutoutMode else 0
            try {
                applyDisplayCutoutMode(window, false)
                if (Build.VERSION.SDK_INT >= 28) {
                    assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER,
                        window.attributes.layoutInDisplayCutoutMode)
                }
                applyDisplayCutoutMode(window, true)
                if (Build.VERSION.SDK_INT >= 28) {
                    assertEquals(
                        if (Build.VERSION.SDK_INT >= 30) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                        else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES,
                        window.attributes.layoutInDisplayCutoutMode
                    )
                }
            } finally {
                if (Build.VERSION.SDK_INT >= 28) {
                    window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = original }
                }
            }
        }
    }
}
