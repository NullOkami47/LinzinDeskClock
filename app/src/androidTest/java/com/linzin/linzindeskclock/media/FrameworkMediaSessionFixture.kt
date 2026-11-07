package com.linzin.linzindeskclock.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class FixtureTransport(val command: String, val positionMs: Long? = null)

/** A real platform session in the target process; no mocks, third-party player or audio playback. */
@Suppress("DEPRECATION")
internal class FrameworkMediaSessionFixture(
    context: Context,
    private val name: String,
    initialState: Int,
    initialActions: Long,
    artwork: Bitmap? = null,
) : Closeable {
    private val session = MediaSession(context, "linzin-media-test-$name")
    private val mutableEvents = MutableStateFlow<List<FixtureTransport>>(emptyList())
    val events: StateFlow<List<FixtureTransport>> = mutableEvents.asStateFlow()
    private var state = initialState
    private var actions = initialActions
    private var position = 1_000L
    private var title = name
    private var cover = artwork
    private var duration = 60_000L
    private var closed = false
    val token: MediaSession.Token get() = session.sessionToken

    init {
        checkMain()
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() {
                record("play")
                publishPlayback(PlaybackState.STATE_PLAYING)
            }
            override fun onPause() {
                record("pause")
                publishPlayback(PlaybackState.STATE_PAUSED)
            }
            override fun onSkipToNext() {
                record("next")
                publishMetadata("$name-next")
            }
            override fun onSkipToPrevious() {
                record("previous")
                publishMetadata("$name-previous")
            }
            override fun onSeekTo(pos: Long) {
                record("seek", pos)
                publishPlayback(positionMs = pos)
            }
            override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                val key = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
                if (key.keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) return false
                if (key.action == KeyEvent.ACTION_DOWN) {
                    record("toggle")
                    publishPlayback(if (state == PlaybackState.STATE_PLAYING) PlaybackState.STATE_PAUSED else PlaybackState.STATE_PLAYING)
                }
                return true
            }
        }, Handler(Looper.getMainLooper()))
        publishMetadata(name, artwork)
        publishPlayback(initialState, initialActions)
        session.isActive = true
    }

    fun publishPlayback(playbackState: Int = state, supportedActions: Long = actions, positionMs: Long = position) {
        checkMain()
        check(!closed)
        state = playbackState
        actions = supportedActions
        position = positionMs
        session.setPlaybackState(PlaybackState.Builder().setActions(actions)
            .setState(state, position, if (state == PlaybackState.STATE_PLAYING) 1f else 0f, SystemClock.elapsedRealtime()).build())
    }

    fun publishMetadata(trackTitle: String, artwork: Bitmap? = cover, durationMs: Long = duration) {
        checkMain()
        check(!closed)
        title = trackTitle
        cover = artwork
        duration = durationMs
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "Fixture artist")
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "Fixture album")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
        cover?.let { metadata.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) }
        session.setMetadata(metadata.build())
    }

    fun clearMetadata() {
        checkMain()
        session.setMetadata(null)
        cover = null
    }

    fun setActive(active: Boolean) {
        checkMain()
        session.isActive = active
    }

    fun awaitEvent(count: Int, expected: FixtureTransport) {
        val received = awaitMediaValue(events, "$name callback $expected") { it.size >= count }
        check(received[count - 1] == expected) { "Unexpected fixture transport callback" }
    }

    override fun close() {
        checkMain()
        if (closed) return
        closed = true
        session.release()
        cover = null
    }

    private fun record(command: String, positionMs: Long? = null) {
        checkMain()
        mutableEvents.value = mutableEvents.value + FixtureTransport(command, positionMs)
    }

    private fun checkMain() { check(Looper.myLooper() == Looper.getMainLooper()) }
}
