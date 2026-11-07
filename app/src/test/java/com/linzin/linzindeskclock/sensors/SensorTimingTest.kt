package com.linzin.linzindeskclock.sensors
import org.junit.Assert.*
import org.junit.Test
class SensorTimingTest {
    @Test fun dataGapResetsEvenWhenCallbacksAreCloseTogether() {
        val timing=SensorTiming()
        assertEquals(SensorContinuity.CONTINUOUS,timing.classify(1_000_000_000,1_500_000_000))
        assertEquals(SensorContinuity.NEW_SEGMENT,timing.classify(1_600_000_000,1_600_000_000))
    }
    @Test fun staleFutureAndDuplicateSamplesAreRejected() {
        val timing=SensorTiming()
        assertEquals(SensorContinuity.REJECT,timing.classify(1_000_000_000,1_500_000_001))
        assertEquals(SensorContinuity.REJECT,timing.classify(2_000_000_000,1_500_000_000))
        assertEquals(SensorContinuity.CONTINUOUS,timing.classify(2_000_000_000,2_000_000_000))
        assertEquals(SensorContinuity.REJECT,timing.classify(2_000_000_000,2_000_000_001))
    }
}
