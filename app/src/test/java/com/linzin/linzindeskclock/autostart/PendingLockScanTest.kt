package com.linzin.linzindeskclock.autostart

import org.junit.Assert.*
import org.junit.Test

class PendingLockScanTest {
    private val cycle=ClockLockWindow(10,1000)
    private class Dream(private val accept: () -> Boolean = {true}) : PendingLockScan.Waiter {
        var shown=0; var ended=0
        override fun showEligibleClock(token: String): Boolean { shown++; return accept() }
        override fun scanUnavailable() { ended++ }
    }
    @Test fun lateDreamCannotSubscribeAfterMonitorCompleted() {
        val broker=PendingLockScan(); broker.begin(cycle)
        assertEquals(PendingLockScan.Delivery.FALLBACK,broker.deliver(cycle,"offer"){true})
        assertFalse(broker.subscribe(cycle,Dream()))
    }
    @Test fun cancellationEndsWaitingDreamExactlyOnce() {
        val broker=PendingLockScan(); val dream=Dream(); broker.begin(cycle)
        assertTrue(broker.subscribe(cycle,dream)); broker.cancel(cycle); broker.cancel(cycle)
        assertEquals(1,dream.ended); assertEquals(0,dream.shown)
    }
    @Test fun deadlineReachedBeforeDeliveryHasNoDisplayOrFallback() {
        val broker=PendingLockScan(); val dream=Dream(); broker.begin(cycle); broker.subscribe(cycle,dream)
        assertEquals(PendingLockScan.Delivery.ABORTED,broker.deliver(cycle,"offer"){false})
        assertEquals(0,dream.shown); assertEquals(1,dream.ended)
    }
    @Test fun deadlineReachedInsideDreamRejectionDoesNotFallBack() {
        var valid=true
        val broker=PendingLockScan(); val dream=Dream { valid=false; false }
        broker.begin(cycle); broker.subscribe(cycle,dream)
        assertEquals(PendingLockScan.Delivery.ABORTED,broker.deliver(cycle,"offer"){valid})
    }
    @Test fun acceptedDreamOwnsTheOnlyDelivery() {
        val broker=PendingLockScan(); val dream=Dream(); broker.begin(cycle); broker.subscribe(cycle,dream)
        assertEquals(PendingLockScan.Delivery.ACCEPTED,broker.deliver(cycle,"offer"){true})
        assertEquals(PendingLockScan.Delivery.ABORTED,broker.deliver(cycle,"offer"){true})
        assertEquals(1,dream.shown); assertEquals(0,dream.ended)
    }
    @Test fun oldCycleAndOldHostCannotAffectReplacement() {
        val broker=PendingLockScan(); val old=Dream(); val next=Dream(); val newCycle=ClockLockWindow(10,9000)
        broker.begin(cycle); broker.subscribe(cycle,old); broker.begin(newCycle)
        assertTrue(broker.subscribe(newCycle,next)); broker.detach(old); broker.cancel(cycle)
        assertEquals(PendingLockScan.Delivery.ACCEPTED,broker.deliver(newCycle,"new"){true})
        assertEquals(1,old.ended); assertEquals(1,next.shown)
    }
}
