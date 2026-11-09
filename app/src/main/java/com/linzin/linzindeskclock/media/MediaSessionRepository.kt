package com.linzin.linzindeskclock.media

import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.KeyEvent
import androidx.annotation.MainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One started UI host owns one repository. start/stop are idempotent and main-thread only.
 * No MediaSession token, metadata or cover is persisted. All controllers are observed so a
 * background player's transition to PLAYING can replace the selected paused session.
 */
@MainThread
class MediaSessionRepository(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(MediaSessionManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(MediaWidgetState())
    val state: StateFlow<MediaWidgetState> = mutableState.asStateFlow()
    private val records = linkedMapOf<MediaSession.Token, SessionRecord>()
    private val destroyed = linkedSetOf<MediaSession.Token>()
    private val artworkCache = ArtworkCache()
    private var activeListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var permissionObserverRegistered = false
    private var started = false
    private var interactionLocked = true
    private var scope: CoroutineScope? = null
    private var selectedToken: MediaSession.Token? = null
    private var nextSessionId = 0L
    private var generation = 0L
    private var artRequest = 0L
    private var artJob: Job? = null
    private var artSource: Bitmap? = null
    private var artSourceGeneration = 0
    private var loadedArt: Bitmap? = null
    private var rebindRequested = false

    private val connectionObserver: () -> Unit = {
        if (!MediaListenerConnection.isConnected) rebindRequested = false
        refreshAccess()
    }
    private val permissionObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) { refreshAccess() }
    }
    private val permissionCheck = object : Runnable {
        override fun run() {
            if (!started) return
            refreshAccess()
            if (started) handler.postDelayed(this, 1_000)
        }
    }

    fun setInteractionLocked(locked: Boolean) {
        checkMainThread()
        interactionLocked = locked
    }

    fun start() {
        checkMainThread()
        if (started) return
        started = true
        generation++
        rebindRequested = false
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        MediaListenerConnection.observe(connectionObserver)
        try {
            app.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor("enabled_notification_listeners"), false, permissionObserver,
            )
            permissionObserverRegistered = true
        } catch (_: RuntimeException) {
            // OEM restrictions: connection callbacks and the foreground-only check still apply.
        }
        refreshAccess()
        handler.postDelayed(permissionCheck, 1_000)
    }

    fun stop() {
        checkMainThread()
        started = false
        generation++
        interactionLocked = true
        handler.removeCallbacks(permissionCheck)
        MediaListenerConnection.removeObserver(connectionObserver)
        if (permissionObserverRegistered) {
            try { app.contentResolver.unregisterContentObserver(permissionObserver) } catch (_: RuntimeException) { }
            permissionObserverRegistered = false
        }
        clearSessions()
        scope?.cancel()
        scope = null
        destroyed.clear()
        mutableState.value = MediaWidgetState()
    }

    /** Rechecks permission, binding, UI/privacy policy, selected identity and supported actions. */
    fun dispatch(sessionId: Long, command: MediaCommand, seekPositionMs: Long? = null, metadataRevision: Long? = null): Boolean {
        checkMainThread()
        if (!validateAccess()) return false
        val record = records[selectedToken] ?: return false
        // Authorised MediaController transports are also available on the lock screen when
        // privacy is off. This does not dismiss Keyguard or launch another application's UI.
        if (!canControlMedia(started, MediaNotificationAccess.isGranted(app), MediaListenerConnection.isConnected,
                interactionLocked, sessionId, record.id)) return false
        if (metadataRevision != null && metadataRevision != record.metadataRevision) return false
        return try {
            val playback = record.controller.playbackState
            val duration = record.metadata.duration()
            if (!isCommandSupported(command, playback.toClock().phase, playback.supportedActions(), duration)) return false
            val controls = record.controller.transportControls
            when (command) {
                MediaCommand.PLAY -> controls.play()
                MediaCommand.PAUSE -> controls.pause()
                MediaCommand.PREVIOUS -> controls.skipToPrevious()
                MediaCommand.NEXT -> controls.skipToNext()
                MediaCommand.SEEK -> controls.seekTo(clampSeekPosition(seekPositionMs ?: return false, duration))
                MediaCommand.TOGGLE -> {
                    // ACTION_PLAY_PAUSE does not imply ACTION_PLAY or ACTION_PAUSE support.
                    val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    val handled = record.controller.dispatchMediaButtonEvent(down)
                    record.controller.dispatchMediaButtonEvent(up)
                    return handled
                }
            }
            true
        } catch (_: SecurityException) {
            block(MediaAccess.NOT_GRANTED)
            false
        } catch (_: RuntimeException) {
            forget(record)
            publish()
            false
        }
    }

    private fun refreshAccess() {
        if (!started) return
        if (!MediaNotificationAccess.isGranted(app)) { block(MediaAccess.NOT_GRANTED); return }
        if (!MediaListenerConnection.isConnected) {
            block(MediaAccess.CONNECTING)
            if (!rebindRequested) {
                rebindRequested = true
                try { NotificationListenerService.requestRebind(MediaNotificationAccess.component(app)) } catch (_: RuntimeException) { }
            }
            return
        }
        if (manager == null) { block(MediaAccess.UNAVAILABLE); return }
        if (activeListener != null) return
        val run = generation
        val listener = object : MediaSessionManager.OnActiveSessionsChangedListener {
            override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
                if (run != generation || activeListener !== this || !validateAccess()) return
                reconcile(controllers.orEmpty())
            }
        }
        activeListener = listener
        try {
            manager.addOnActiveSessionsChangedListener(listener, MediaNotificationAccess.component(app), handler)
            reconcile(manager.getActiveSessions(MediaNotificationAccess.component(app)))
        } catch (_: SecurityException) {
            block(MediaAccess.NOT_GRANTED)
        } catch (_: RuntimeException) {
            block(MediaAccess.UNAVAILABLE)
        }
    }

    private fun validateAccess(): Boolean {
        if (!started) return false
        if (!MediaNotificationAccess.isGranted(app)) { block(MediaAccess.NOT_GRANTED); return false }
        if (!MediaListenerConnection.isConnected) { block(MediaAccess.CONNECTING); return false }
        return true
    }

    private fun reconcile(controllers: List<MediaController>) {
        if (!validateAccess()) return
        val tokens = controllers.map { it.sessionToken }.toSet()
        destroyed.retainAll(tokens)
        records.values.filter { it.controller.sessionToken !in tokens }.toList().forEach(::forget)
        // Preserve the manager's ordering for initial selection without disturbing a tied selection.
        val ordered = linkedMapOf<MediaSession.Token, SessionRecord>()
        for (controller in controllers) {
            val token = controller.sessionToken
            if (token in destroyed) continue
            val record = records[token] ?: SessionRecord(++nextSessionId, controller)
            if (token !in records) {
                records[token] = record
                try {
                    controller.registerCallback(record.callback, handler)
                    record.playback = controller.playbackState
                    record.metadata = controller.metadata
                } catch (_: SecurityException) {
                    block(MediaAccess.NOT_GRANTED)
                    return
                } catch (_: RuntimeException) {
                    forget(record)
                    continue
                }
            }
            ordered[token] = record
        }
        records.clear()
        records.putAll(ordered)
        publish()
    }

    private inner class SessionRecord(val id: Long, val controller: MediaController) {
        var playback: PlaybackState? = null
        var metadata: MediaMetadata? = null
        var metadataRevision = 0L
        private fun current(): Boolean = records[controller.sessionToken] === this && validateAccess()
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                if (!current()) return
                playback = state
                publish()
            }
            override fun onMetadataChanged(value: MediaMetadata?) {
                if (!current()) return
                metadata = value
                metadataRevision++
                publish()
            }
            override fun onSessionDestroyed() {
                if (!current()) return
                destroyed.add(controller.sessionToken)
                while (destroyed.size > 64) destroyed.remove(destroyed.first())
                forget(this@SessionRecord)
                publish()
            }
        }
    }

    private fun forget(record: SessionRecord) {
        records.remove(record.controller.sessionToken)
        try { record.controller.unregisterCallback(record.callback) } catch (_: RuntimeException) { }
        record.metadata = null
        record.playback = null
    }

    private fun publish() {
        if (!validateAccess()) return
        val token = selectSession(records.map { (key, record) -> SessionCandidate(key, record.playback.toClock().phase) }, selectedToken)
        if (token != selectedToken) {
            clearArtwork()
            selectedToken = token
        }
        val record = records[token]
        if (record == null) {
            mutableState.value = MediaWidgetState(MediaAccess.READY)
            return
        }
        val metadata = record.metadata
        val source = metadata.embeddedArtwork()
        val sourceGeneration = source?.generationId ?: 0
        val changed = source !== artSource || sourceGeneration != artSourceGeneration
        if (changed) {
            artJob?.cancel()
            artRequest++
            artSource = source
            artSourceGeneration = sourceGeneration
            if (source == null) loadedArt = null
        }
        mutableState.value = MediaWidgetState(MediaAccess.READY, MediaSessionSnapshot(
            sessionId = record.id,
            metadataRevision = record.metadataRevision,
            packageName = record.controller.packageName,
            title = metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, MediaMetadata.METADATA_KEY_TITLE),
            artist = metadata.text(MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            album = metadata.text(MediaMetadata.METADATA_KEY_ALBUM),
            durationMs = metadata.duration(),
            playback = record.playback.toClock(),
            actions = record.playback.supportedActions(),
            artwork = loadedArt,
        ))
        if (changed && source != null) {
            val request = artRequest
            val run = generation
            artJob = scope?.launch {
                val bitmap = withContext(Dispatchers.Default) { artworkCache.load(source) }
                if (run != generation || request != artRequest || records[selectedToken] !== record || !validateAccess()) return@launch
                loadedArt = bitmap
                val current = mutableState.value
                mutableState.value = current.copy(session = current.session?.copy(artwork = bitmap))
            }
        }
    }

    private fun clearArtwork() {
        artRequest++
        artJob?.cancel()
        artJob = null
        artSource = null
        artSourceGeneration = 0
        loadedArt = null
    }

    private fun clearSessions() {
        activeListener?.let { listener ->
            activeListener = null
            try { manager?.removeOnActiveSessionsChangedListener(listener) } catch (_: RuntimeException) { }
        }
        records.values.toList().forEach(::forget)
        selectedToken = null
        clearArtwork()
        artworkCache.clear()
    }

    private fun block(access: MediaAccess) {
        clearSessions()
        mutableState.value = MediaWidgetState(access)
    }

    private fun checkMainThread() { check(Looper.myLooper() == Looper.getMainLooper()) }
}


private fun MediaMetadata?.duration(): Long = try {
    (this?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L).coerceAtLeast(0)
} catch (_: RuntimeException) { 0L }

private fun MediaMetadata?.text(vararg keys: String): String {
    if (this == null) return ""
    for (key in keys) {
        val value = try { getText(key)?.toString()?.take(512)?.trim().orEmpty() } catch (_: RuntimeException) { "" }
        if (value.isNotEmpty()) return value
    }
    return ""
}

private fun MediaMetadata?.embeddedArtwork(): Bitmap? {
    if (this == null) return null
    for (key in arrayOf(MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON)) {
        val bitmap = try { getBitmap(key) } catch (_: RuntimeException) { null }
        if (bitmap != null && !bitmap.isRecycled) return bitmap
    }
    return null
}
