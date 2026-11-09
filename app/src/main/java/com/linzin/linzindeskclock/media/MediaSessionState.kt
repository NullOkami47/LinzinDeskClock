package com.linzin.linzindeskclock.media

import android.graphics.Bitmap
import android.media.session.PlaybackState

/** STOPPED means the host is not started; CONNECTING means granted but not bound yet. */
enum class MediaAccess { STOPPED, NOT_GRANTED, CONNECTING, READY, UNAVAILABLE }

data class MediaSessionSnapshot(
    val sessionId: Long,
    val metadataRevision: Long,
    val packageName: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val playback: PlaybackClock,
    val actions: SupportedActions,
    val artwork: Bitmap? = null,
)

data class MediaWidgetState(val access: MediaAccess = MediaAccess.STOPPED, val session: MediaSessionSnapshot? = null)

internal fun PlaybackState?.toClock(): PlaybackClock {
    if (this == null) return PlaybackClock()
    val phase = when (state) {
        PlaybackState.STATE_PLAYING -> PlaybackPhase.PLAYING
        PlaybackState.STATE_PAUSED -> PlaybackPhase.PAUSED
        PlaybackState.STATE_STOPPED -> PlaybackPhase.STOPPED
        PlaybackState.STATE_FAST_FORWARDING -> PlaybackPhase.FAST_FORWARDING
        PlaybackState.STATE_REWINDING -> PlaybackPhase.REWINDING
        PlaybackState.STATE_BUFFERING -> PlaybackPhase.BUFFERING
        PlaybackState.STATE_CONNECTING -> PlaybackPhase.CONNECTING
        PlaybackState.STATE_SKIPPING_TO_NEXT, PlaybackState.STATE_SKIPPING_TO_PREVIOUS, PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> PlaybackPhase.SKIPPING
        PlaybackState.STATE_ERROR -> PlaybackPhase.ERROR
        else -> PlaybackPhase.NONE
    }
    return PlaybackClock(position, lastPositionUpdateTime, playbackSpeed, phase)
}

internal fun PlaybackState?.supportedActions(): SupportedActions {
    val mask = this?.actions ?: 0L
    fun has(action: Long) = mask and action != 0L
    return SupportedActions(
        play = has(PlaybackState.ACTION_PLAY),
        pause = has(PlaybackState.ACTION_PAUSE),
        toggle = has(PlaybackState.ACTION_PLAY_PAUSE),
        previous = has(PlaybackState.ACTION_SKIP_TO_PREVIOUS),
        next = has(PlaybackState.ACTION_SKIP_TO_NEXT),
        seek = has(PlaybackState.ACTION_SEEK_TO),
    )
}
