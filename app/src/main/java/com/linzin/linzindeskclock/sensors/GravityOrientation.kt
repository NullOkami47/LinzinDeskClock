package com.linzin.linzindeskclock.sensors

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

data class TiltReading(val tiltDegrees: Float, val isLandscape: Boolean, val reliable: Boolean) {
    companion object { val INVALID = TiltReading(0f, false, false) }
}

enum class ChargingMode { WIRELESS_ONLY, ANY_POWER, NOT_REQUIRED }

object GravityOrientation {
    /** Sensor axes describe the natural display, not the Activity's requested orientation. */
    fun measure(x: Float, y: Float, z: Float, naturalPortrait: Boolean = true, landscapeDominance: Float = 1.35f): TiltReading {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return TiltReading.INVALID
        val magnitude = sqrt(x*x + y*y + z*z)
        if (magnitude !in 7.5f..12f || z < 0) return TiltReading.INVALID
        val tilt = Math.toDegrees(acos((abs(z) / magnitude).coerceIn(0f,1f)).toDouble()).toFloat()
        val longEdgeDown = if (naturalPortrait) abs(x) else abs(y)
        val shortEdgeDown = if (naturalPortrait) abs(y) else abs(x)
        // Exclude diagonal/flat poses; both left and right landscape are valid.
        return TiltReading(tilt, longEdgeDown >= 3.5f && longEdgeDown > shortEdgeDown * landscapeDominance.coerceIn(1.2f, 1.35f), true)
    }
}

object ActivationEligibility {
    // BatteryManager plug bit values. Pure logic does not confuse FULL/CHARGING with power source.
    fun accepts(isFoldable: Boolean, tilt: TiltReading, plugged: Int, mode: ChargingMode): Boolean {
        val power = when(mode) {
            ChargingMode.WIRELESS_ONLY -> plugged and 4 != 0
            ChargingMode.ANY_POWER -> plugged and (1 or 2 or 4 or 8) != 0
            ChargingMode.NOT_REQUIRED -> true
        }
        return !isFoldable && tilt.reliable && tilt.isLandscape && tilt.tiltDegrees in 30f..75f && power
    }
}
