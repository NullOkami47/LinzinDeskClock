package com.linzin.linzindeskclock.autostart

import org.junit.Assert.*
import org.junit.Test

class BackgroundClockLaunchPolicyTest {
    private fun decision(enabled:Boolean=true,consent:Boolean=true,permission:Boolean=true,cycle:Boolean=true,interactive:Boolean=false,locked:Boolean=true) =
        BackgroundClockLaunchPolicy.canLaunch(enabled,consent,permission,cycle,interactive,locked)
    @Test fun locksFromOtherAppsNeedNoForegroundClockHost() { assertTrue(decision()) }
    @Test fun unlockedActivePhoneCannotBeInterrupted() { assertFalse(decision(interactive=true,locked=false)) }
    @Test fun lockedInteractiveScreenIsStillAllowed() { assertTrue(decision(interactive=true,locked=true)) }
    @Test fun explicitOptInAndSystemAccessAreBothRequired() {
        assertFalse(decision(consent=false)); assertFalse(decision(permission=false)); assertFalse(decision(enabled=false))
    }
    @Test fun expiredSuppressedOrChangedCycleCannotStart() { assertFalse(decision(cycle=false)) }
}
