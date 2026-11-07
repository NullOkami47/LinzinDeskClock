package com.linzin.linzindeskclock.media

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.UserManager
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import java.io.Closeable
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

internal const val MEDIA_WAIT_MS = 8_000L
internal const val MEDIA_QUIET_MS = 350L
private val mediaMainHandler = Handler(Looper.getMainLooper())

/** MUST run before launching the test host or issuing any shell command. No physical device opt-out. */
internal fun requireDedicatedMediaEmulator() {
    val hardware = Build.HARDWARE.lowercase()
    val fingerprint = Build.FINGERPRINT.lowercase()
    val emulatorHardware = hardware == "ranchu" || hardware == "goldfish" || hardware.startsWith("cutf_")
    val emulatorFingerprint = fingerprint.startsWith("generic") || fingerprint.contains("sdk_gphone") ||
        fingerprint.contains("sdk_phone") || fingerprint.contains("vsoc_")
    assumeTrue("Media instrumentation is emulator-only; physical devices must not run this suite.",
        emulatorHardware && emulatorFingerprint)
    assumeTrue("Pass -e linzinDedicatedMediaEmulator true on the explicitly selected disposable emulator.",
        InstrumentationRegistry.getArguments().getString("linzinDedicatedMediaEmulator") == "true")
    assumeTrue("Media RenderEffect instrumentation requires API 31+.", Build.VERSION.SDK_INT >= 31)
    val userManager = InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(UserManager::class.java)
    assumeTrue("Run this dedicated emulator suite as system user 0, not in a work/secondary profile.", userManager?.isSystemUser == true)
}

/** Outermost rule: even the Compose host is never launched by these tests on a physical device. */
internal class DedicatedMediaEmulatorRule : TestRule {
    override fun apply(base: Statement, description: Description) = object : Statement() {
        override fun evaluate() {
            requireDedicatedMediaEmulator()
            base.evaluate()
        }
    }
}

/** Repository and framework fixture mutations are always posted to the target process's main looper. */
internal fun <T> mediaOnMain(block: () -> T): T {
    if (Looper.myLooper() == Looper.getMainLooper()) return block()
    val task = FutureTask(Callable(block))
    check(mediaMainHandler.post(task))
    return try {
        task.get(MEDIA_WAIT_MS, TimeUnit.MILLISECONDS)
    } catch (failure: ExecutionException) {
        throw failure.cause ?: failure
    } finally {
        mediaMainHandler.removeCallbacks(task)
        if (!task.isDone) task.cancel(false)
    }
}

internal fun awaitMediaCondition(label: String, condition: () -> Boolean) {
    try {
        runBlocking {
            withTimeout(MEDIA_WAIT_MS) {
                // Bounded condition polling for OS permission state, never a fixed settling sleep.
                while (!mediaOnMain(condition)) delay(25)
            }
        }
    } catch (failure: TimeoutCancellationException) {
        throw AssertionError("Timed out waiting for $label", failure)
    }
}

internal fun <T> awaitMediaValue(flow: StateFlow<T>, label: String, condition: (T) -> Boolean): T = try {
    runBlocking { withTimeout(MEDIA_WAIT_MS) { flow.first(condition) } }
} catch (failure: TimeoutCancellationException) {
    throw AssertionError("Timed out waiting for $label", failure)
}

/** A deliberately bounded negative observation, not a claim about infinite future callbacks. */
internal fun <T> assertMediaValueStays(flow: StateFlow<T>, label: String, condition: (T) -> Boolean) {
    val violation = runBlocking {
        withTimeoutOrNull(MEDIA_QUIET_MS) { flow.first { !condition(it) } }
    }
    assertTrue("$label changed during the $MEDIA_QUIET_MS ms observation window", violation == null)
}

/**
 * Changes ONLY this application's notification listener for this process's user.
 * No adopted MEDIA_CONTENT_CONTROL identity: revocation must exercise real app permissions.
 * use {} always attempts restoration, even when granting, a test, or cleanup fails.
 */
internal class NotificationListenerAccessLease : Closeable {
    val context: Context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val notificationManager = checkNotNull(context.getSystemService(NotificationManager::class.java))
    private val component: ComponentName
    private val userId = 0 // requireDedicatedMediaEmulator verifies the target process is system user 0.
    private val originalGranted: Boolean
    private val otherListeners: Set<ComponentName>
    private var mutated = false

    init {
        requireDedicatedMediaEmulator()
        component = MediaNotificationAccess.component(context)
        check(component.packageName == context.packageName)
        check(component.className == "com.linzin.linzindeskclock.media.ClockNotificationListenerService")
        check(component.flattenToString().matches(Regex("[A-Za-z0-9_.$/]+")))
        check(userId >= 0)
        @Suppress("DEPRECATION")
        val service = context.packageManager.getServiceInfo(component, 0)
        assertEquals("Listener must be in the target process", context.applicationInfo.processName, service.processName)
        assertEquals("Listener binding permission", "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", service.permission)
        originalGranted = isGranted()
        otherListeners = enabledListeners() - component
    }

    fun isGranted(): Boolean = notificationManager.isNotificationListenerAccessGranted(component)

    fun setGranted(granted: Boolean) {
        requireDedicatedMediaEmulator() // Also guard any direct call to this helper.
        if (isGranted() == granted) return
        // Set before the command, so a timeout still triggers restoration.
        mutated = true
        command(granted)
        awaitMediaCondition("notification listener access = $granted") { isGranted() == granted }
    }

    override fun close() {
        try {
            if (mutated) {
                // Force the original command even if a previous command timed out.
                command(originalGranted)
                awaitMediaCondition("original notification listener access restored") { isGranted() == originalGranted }
            }
        } finally {
            // Never overwrite the whole setting: other listeners are observed, not modified.
            assertEquals("Unrelated notification listener access changed", otherListeners, enabledListeners() - component)
        }
    }

    private fun command(granted: Boolean) {
        requireDedicatedMediaEmulator()
        val operation = if (granted) "allow_listener" else "disallow_listener"
        val command = "cmd notification $operation " + component.flattenToString() + " $userId"
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        val reader = Executors.newSingleThreadExecutor { task -> Thread(task, "media-shell-output").apply { isDaemon = true } }
        try {
            val result = reader.submit(Callable {
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText().take(4096) }
            }).get(5, TimeUnit.SECONDS)
            assertTrue("Notification command failed: $result", !result.contains("Exception") &&
                !result.contains("Unknown command", ignoreCase = true) && !result.contains("usage:", ignoreCase = true))
        } finally {
            descriptor.close()
            reader.shutdownNow()
        }
    }

    private fun enabledListeners(): Set<ComponentName> =
        Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            .orEmpty().split(':').mapNotNull(ComponentName::unflattenFromString).toSet()
}

internal class MediaRepositoryHarness(val access: NotificationListenerAccessLease) : Closeable {
    val context: Context get() = access.context
    val repository = mediaOnMain { MediaSessionRepository(context) }
    private val fixtures = mutableListOf<FrameworkMediaSessionFixture>()

    fun startReady() {
        mediaOnMain { repository.setInteractionLocked(false); repository.start(); repository.start() }
        awaitState("authorised listener connected") { it.access == MediaAccess.READY }
        assertEquals("Dedicated emulator must have no pre-existing media sessions", null, repository.state.value.session)
    }

    fun session(name: String, state: Int, actions: Long, artwork: android.graphics.Bitmap? = null): FrameworkMediaSessionFixture = mediaOnMain {
        FrameworkMediaSessionFixture(context, name, state, actions, artwork).also(fixtures::add)
    }

    fun awaitState(label: String, condition: (MediaWidgetState) -> Boolean): MediaWidgetState =
        awaitMediaValue(repository.state, label, condition)

    fun awaitSession(title: String, condition: (MediaSessionSnapshot) -> Boolean = { true }): MediaSessionSnapshot =
        checkNotNull(awaitState("fixture session $title") {
            it.access == MediaAccess.READY && it.session?.let { session -> session.title == title && condition(session) } == true
        }.session)

    override fun close() {
        mediaOnMain {
            try { repository.stop() } finally { fixtures.asReversed().forEach { it.close() } }
        }
    }
}

internal fun withMediaRepository(initiallyGranted: Boolean = true, block: MediaRepositoryHarness.() -> Unit) {
    requireDedicatedMediaEmulator()
    NotificationListenerAccessLease().use { access ->
        access.setGranted(initiallyGranted)
        MediaRepositoryHarness(access).use { it.block() }
    }
}
