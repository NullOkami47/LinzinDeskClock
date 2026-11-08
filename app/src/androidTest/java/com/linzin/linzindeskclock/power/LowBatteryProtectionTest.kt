package com.linzin.linzindeskclock.power

import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.linzin.linzindeskclock.DeskClockColorScheme
import com.linzin.linzindeskclock.autostart.ClockEntryMode
import com.linzin.linzindeskclock.media.DedicatedMediaEmulatorRule
import com.linzin.linzindeskclock.media.mediaOnMain
import com.linzin.linzindeskclock.service.AutoClockService
import com.linzin.linzindeskclock.ui.DeskClockScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import java.util.concurrent.atomic.AtomicInteger

class LowBatteryProtectionTest {
    private val compose=createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules:TestRule=RuleChain.outerRule(DedicatedMediaEmulatorRule()).around(compose)
    private fun battery(command:String) {
        val descriptor=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("dumpsys battery " + command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }
    private fun show(action:String,onExit:()->Unit={}) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("desk_clock_prefs",Context.MODE_PRIVATE).edit().clear()
            .putString("low_battery_action",action).putInt("low_battery_threshold",20)
            .putBoolean("pref_manual_brightness",true).putFloat("pref_brightness_val",.6f)
            .putBoolean("pref_dismiss_gesture_tutorial",true).putBoolean("pref_dismiss_layout_tip",true).commit()
        battery("unplug");battery("set level 50");battery("set status 3")
        compose.setContent { MaterialTheme(colorScheme=DeskClockColorScheme) {
            DeskClockScreen(hasHingeSensor=false,entryMode=ClockEntryMode.LOCK_SCREEN,windowOverride=compose.activity.window,onLowBatteryExit=onExit)
        } }
        compose.waitUntil(5000) { mediaOnMain { kotlin.math.abs(compose.activity.window.attributes.screenBrightness-.6f)<.01f } }
    }
    @Test fun lowBatteryDimsRealWindowAndChargingRestoresManualBrightness() {
        try {
            show("DIM")
            battery("set level 19")
            compose.waitUntil(5000) { mediaOnMain { kotlin.math.abs(compose.activity.window.attributes.screenBrightness-.08f)<.01f } }
            battery("set ac 1")
            compose.waitUntil(5000) { mediaOnMain { kotlin.math.abs(compose.activity.window.attributes.screenBrightness-.6f)<.01f } }
        } finally { battery("reset") }
    }
    @Test fun lowBatteryExitFiresOncePerCrossingAndNotAtTheThreshold() {
        val exits=AtomicInteger()
        try {
            show("EXIT") { exits.incrementAndGet() }
            battery("set level 20");compose.waitForIdle()
            assertEquals(0,exits.get())
            battery("set level 19")
            compose.waitUntil(5000) { exits.get()==1 }
            battery("set level 18");compose.waitForIdle()
            assertEquals(1,exits.get())
            battery("set ac 1");compose.waitForIdle()
            assertEquals(1,exits.get())
        } finally { battery("reset") }
    }
    @Test fun lowBatteryRestartIsRejectedUntilBatteryRecoversToThreshold() {
        assertMonitorRestartsAfterLowBattery { battery("set level 20") }
    }
    @Test fun lowBatteryRestartIsRejectedUntilPowerIsConnected() {
        assertMonitorRestartsAfterLowBattery { battery("set ac 1") }
    }
    private fun assertMonitorRestartsAfterLowBattery(recover:()->Unit) {
        val context=compose.activity
        val prefs=context.getSharedPreferences("desk_clock_prefs",Context.MODE_PRIVATE)
        val service=Intent(context,AutoClockService::class.java)
        fun startMonitor() { mediaOnMain { ContextCompat.startForegroundService(context,service) } }
        try {
            mediaOnMain { context.stopService(service) }
            compose.waitUntil(5000) { !AutoClockService.running }
            assertTrue(prefs.edit().putBoolean("auto_clock_enabled",true)
                .putString("low_battery_action","EXIT").putInt("low_battery_threshold",20).commit())
            battery("unplug");battery("set level 50");battery("set status 3")
            compose.waitUntil(5000) { !shouldPauseAutoClockForLowBattery(context) }
            startMonitor()
            compose.waitUntil(5000) { AutoClockService.running }

            battery("set level 19")
            compose.waitUntil(5000) { shouldPauseAutoClockForLowBattery(context) }
            // Require acknowledgement of this start, not a stale running=false value.
            assertTrue(prefs.edit().putString("auto_last_result","LOW_BATTERY_RESTART_PENDING").commit())
            startMonitor()
            compose.waitUntil(5000) {
                prefs.getString("auto_last_result",null)=="MONITOR_STOPPED" && !AutoClockService.running
            }
            assertFalse(AutoClockService.running)
            assertTrue("Low-battery suspension must retain user opt-in",prefs.getBoolean("auto_clock_enabled",false))

            recover()
            compose.waitUntil(5000) { !shouldPauseAutoClockForLowBattery(context) }
            startMonitor()
            compose.waitUntil(5000) { AutoClockService.running }
        } finally {
            try {
                assertTrue(prefs.edit().putBoolean("auto_clock_enabled",false).commit())
            } finally {
                try {
                    mediaOnMain { context.stopService(service) }
                    compose.waitUntil(5000) { !AutoClockService.running }
                } finally { battery("reset") }
            }
        }
    }
}
