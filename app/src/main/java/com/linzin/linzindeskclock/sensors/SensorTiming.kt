package com.linzin.linzindeskclock.sensors

enum class SensorContinuity { CONTINUOUS, NEW_SEGMENT, REJECT }

class SensorTiming {
    private var previous = 0L
    fun reset() { previous = 0L }
    fun classify(timestampNanos: Long, receivedNanos: Long): SensorContinuity {
        if(timestampNanos <= 0 || timestampNanos <= previous || receivedNanos < timestampNanos ||
            receivedNanos - timestampNanos > MAX_GAP_NANOS) return SensorContinuity.REJECT
        val gap = previous > 0 && timestampNanos - previous > MAX_GAP_NANOS
        previous = timestampNanos
        return if(gap) SensorContinuity.NEW_SEGMENT else SensorContinuity.CONTINUOUS
    }
    companion object { const val MAX_GAP_NANOS = 500_000_000L }
}
