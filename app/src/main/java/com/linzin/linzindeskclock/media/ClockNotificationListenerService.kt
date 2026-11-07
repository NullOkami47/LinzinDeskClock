package com.linzin.linzindeskclock.media

import android.content.Intent
import android.service.notification.NotificationListenerService
import androidx.annotation.MainThread

/**
 * Authorisation bridge only. It never reads, stores or logs notification contents.
 * Must be declared in the application's process with BIND_NOTIFICATION_LISTENER_SERVICE.
 */
class ClockNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        MediaListenerConnection.connected(this)
    }

    override fun onListenerDisconnected() {
        MediaListenerConnection.disconnected(this)
        super.onListenerDisconnected()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        MediaListenerConnection.disconnected(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        MediaListenerConnection.disconnected(this)
        super.onDestroy()
    }
}

/** All listener/service/repository callbacks run on the main thread (API 31+). */
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
