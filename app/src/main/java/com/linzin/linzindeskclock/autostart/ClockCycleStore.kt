package com.linzin.linzindeskclock.autostart

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import java.util.UUID
import java.util.WeakHashMap

/**
 * Synchronous writes preserve cycle decisions across wrapper/process recreation on success.
 * A failed write blocks this preferences instance across wrappers until a successful unlock.
 */
class ClockCycleStore(
    private val prefs: SharedPreferences,
    private val elapsedRealtime: () -> Long,
    private val bootProvider: () -> Int,
    private val tokenGenerator: () -> String,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences("clock_cycle", Context.MODE_PRIVATE),
        { SystemClock.elapsedRealtime() },
        { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) },
        { UUID.randomUUID().toString() },
    )

    fun noteLock() {
        synchronized(prefs) {
            if (writeFailed) return
            val record = read()
            // Consume the arm even when boot is unknown; a duplicate must never start a later window.
            if (record.armed) write(record.noteLock(elapsedRealtime(), currentBoot()))
        }
    }

    fun remainingWindowMs(): Long = synchronized(prefs) {
        if (writeFailed) 0L else read().remainingWindowMs(elapsedRealtime(), currentBoot())
    }

    fun activeWindow(): ClockLockWindow? = synchronized(prefs) {
        val record = read()
        if(writeFailed || record.suppressed || record.remainingWindowMs(elapsedRealtime(),currentBoot()) <= 0) null
        else ClockLockWindow(record.lockBoot,record.lockedAt)
    }

    fun isCurrentWindow(window: ClockLockWindow): Boolean = activeWindow() == window

    /** Session continuation may outlive scanning, but never an unlock, suppression or reboot. */
    fun currentCycle(): ClockLockWindow? = synchronized(prefs) {
        val record=read()
        val boot=currentBoot()
        if(writeFailed || record.suppressed || boot < 0 || record.lockBoot != boot || record.lockedAt < 0 || elapsedRealtime() < record.lockedAt) null
        else ClockLockWindow(record.lockBoot,record.lockedAt)
    }

    fun claimAttempt(): Boolean = synchronized(prefs) {
        if (writeFailed) return false
        val record = read()
        if (!record.canAttempt(elapsedRealtime(), currentBoot())) return false
        write(record.copy(attempted = true))
    }

    val suppressed: Boolean
        get() = synchronized(prefs) { writeFailed || read().suppressed }

    /** A foreground/screen-on observation is not proof of a new unlock after an explicit exit. */
    fun observeUnlocked() {
        synchronized(prefs) {
            if (!suppressed) onUnlock()
        }
    }

    /** Only an explicit foreground user action can recover a missed unlock broadcast. */
    fun onExplicitUserEntry(interactive: Boolean, keyguardLocked: Boolean) {
        if (interactive && !keyguardLocked) onUnlock()
    }

    fun onUnlock() {
        synchronized(prefs) {
            if (write(ClockCycleRecord(armed = true))) {
                synchronized(failedWrites) { failedWrites.remove(prefs) }
            }
        }
    }

    fun suppress() {
        synchronized(prefs) {
            write(read().copy(suppressed = true, attempted = true).withoutOffer())
        }
    }

    fun invalidateOffer() {
        synchronized(prefs) { write(read().withoutOffer()) }
    }

    /** An empty result means no activation was authorised; callers must skip every activation path. */
    fun offer(): String = synchronized(prefs) {
        val record = read()
        val now = elapsedRealtime()
        val boot = currentBoot()
        if (writeFailed || record.suppressed || boot < 0 || now < 0L ||
            now > Long.MAX_VALUE - ClockCycleRecord.OFFER_WINDOW_MS
        ) {
            write(record.withoutOffer())
            return ""
        }
        val token = tokenGenerator()
        if (token.isEmpty()) {
            write(record.withoutOffer())
            return ""
        }
        val offered = record.copy(
            offerToken = token,
            offerUntil = now + ClockCycleRecord.OFFER_WINDOW_MS,
            offerBoot = boot,
        )
        if (write(offered)) token else ""
    }

    fun consume(token: String?): Boolean = synchronized(prefs) {
        val record = read()
        val valid = !writeFailed && record.acceptsOffer(token, elapsedRealtime(), currentBoot())
        // Invalid/missing tokens also burn the pending offer, as in the original contract.
        val cleared = write(record.withoutOffer())
        valid && cleared
    }

    private fun currentBoot(): Int = try {
        bootProvider().takeIf { it >= 0 } ?: -1
    } catch (_: RuntimeException) {
        // Includes Settings.Global permission/provider failures; -1 never matches a valid boot.
        -1
    }

    private fun read(): ClockCycleRecord = ClockCycleRecord(
        armed = prefs.getBoolean("armed", false),
        attempted = prefs.getBoolean("attempted", false),
        suppressed = prefs.getBoolean("suppressed", false),
        lockedAt = prefs.getLong("lock_at", -1L),
        lockBoot = prefs.getInt("lock_boot", -1),
        offerToken = prefs.getString("offer", null),
        offerUntil = prefs.getLong("offer_until", 0L),
        offerBoot = prefs.getInt("offer_boot", -1),
    )

    private fun write(record: ClockCycleRecord): Boolean {
        val committed = try {
            prefs.edit().apply {
                putBoolean("armed", record.armed)
                putBoolean("attempted", record.attempted)
                putBoolean("suppressed", record.suppressed)
                if (record.lockedAt >= 0L) {
                    putLong("lock_at", record.lockedAt)
                    putInt("lock_boot", record.lockBoot)
                } else {
                    remove("lock_at")
                    remove("lock_boot")
                }
                if (record.offerToken != null) {
                    putString("offer", record.offerToken)
                    putLong("offer_until", record.offerUntil)
                    putInt("offer_boot", record.offerBoot)
                } else {
                    remove("offer")
                    remove("offer_until")
                    remove("offer_boot")
                }
            }.commit()
        } catch (_: RuntimeException) {
            false
        }
        if (!committed) synchronized(failedWrites) { failedWrites[prefs] = true }
        return committed
    }

    private val writeFailed: Boolean
        get() = synchronized(failedWrites) { failedWrites.containsKey(prefs) }

    private companion object {
        // SharedPreferences is shared per file in an Android process. A wrapper-local flag is unsafe.
        val failedWrites = WeakHashMap<SharedPreferences, Boolean>()
    }
}
