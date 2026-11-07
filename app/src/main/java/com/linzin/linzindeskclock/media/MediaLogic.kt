package com.linzin.linzindeskclock.media

/** Platform-independent decisions; all time values use elapsed realtime, never wall time. */
enum class PlaybackPhase {
    NONE, STOPPED, PAUSED, PLAYING, FAST_FORWARDING, REWINDING, BUFFERING, CONNECTING, SKIPPING, ERROR;

    val advances: Boolean get() = this == PLAYING || this == FAST_FORWARDING || this == REWINDING
    val active: Boolean get() = advances || this == BUFFERING || this == CONNECTING || this == SKIPPING
}

data class SessionCandidate<T>(val id: T, val phase: PlaybackPhase)

/** Preserve the selected session within a priority tier, otherwise use the manager's order. */
fun <T> selectSession(candidates: List<SessionCandidate<T>>, selectedId: T?): T? {
    fun priority(phase: PlaybackPhase): Int = when {
        phase.advances -> 0
        phase == PlaybackPhase.PAUSED -> 1
        phase.active -> 2
        phase == PlaybackPhase.STOPPED -> 3
        phase == PlaybackPhase.NONE -> 4
        else -> 5
    }
    val best = candidates.minOfOrNull { priority(it.phase) } ?: return null
    return candidates.firstOrNull { it.id == selectedId && priority(it.phase) == best }?.id
        ?: candidates.first { priority(it.phase) == best }.id
}

data class SupportedActions(
    val play: Boolean = false,
    val pause: Boolean = false,
    val toggle: Boolean = false,
    val previous: Boolean = false,
    val next: Boolean = false,
    val seek: Boolean = false,
)

enum class MediaCommand { PLAY, PAUSE, TOGGLE, PREVIOUS, NEXT, SEEK }

fun primaryCommand(phase: PlaybackPhase, actions: SupportedActions): MediaCommand? = when {
    phase.active && actions.pause -> MediaCommand.PAUSE
    !phase.active && actions.play -> MediaCommand.PLAY
    // A generic toggle is ambiguous while buffering, connecting or skipping.
    actions.toggle && (!phase.active || phase.advances) -> MediaCommand.TOGGLE
    else -> null
}

fun isCommandSupported(command: MediaCommand, phase: PlaybackPhase, actions: SupportedActions, durationMs: Long): Boolean = when (command) {
    MediaCommand.PLAY -> actions.play && !phase.active
    MediaCommand.PAUSE -> actions.pause && phase.active
    MediaCommand.TOGGLE -> actions.toggle && (!phase.active || phase.advances)
    MediaCommand.PREVIOUS -> actions.previous
    MediaCommand.NEXT -> actions.next
    MediaCommand.SEEK -> actions.seek && durationMs > 0
}

fun canControlMedia(started: Boolean, authorised: Boolean, connected: Boolean, controlsBlocked: Boolean, expectedSessionId: Long, selectedSessionId: Long?): Boolean =
    started && authorised && connected && !controlsBlocked && selectedSessionId == expectedSessionId

fun clampSeekPosition(positionMs: Long, durationMs: Long): Long = positionMs.coerceIn(0, durationMs.coerceAtLeast(0))

data class PlaybackClock(
    val positionMs: Long = -1,
    val updatedAtElapsedMs: Long = 0,
    val speed: Float = 0f,
    val phase: PlaybackPhase = PlaybackPhase.NONE,
) {
    fun positionAt(nowElapsedMs: Long, durationMs: Long): Long? {
        if (positionMs < 0) return null
        val elapsed = if (phase.advances && speed.isFinite() && updatedAtElapsedMs > 0 && nowElapsedMs >= updatedAtElapsedMs) {
            nowElapsedMs.toDouble() - updatedAtElapsedMs.toDouble()
        } else 0.0
        val projected = positionMs.toDouble() + if (elapsed > 0) elapsed * speed.toDouble() else 0.0
        val maximum = if (durationMs > 0) durationMs else Long.MAX_VALUE
        return projected.coerceIn(0.0, maximum.toDouble()).toLong()
    }
}

/** Privacy hides content and controls; UI Lock only blocks controls. Keyguard alone is not a transport gate. */
data class MediaPresentationPolicy(val hideContent: Boolean, val allowControls: Boolean)

fun mediaPresentationPolicy(isLocked: Boolean, hideWhenLocked: Boolean, controlsEnabled: Boolean): MediaPresentationPolicy =
    MediaPresentationPolicy(hideContent = isLocked && hideWhenLocked, allowControls = !(isLocked && hideWhenLocked) && controlsEnabled)
