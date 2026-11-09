package com.linzin.linzindeskclock.power

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DeviceAdminInfo
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.linzin.linzindeskclock.autostart.ClockCycleStore
import com.linzin.linzindeskclock.media.DedicatedMediaEmulatorRule
import com.linzin.linzindeskclock.media.awaitMediaCondition
import com.linzin.linzindeskclock.media.mediaOnMain
import com.linzin.linzindeskclock.media.requireDedicatedMediaEmulator
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

internal fun screenOffShell(command: String): String {
    requireDedicatedMediaEmulator()
    val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    return ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
}

class ScreenOffSystemTest {
    @get:Rule val emulator: org.junit.rules.TestRule = DedicatedMediaEmulatorRule()
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun manifestRequestsOnlyForceLockWithSystemProtectedReceiver() {
        val component = ScreenOffAccess.component(context)
        @Suppress("DEPRECATION")
        val receiver = context.packageManager.getReceiverInfo(component, android.content.pm.PackageManager.GET_META_DATA)
        assertEquals("android.permission.BIND_DEVICE_ADMIN", receiver.permission)
        val info = DeviceAdminInfo(context, android.content.pm.ResolveInfo().apply { activityInfo = receiver })
        assertTrue(info.usesPolicy(DeviceAdminInfo.USES_POLICY_FORCE_LOCK))
        val policies = mutableListOf<String>()
        val xml = receiver.loadXmlMetaData(context.packageManager, DeviceAdminReceiver.DEVICE_ADMIN_META_DATA)
        xml.use {
            while (it.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (it.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && it.depth == 3) policies.add(it.name)
            }
        }
        assertEquals(listOf("force-lock"), policies)
        val intent = ScreenOffAccess.activationIntent(context)
        assertEquals(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN, intent.action)
        @Suppress("DEPRECATION")
        assertEquals(component, intent.getParcelableExtra<android.content.ComponentName>(DevicePolicyManager.EXTRA_DEVICE_ADMIN))
    }

    @Test fun noConsentReleasesWakeFlagsAndFinishesWithoutDimmingOrGrantingAdmin() {
        assertFalse("Disposable emulator must start without this optional grant", ScreenOffAccess.isGranted(context))
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val finished = AtomicInteger()
                val cycle = ClockCycleStore(context)
                cycle.onUnlock()
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                activity.window.attributes = activity.window.attributes.apply { screenBrightness = .6f }
                val controller = ScreenOffController(context, activity.window,
                    prepareHost = {
                        assertTrue(cycle.suppressed)
                        activity.setTurnScreenOn(false)
                        activity.setShowWhenLocked(false)
                    }, finishHost = { finished.incrementAndGet(); activity.finish() })
                controller.request()
                controller.request()
                assertEquals(1, finished.get())
                assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                assertEquals(.6f, activity.window.attributes.screenBrightness, .001f)
                assertFalse(ScreenOffAccess.isGranted(context))
                assertTrue(context.getSystemService(PowerManager::class.java).isInteractive)
                cycle.observeUnlocked()
                cycle.noteLock()
                assertTrue(cycle.suppressed)
                assertFalse(cycle.claimAttempt())
            }
        }
    }

    @Test fun authorisedTimeoutTurnsDisplayOffAndSuppressesItsOwnLockCycle() {
        val admin = context.getSystemService(DevicePolicyManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val component = ScreenOffAccess.component(context)
        val prefs = context.getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE)
        val originalPrefs = prefs.all.toMap()
        assertFalse("Test must not inherit an admin grant", ScreenOffAccess.isGranted(context))
        try {
            val result = screenOffShell("dpm set-active-admin --user 0 "+component.flattenToString())
            assertTrue(result, result.contains("Success"))
            awaitMediaCondition("lock-only admin enabled") { ScreenOffAccess.isGranted(context) }
            screenOffShell("input keyevent KEYCODE_WAKEUP")
            screenOffShell("wm dismiss-keyguard")
            prefs.edit().clear()
                .putInt("pref_non_fold_bypass_count", 3)
                .putLong("pref_timeout_millis", 1_000L)
                .putBoolean("pref_manual_brightness", true).putFloat("pref_brightness_val", .6f)
                .putString("low_battery_action", "OFF")
                .putBoolean("pref_dismiss_gesture_tutorial", true).putBoolean("pref_dismiss_layout_tip", true)
                .commit()
            ActivityScenario.launch(com.linzin.linzindeskclock.MainActivity::class.java).use { scenario ->
                awaitMediaCondition("real clock timeout turns system display off") { !power.isInteractive }
                val finishDeadline = android.os.SystemClock.elapsedRealtime() + 8_000L
                while (scenario.state != androidx.lifecycle.Lifecycle.State.DESTROYED &&
                    android.os.SystemClock.elapsedRealtime() < finishDeadline) Thread.sleep(50)
                assertEquals("MainActivity completes asynchronous finish after locking",
                    androidx.lifecycle.Lifecycle.State.DESTROYED, scenario.state)
                // Observe through the full five-second auto-activation scan window.
                val end = android.os.SystemClock.elapsedRealtime() + 5_500L
                while (android.os.SystemClock.elapsedRealtime() < end) {
                    assertFalse("Timed-out clock must stay off", power.isInteractive)
                    val cycle = ClockCycleStore(context)
                    cycle.noteLock()
                    cycle.observeUnlocked()
                    assertTrue(cycle.suppressed)
                    assertNull(cycle.activeWindow())
                    assertFalse(cycle.claimAttempt())
                    Thread.sleep(100)
                }
            }
        } finally {
            mediaOnMain { admin.removeActiveAdmin(component) }
            awaitMediaCondition("temporary admin grant removed") { !ScreenOffAccess.isGranted(context) }
            screenOffShell("input keyevent KEYCODE_WAKEUP")
            screenOffShell("wm dismiss-keyguard")
            awaitMediaCondition("emulator awake after test") { power.isInteractive }
            ClockCycleStore(context).onUnlock()
            prefs.edit().clear().apply {
                originalPrefs.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is String -> putString(key, value)
                        is Set<*> -> @Suppress("UNCHECKED_CAST") putStringSet(key, value as Set<String>)
                    }
                }
            }.commit()
        }
    }
}
