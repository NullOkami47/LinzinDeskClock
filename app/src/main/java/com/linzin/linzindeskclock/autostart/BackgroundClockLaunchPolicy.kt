package com.linzin.linzindeskclock.autostart

object BackgroundClockLaunchPolicy {
    fun canLaunch(enabled: Boolean, consent: Boolean, specialAccess: Boolean, liveCycle: Boolean, interactive: Boolean, keyguardLocked: Boolean): Boolean =
        enabled && consent && specialAccess && liveCycle && (!interactive || keyguardLocked)
}
