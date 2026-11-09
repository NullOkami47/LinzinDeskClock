package com.linzin.linzindeskclock.display

import org.junit.Assert.*
import org.junit.Test

class ClockOrientationPolicyTest {
    @Test fun bookFoldablesKeepReverseLandscape() {
        assertEquals(LandscapeOrientation.REVERSE, clockOrientation(true, false, false, false))
    }
    @Test fun motorolaAndTallFoldablesFollowTheSensor() {
        assertEquals(LandscapeOrientation.SENSOR, clockOrientation(true, true, false, false))
        assertEquals(LandscapeOrientation.SENSOR, clockOrientation(true, false, true, false))
    }
    @Test fun ordinaryPhonesKeepTheirSensorOrientation() {
        assertEquals(LandscapeOrientation.SENSOR, clockOrientation(false, false, false, false))
        assertEquals(LandscapeOrientation.SENSOR, clockOrientation(false, false, true, false))
    }
    @Test fun flipUsesTheOppositeOfTheCurrentLandscapeRotation() {
        assertEquals(LandscapeOrientation.REVERSE, oppositeLandscapeOrientation(1))
        assertEquals(LandscapeOrientation.NORMAL, oppositeLandscapeOrientation(3))
    }
    @Test fun recreatingAnAlreadyFlippedClockKeepsItsSavedOrientation() {
        repeat(3) {
            assertEquals(LandscapeOrientation.REVERSE,
                clockOrientation(true, true, true, true, LandscapeOrientation.REVERSE))
            assertEquals(LandscapeOrientation.NORMAL, clockOrientation(true, false, false, true))
        }
    }
}
