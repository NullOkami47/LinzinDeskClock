package com.linzin.linzindeskclock.media

import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.service.notification.NotificationListenerService
import androidx.annotation.MainThread

/**
 * Authorisation bridge only. It never reads, stores or logs notification contents.
 * Must be declared in the application's process with BIND_NOTIFICATION_LISTENER_SERVICE.
 */
class ClockNotificationListenerService : NotificationListenerService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var destroyed = false
    private var unbound = false

    private fun notifyConnection(connected: Boolean) {
        val update = {
            if (connected) {
                if (!destroyed && !unbound) MediaListenerConnection.connected(this)
            } else MediaListenerConnection.disconnected(this)
        }
        // Android versions before N do not guarantee notification callbacks on the main thread.
        if (Looper.myLooper() == Looper.getMainLooper()) update() else mainHandler.post { update() }
    }

    override fun onBind(intent: Intent): IBinder? {
        unbound = false
        return super.onBind(intent)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        notifyConnection(true)
    }

    override fun onListenerDisconnected() {
        notifyConnection(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) super.onListenerDisconnected()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        unbound = true
        mainHandler.removeCallbacksAndMessages(null)
        MediaListenerConnection.disconnected(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        destroyed = true
        mainHandler.removeCallbacksAndMessages(null)
        MediaListenerConnection.disconnected(this)
        super.onDestroy()
    }
}

/** Listener callbacks are marshalled onto the main thread, including on Android 5 and 6. */
@MainThread
internal object MediaListenerConnection {
    private var owner: ClockNotificationListenerService? = null
    private val observers = linkedSetOf<() -> Unit>()
    val isConnected: Boolean get() = owner != null

    fun connected(service: ClockNotificationListenerService) {
        owner = service
        observers.toList().forEach { it() }
    }

    fun disconnected(service: ClockNotificationListenerService) {
        // A late onDestroy from an old instance must not disconnect its replacement.
        if (owner !== service) return
        owner = null
        observers.toList().forEach { it() }
    }

    fun observe(observer: () -> Unit) { observers.add(observer) }
    fun removeObserver(observer: () -> Unit) { observers.remove(observer) }
}
