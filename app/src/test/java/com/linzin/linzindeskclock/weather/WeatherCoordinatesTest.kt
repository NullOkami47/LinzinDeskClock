package com.linzin.linzindeskclock.weather

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherCoordinatesTest {
    @Test fun acceptsRealLocationsIncludingEquatorAndPrimeMeridian() {
        assertTrue(isUsableWeatherCoordinates(25.03, 121.57))
        assertTrue(isUsableWeatherCoordinates(0.0, 30.0))
        assertTrue(isUsableWeatherCoordinates(51.5, 0.0))
    }

    @Test fun rejectsStartupZeroCoordinateAndInvalidSavedCoordinates() {
        assertFalse(isUsableWeatherCoordinates(0.0, 0.0))
        assertFalse(isUsableWeatherCoordinates(0.0001, -0.0001))
        assertFalse(isUsableWeatherCoordinates(Double.NaN, 121.57))
        assertFalse(isUsableWeatherCoordinates(25.03, Double.POSITIVE_INFINITY))
        assertFalse(isUsableWeatherCoordinates(91.0, 0.0))
        assertFalse(isUsableWeatherCoordinates(25.03, -181.0))
    }
}
