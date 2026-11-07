package com.linzin.linzindeskclock.autostart

enum class ActivationState { IDLE, DETECTING, ELIGIBLE, ACTIVE, WAITING_FOR_UNLOCK, SUPPRESSED_UNTIL_UNLOCK }

/** Monotonic-time, event-driven one-shot gate. Construction never invents a lock transition. */
class ClockActivationStateMachine {
    var state: ActivationState = ActivationState.WAITING_FOR_UNLOCK
        private set
    private var started = 0L
    private var deadline = 0L
    private var stableSince: Long? = null
    private var previousSample: Long? = null
    fun onUnlock() { state = ActivationState.IDLE; stableSince = null; previousSample = null }
    fun onLock(now: Long, remainingMs: Long = WINDOW_MS): Boolean {
        if (remainingMs <= 0 || state != ActivationState.IDLE) return false
        deadline = now + remainingMs.coerceAtMost(WINDOW_MS)
        started = now; stableSince = null; previousSample = null; state = ActivationState.DETECTING
        return true
    }
    fun sample(now: Long, eligible: Boolean): Boolean {
        if (state != ActivationState.DETECTING) return false
        if (now < started || previousSample?.let { now < it } == true) { cancel(); return false }
        expire(now)
        if (state != ActivationState.DETECTING) return false
        if (previousSample?.let { now - it > MAX_SAMPLE_GAP_MS } == true) stableSince = null
        previousSample = now
        if (!eligible) { stableSince = null; return false }
        val since = stableSince ?: now.also { stableSince = it }
        if (now - since < STABILITY_MS) return false
        state = ActivationState.ELIGIBLE
        return true
    }
    fun expire(now: Long) { if (state == ActivationState.DETECTING && now >= deadline) cancel() }
    fun activated() { if (state == ActivationState.ELIGIBLE) state = ActivationState.ACTIVE }
    fun cancel() { state = ActivationState.WAITING_FOR_UNLOCK; stableSince = null; previousSample = null }
    fun manualExit() { state = ActivationState.SUPPRESSED_UNTIL_UNLOCK; stableSince = null; previousSample = null }
    companion object {
        const val WINDOW_MS = 5000L
        const val STABILITY_MS = 1500L
        const val MAX_SAMPLE_GAP_MS = 500L
    }
}
