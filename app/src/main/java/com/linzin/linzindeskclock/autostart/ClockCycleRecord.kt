package com.linzin.linzindeskclock.autostart

/** Persisted facts and elapsed-time rules, independent of Android and wrapper lifetime. */
internal data class ClockCycleRecord(
    val armed: Boolean = false,
    val attempted: Boolean = false,
    val suppressed: Boolean = false,
    val lockedAt: Long = -1L,
    val lockBoot: Int = -1,
    val offerToken: String? = null,
    val offerUntil: Long = 0L,
    val offerBoot: Int = -1,
) {
    fun noteLock(now: Long, boot: Int): ClockCycleRecord =
        if (armed) copy(armed = false, lockedAt = now, lockBoot = boot) else this

    fun remainingWindowMs(now: Long, boot: Int): Long {
        if (boot < 0 || lockBoot != boot || lockedAt < 0L || now < lockedAt) return 0L
        return (LOCK_WINDOW_MS - (now - lockedAt)).coerceAtLeast(0L)
    }

    fun canAttempt(now: Long, boot: Int): Boolean =
        !attempted && !suppressed && remainingWindowMs(now, boot) > 0L

    fun acceptsOffer(token: String?, now: Long, boot: Int): Boolean =
        !suppressed && !token.isNullOrEmpty() && token == offerToken &&
            boot >= 0 && offerBoot == boot && now >= 0L && now < offerUntil &&
            offerUntil - now <= OFFER_WINDOW_MS

    fun withoutOffer(): ClockCycleRecord = copy(offerToken = null, offerUntil = 0L, offerBoot = -1)

    companion object {
        const val LOCK_WINDOW_MS = 5_000L
        const val OFFER_WINDOW_MS = 30_000L
    }
}
