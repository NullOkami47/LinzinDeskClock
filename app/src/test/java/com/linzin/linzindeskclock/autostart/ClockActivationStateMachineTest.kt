package com.linzin.linzindeskclock.autostart

import org.junit.Assert.*
import org.junit.Test

class ClockActivationStateMachineTest {
    private fun armed() = ClockActivationStateMachine().apply { onUnlock() }
    private fun stable(s: ClockActivationStateMachine, start: Long = 0): Boolean {
        var activated = false
        for (t in start..start + 1500 step 100) activated = s.sample(t, true) || activated
        return activated
    }
    @Test fun eligibleSamplesWithoutLockNeverActivate() { assertFalse(stable(armed())) }
    @Test fun coldStartWhileLockedDoesNotInventTransition() {
        val s = ClockActivationStateMachine(); assertFalse(s.onLock(0)); assertFalse(stable(s))
    }
    @Test fun lockStartsOneBoundedScan() {
        val s = armed(); assertTrue(s.onLock(0)); assertFalse(s.onLock(100)); assertTrue(stable(s))
        assertFalse(s.sample(1600, true)); assertFalse(s.onLock(1700))
    }
    @Test fun invalidReadingResetsStability() {
        val s = armed(); s.onLock(0)
        for(t in 0L..1400 step 100) assertFalse(s.sample(t, true))
        assertFalse(s.sample(1500, false)); assertFalse(s.sample(1600, true))
        assertTrue(stable(s, 1700))
    }
    @Test fun staleSensorStreamCannotCountAsContinuous() {
        val s = armed(); s.onLock(0); assertFalse(s.sample(0, true)); assertFalse(s.sample(1500, true))
        assertTrue(stable(s, 1600))
    }
    @Test fun deadlineIsExclusiveAndNeverRetries() {
        val s = armed(); s.onLock(0)
        for(t in 3500L..4900 step 100) assertFalse(s.sample(t, true))
        assertFalse(s.sample(5000, true)); assertEquals(ActivationState.WAITING_FOR_UNLOCK, s.state)
        assertFalse(s.onLock(5001)); assertFalse(s.sample(6500, true))
    }
    @Test fun timeoutWithoutSensorEventsEndsScan() {
        val s = armed(); s.onLock(0); s.expire(5000)
        assertEquals(ActivationState.WAITING_FOR_UNLOCK, s.state)
    }
    @Test fun unlockCancelsAndRearmsForNextLockOnly() {
        val s = armed(); s.onLock(0); s.sample(0, true); s.onUnlock()
        assertFalse(stable(s, 100)); assertTrue(s.onLock(2000)); assertTrue(stable(s, 2000))
    }
    @Test fun manualExitSuppressesDuplicateLockAndDreamUntilUnlock() {
        val s = armed(); s.onLock(0); assertTrue(stable(s)); s.manualExit()
        assertEquals(ActivationState.SUPPRESSED_UNTIL_UNLOCK, s.state)
        assertFalse(s.onLock(2000)); assertFalse(stable(s, 2000))
        s.onUnlock(); assertTrue(s.onLock(4000)); assertTrue(stable(s, 4000))
    }
    @Test fun cancellationConsumesAttempt() {
        val s = armed(); s.onLock(0); s.cancel(); assertFalse(stable(s)); assertFalse(s.onLock(1000))
    }
    @Test fun timeGoingBackwardsFailsClosed() {
        val s = armed(); s.onLock(100); s.sample(200, true); assertFalse(s.sample(150, true))
        assertEquals(ActivationState.WAITING_FOR_UNLOCK, s.state)
    }
    @Test fun delayedDreamCannotExtendOriginalLockDeadline() {
        val s=armed(); assertTrue(s.onLock(4000,1000))
        for(time in 4000L..4900 step 100) assertFalse(s.sample(time,true))
        assertFalse(s.sample(5000,true))
        assertEquals(ActivationState.WAITING_FOR_UNLOCK,s.state)
    }
    @Test fun expiredLockWindowCannotStartScan() { assertFalse(armed().onLock(5000,0)) }
}
