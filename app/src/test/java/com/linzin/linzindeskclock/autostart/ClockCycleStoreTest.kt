package com.linzin.linzindeskclock.autostart

import android.content.SharedPreferences
import com.linzin.linzindeskclock.layout.InMemorySharedPreferences
import org.junit.Assert.*
import org.junit.Test

/** Real store and serialised state; only the Android storage/clock/boot boundaries are supplied. */
class ClockCycleStoreTest {
    @Test fun aFreshStoreNeedsAnObservedUnlock() {
        val f = Fixture()
        f.store().noteLock()
        assertEquals(0L, f.store().remainingWindowMs())
        assertFalse(f.store().claimAttempt())
    }

    @Test fun lockTimeSurvivesWrapperAndStorageReconstruction() {
        val f = Fixture()
        f.lock()
        f.now = 2_250L
        val restored = f.restoredStore()
        assertEquals(3_750L, restored.remainingWindowMs())
        assertTrue(restored.claimAttempt())
    }

    @Test fun duplicateLockNeverExtendsTheOriginalFiveSeconds() {
        val f = Fixture()
        f.lock()
        f.now = 5_999L
        f.store().noteLock()
        assertEquals(1L, f.store().remainingWindowMs())
        f.now = 6_000L
        f.store().noteLock()
        assertEquals(0L, f.store().remainingWindowMs())
        assertFalse(f.store().claimAttempt())
    }

    @Test fun dreamMustClaimStrictlyBeforeFiveSeconds() {
        for ((age, allowed) in listOf(4_999L to true, 5_000L to false, 5_001L to false)) {
            val f = Fixture()
            f.lock()
            f.now = 1_000L + age
            assertEquals("age=" + age, allowed, f.store().claimAttempt())
        }
    }

    @Test fun aClaimSurvivesStorageReconstruction() {
        val f = Fixture()
        f.lock()
        assertTrue(f.store().claimAttempt())
        assertFalse(f.restoredStore().claimAttempt())
    }

    @Test fun twoExistingWrappersCanClaimOnlyOnce() {
        val f = Fixture()
        val monitor = f.store()
        val dream = f.store()
        f.lock()
        assertTrue(monitor.claimAttempt())
        assertFalse(dream.claimAttempt())
    }

    @Test fun manualExitRemainsSuppressedUntilObservedUnlock() {
        val f = Fixture()
        f.lock()
        val token = f.store().offer()
        f.store().suppress()
        val restarted = f.restoredStore()
        assertTrue(restarted.suppressed)
        assertFalse(restarted.claimAttempt())
        assertEquals("", restarted.offer())
        assertFalse(restarted.consume(token))
        restarted.noteLock()
        assertTrue(restarted.suppressed)
        restarted.onUnlock()
        assertFalse(restarted.suppressed)
        restarted.noteLock()
        assertTrue(restarted.claimAttempt())
    }

    @Test fun unlockClearsWindowAttemptSuppressionAndOfferBeforeRearming() {
        val f = Fixture()
        f.lock()
        assertTrue(f.store().claimAttempt())
        val token = f.store().offer()
        f.store().suppress()
        f.store().onUnlock()
        assertFalse(f.store().suppressed)
        assertEquals(0L, f.store().remainingWindowMs())
        assertFalse(f.store().consume(token))
        f.now = 9_000L
        f.store().noteLock()
        assertEquals(5_000L, f.store().remainingWindowMs())
        assertTrue(f.store().claimAttempt())
    }

    @Test fun aBootChangeInvalidatesTheOldLockUntilAnotherUnlock() {
        val f = Fixture()
        f.lock()
        f.boot = 8
        f.store().noteLock()
        assertEquals(0L, f.store().remainingWindowMs())
        assertFalse(f.store().claimAttempt())
        f.lock()
        assertTrue(f.store().claimAttempt())
    }

    @Test fun matchingUnknownBootsNeverOpenALockWindow() {
        val f = Fixture()
        f.boot = -1
        f.lock()
        assertEquals(0L, f.store().remainingWindowMs())
        assertFalse(f.store().claimAttempt())
    }

    @Test fun aLockObservedWithUnknownBootCannotBeRevivedByBootRecovery() {
        val f = Fixture()
        f.boot = -1
        f.lock()
        f.boot = 7
        f.store().noteLock()
        assertEquals(0L, f.store().remainingWindowMs())
        assertFalse(f.store().claimAttempt())
    }

    @Test fun anUnreadableBootProviderFailsClosedWithoutThrowing() {
        val f = Fixture()
        val token = f.store().offer()
        f.bootReadable = false
        f.lock()
        assertEquals(0L, f.store().remainingWindowMs())
        assertFalse(f.store().claimAttempt())
        assertEquals("", f.store().offer())
        assertFalse(f.store().consume(token))
    }

    @Test fun aFutureLockTimestampIsRejected() {
        val f = Fixture()
        f.lock()
        f.now = 999L
        assertEquals(0L, f.store().remainingWindowMs())
        assertFalse(f.store().claimAttempt())
    }

    @Test fun elapsedZeroIsAValidLockTime() {
        val f = Fixture()
        f.now = 0L
        f.lock()
        assertEquals(5_000L, f.store().remainingWindowMs())
        assertTrue(f.store().claimAttempt())
    }

    @Test fun offerSurvivesStorageReconstructionWithinTheSameBoot() {
        val f = Fixture()
        val token = f.store().offer()
        assertFalse(token.isEmpty())
        f.now = 30_999L
        assertTrue(f.restoredStore().consume(token))
    }

    @Test fun offerCarriesItsIssuingBoot() {
        val f = Fixture()
        f.store().offer()
        assertEquals(7, f.prefs.getInt("offer_boot", -1))
    }

    @Test fun aDifferentBootRejectsAnOtherwiseLiveOffer() {
        val f = Fixture()
        val token = f.store().offer()
        f.boot = 8
        assertFalse(f.restoredStore().consume(token))
    }

    @Test fun aLegacyOfferWithoutBootIsRejected() {
        val f = Fixture(mapOf("offer" to "legacy", "offer_until" to 31_000L))
        assertFalse(f.store().consume("legacy"))
    }

    @Test fun matchingUnknownBootsNeverAuthoriseAnOffer() {
        val f = Fixture(mapOf("offer" to "old", "offer_until" to 31_000L, "offer_boot" to -1))
        f.boot = -1
        assertFalse(f.store().consume("old"))
        assertEquals("", f.store().offer())
    }

    @Test fun unknownCurrentBootRejectsAKnownBootOffer() {
        val f = Fixture()
        val token = f.store().offer()
        f.boot = -1
        assertFalse(f.store().consume(token))
    }

    @Test fun anOfferExpiresAtExactlyThirtySeconds() {
        for ((age, allowed) in listOf(0L to true, 29_999L to true, 30_000L to false, 30_001L to false)) {
            val f = Fixture()
            val token = f.store().offer()
            f.now = 1_000L + age
            assertEquals("age=" + age, allowed, f.store().consume(token))
        }
    }

    @Test fun anOfferIssuedInTheFutureIsRejected() {
        val f = Fixture()
        val token = f.store().offer()
        f.now = 999L
        assertFalse(f.store().consume(token))
    }

    @Test fun anImplausiblyDistantDeadlineIsRejected() {
        val f = Fixture(mapOf("offer" to "far", "offer_until" to Long.MAX_VALUE, "offer_boot" to 7))
        assertFalse(f.store().consume("far"))
    }

    @Test fun deadlineOverflowDoesNotIssueAToken() {
        val f = Fixture()
        f.now = Long.MAX_VALUE - 29_999L
        assertEquals("", f.store().offer())
    }

    @Test fun negativeElapsedTimeCannotIssueOrConsumeAnOffer() {
        val f = Fixture(mapOf("offer" to "old", "offer_until" to 10L, "offer_boot" to 7))
        f.now = -1L
        assertFalse(f.store().consume("old"))
        assertEquals("", f.store().offer())
    }

    @Test fun anEmptyGeneratedTokenCannotBecomeAnOffer() {
        val f = Fixture()
        val old = f.store().offer()
        val brokenGenerator = ClockCycleStore(f.prefs, { f.now }, { f.boot }, { "" })
        assertEquals("", brokenGenerator.offer())
        assertFalse(f.store().consume(old))
        assertFalse(brokenGenerator.consume(""))
    }

    @Test fun twoExistingConsumersGetExactlyOneSuccessOnTheMainThread() {
        val f = Fixture()
        val activity = f.store()
        val notification = f.store()
        val token = f.store().offer()
        assertTrue(activity.consume(token))
        assertFalse(notification.consume(token))
        assertFalse(f.restoredStore().consume(token))
    }

    @Test fun wrongOrMissingTokensStillInvalidateThePendingOffer() {
        for (presented in listOf(null, "wrong", "")) {
            val f = Fixture()
            val token = f.store().offer()
            assertFalse(f.store().consume(presented))
            assertFalse(f.store().consume(token))
        }
    }

    @Test fun aNewOfferReplacesThePreviousOne() {
        val f = Fixture()
        val old = f.store().offer()
        val fresh = f.store().offer()
        assertNotEquals(old, fresh)
        assertTrue(f.store().consume(fresh))
        assertFalse(f.store().consume(old))
    }

    @Test fun explicitInvalidationRemovesEveryOfferField() {
        val f = Fixture()
        val token = f.store().offer()
        f.store().invalidateOffer()
        assertFalse(f.prefs.contains("offer"))
        assertFalse(f.prefs.contains("offer_until"))
        assertFalse(f.prefs.contains("offer_boot"))
        assertFalse(f.store().consume(token))
    }

    @Test fun failedOfferReturnsEmptyAndQuarantinesRecreatedWrappers() {
        for (failure in CommitFailure.entries) {
            val f = Fixture()
            val old = f.store().offer()
            f.prefs.failure = failure
            assertEquals(failure.name, "", f.store().offer())
            f.prefs.failure = null
            assertEquals(failure.name, "", f.store().offer())
            assertFalse(failure.name, f.store().consume(old))
        }
    }

    @Test fun failedConsumptionNeverAuthorisesOrAllowsRetryThroughAnotherWrapper() {
        for (failure in CommitFailure.entries) {
            val f = Fixture()
            val token = f.store().offer()
            f.prefs.failure = failure
            assertFalse(failure.name, f.store().consume(token))
            f.prefs.failure = null
            assertFalse(failure.name, f.store().consume(token))
        }
    }

    @Test fun failedInvalidationCannotLeaveAnOfferUsable() {
        for (failure in CommitFailure.entries) {
            val f = Fixture()
            val token = f.store().offer()
            f.prefs.failure = failure
            f.store().invalidateOffer()
            f.prefs.failure = null
            assertFalse(failure.name, f.store().consume(token))
        }
    }

    @Test fun failedSuppressionStillBlocksEveryRecreatedWrapper() {
        for (failure in CommitFailure.entries) {
            val f = Fixture()
            f.lock()
            val token = f.store().offer()
            f.prefs.failure = failure
            f.store().suppress()
            f.prefs.failure = null
            assertTrue(failure.name, f.store().suppressed)
            assertFalse(failure.name, f.store().claimAttempt())
            assertEquals(failure.name, "", f.store().offer())
            assertFalse(failure.name, f.store().consume(token))
        }
    }

    @Test fun failedUnlockCannotRearmEvenIfPreferencesChangedInMemory() {
        for (failure in CommitFailure.entries) {
            val f = Fixture()
            f.store().suppress()
            f.prefs.failure = failure
            f.store().onUnlock()
            f.prefs.failure = null
            f.store().noteLock()
            assertTrue(failure.name, f.store().suppressed)
            assertFalse(failure.name, f.store().claimAttempt())
        }
    }

    @Test fun failedLockCannotBeRetriedToStartALaterWindow() {
        for (failure in CommitFailure.entries) {
            val f = Fixture()
            f.store().onUnlock()
            f.prefs.failure = failure
            f.store().noteLock()
            f.prefs.failure = null
            f.now = 2_000L
            f.store().noteLock()
            assertEquals(failure.name, 0L, f.store().remainingWindowMs())
            assertFalse(failure.name, f.store().claimAttempt())
        }
    }

    @Test fun failedClaimCannotBeRetriedThroughAnotherWrapper() {
        for (failure in CommitFailure.entries) {
            val f = Fixture()
            f.lock()
            f.prefs.failure = failure
            assertFalse(failure.name, f.store().claimAttempt())
            f.prefs.failure = null
            assertFalse(failure.name, f.store().claimAttempt())
        }
    }

    @Test fun onlyASuccessfulUnlockRecoversFromAFailedWrite() {
        val f = Fixture()
        f.prefs.failure = CommitFailure.FALSE_BEFORE_MEMORY
        f.store().suppress()
        f.prefs.failure = null
        f.store().invalidateOffer()
        assertTrue(f.store().suppressed)
        f.lock()
        assertFalse(f.store().suppressed)
        assertTrue(f.store().claimAttempt())
        assertTrue(f.store().consume(f.store().offer()))
    }

    @Test fun aWriteFailureDoesNotQuarantineAnotherPreferencesFile() {
        val broken = Fixture()
        broken.prefs.failure = CommitFailure.FALSE_BEFORE_MEMORY
        broken.store().suppress()
        val healthy = Fixture()
        healthy.lock()
        assertTrue(healthy.store().claimAttempt())
        assertTrue(healthy.store().consume(healthy.store().offer()))
    }

    private class Fixture(initial: Map<String, Any> = emptyMap()) {
        val prefs = CommitControlledPreferences(InMemorySharedPreferences(initial))
        var now = 1_000L
        var boot = 7
        var bootReadable = true
        private var tokens = 0

        fun store(preferences: SharedPreferences = prefs) = ClockCycleStore(
            preferences,
            { now },
            { if (!bootReadable) throw SecurityException("BOOT_COUNT unavailable") else boot },
            { "offer-" + ++tokens },
        )

        fun lock() {
            store().onUnlock()
            store().noteLock()
        }

        fun restoredStore(): ClockCycleStore = store(
            InMemorySharedPreferences(prefs.all.mapValues { requireNotNull(it.value) }),
        )
    }

    private enum class CommitFailure { FALSE_BEFORE_MEMORY, FALSE_AFTER_MEMORY, THROW_BEFORE_MEMORY, THROW_AFTER_MEMORY }

    /** Models failed durable writes both before and after SharedPreferences changes its live map. */
    private class CommitControlledPreferences(private val backing: SharedPreferences) : SharedPreferences by backing {
        var failure: CommitFailure? = null
        override fun edit(): SharedPreferences.Editor {
            val editor = backing.edit()
            return object : SharedPreferences.Editor by editor {
                override fun putString(key: String?, value: String?) = apply { editor.putString(key, value) }
                override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { editor.putStringSet(key, values) }
                override fun putInt(key: String?, value: Int) = apply { editor.putInt(key, value) }
                override fun putLong(key: String?, value: Long) = apply { editor.putLong(key, value) }
                override fun putFloat(key: String?, value: Float) = apply { editor.putFloat(key, value) }
                override fun putBoolean(key: String?, value: Boolean) = apply { editor.putBoolean(key, value) }
                override fun remove(key: String?) = apply { editor.remove(key) }
                override fun clear() = apply { editor.clear() }
                override fun apply(): Unit = error("Clock cycle writes must use synchronous commit")
                override fun commit(): Boolean {
                    when (failure) {
                        null -> return editor.commit()
                        CommitFailure.FALSE_AFTER_MEMORY, CommitFailure.THROW_AFTER_MEMORY -> editor.apply()
                        else -> Unit
                    }
                    if (failure == CommitFailure.THROW_BEFORE_MEMORY || failure == CommitFailure.THROW_AFTER_MEMORY) {
                        throw IllegalStateException("Simulated write failure")
                    }
                    return false
                }
            }
        }
    }
}
