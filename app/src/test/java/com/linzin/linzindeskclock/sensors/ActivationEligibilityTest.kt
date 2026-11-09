package com.linzin.linzindeskclock.sensors

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class ActivationEligibilityTest {
    private val landscape = GravityOrientation.measure(6.934f, 0f, 6.934f)
    @Test fun gravityMeasuresAngleFromDeskNotVertical() {
        assertEquals(0f, GravityOrientation.measure(0f,0f,9.806f).tiltDegrees, .01f)
        assertEquals(45f, landscape.tiltDegrees, .01f)
        assertEquals(90f, GravityOrientation.measure(9.806f,0f,0f).tiltDegrees,.01f)
    }
    @Test fun landscapeWorksBothWaysAndOnNaturalLandscapeDisplays() {
        assertTrue(landscape.isLandscape)
        assertTrue(GravityOrientation.measure(-6.934f,0f,6.934f).isLandscape)
        assertFalse(GravityOrientation.measure(0f,6.934f,6.934f).isLandscape)
        assertTrue(GravityOrientation.measure(0f,6.934f,6.934f,naturalPortrait=false).isLandscape)
    }
    @Test fun ambiguousOrInvalidGravityIsRejected() {
        assertFalse(GravityOrientation.measure(5.66f,5.66f,5.66f).isLandscape)
        assertFalse(GravityOrientation.measure(Float.NaN,0f,9f).reliable)
        assertFalse(GravityOrientation.measure(0f,0f,0f).reliable)
        assertFalse(GravityOrientation.measure(20f,0f,20f).reliable)
        assertFalse(GravityOrientation.measure(6.934f,0f,-6.934f).reliable)
    }
    @Test fun powerSourceIsIndependentOfFullBatteryState() {
        assertTrue(ActivationEligibility.accepts(false,landscape,4,ChargingMode.WIRELESS_ONLY))
        assertFalse(ActivationEligibility.accepts(false,landscape,1,ChargingMode.WIRELESS_ONLY))
        assertTrue(ActivationEligibility.accepts(false,landscape,1,ChargingMode.ANY_POWER))
        assertTrue(ActivationEligibility.accepts(false,landscape,2,ChargingMode.ANY_POWER))
        assertTrue(ActivationEligibility.accepts(false,landscape,8,ChargingMode.ANY_POWER))
        assertFalse(ActivationEligibility.accepts(false,landscape,16,ChargingMode.ANY_POWER))
        assertTrue(ActivationEligibility.accepts(false,landscape,0,ChargingMode.NOT_REQUIRED))
    }
    @Test fun foldablePortraitAndOutOfRangeTiltNeverEligible() {
        assertFalse(ActivationEligibility.accepts(true,landscape,4,ChargingMode.WIRELESS_ONLY))
        for(angle in listOf(20f,80f)) assertFalse(ActivationEligibility.accepts(false,TiltReading(angle,true,true),4,ChargingMode.WIRELESS_ONLY))
        for(angle in listOf(30f,75f)) assertTrue(ActivationEligibility.accepts(false,TiltReading(angle,true,true),4,ChargingMode.WIRELESS_ONLY))
        assertFalse(ActivationEligibility.accepts(false,TiltReading(45f,false,true),4,ChargingMode.WIRELESS_ONLY))
    }
}
