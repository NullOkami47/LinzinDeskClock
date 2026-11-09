package com.linzin.linzindeskclock.updates

import org.junit.Assert.*
import org.junit.Test

class AppUpdateTest {
    @Test fun comparesVersionComponentsNumerically() {
        assertTrue(isVersionNewer("2.0.10", "2.0.4"))
        assertTrue(isVersionNewer("2.1.0", "2.0.99"))
        assertTrue(isVersionNewer("3.0.0", "2.99.99"))
    }
    @Test fun equivalentVersionsDoNotAdvertiseUpdates() {
        assertFalse(isVersionNewer("2.0.4", "2.0.4"))
        assertFalse(isVersionNewer("2.0.4.0", "2.0.4"))
    }
    @Test fun acceptsGithubVersionPrefixesAndWhitespace() {
        assertTrue(isVersionNewer(" v2.0.5 ", "V2.0.4"))
        assertFalse(isVersionNewer("V2.0.4", " v2.0.4 "))
    }
    @Test fun upstreamOnePointZeroReleaseNeverDowngradesV2() {
        assertFalse(isVersionNewer("v1.0.2", "2.0.4"))
        assertFalse(isVersionNewer("2.0.3", "2.0.4"))
    }
}
