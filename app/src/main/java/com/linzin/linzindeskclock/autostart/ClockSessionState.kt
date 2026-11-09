package com.linzin.linzindeskclock.autostart

import androidx.lifecycle.ViewModel

/** Retained only across configuration changes. Process recreation requires a new one-use offer. */
class ClockSessionState : ViewModel() {
    var authorisedCycle: ClockLockWindow? = null
    var automatic: Boolean = false
    var presented: Boolean = false
}
