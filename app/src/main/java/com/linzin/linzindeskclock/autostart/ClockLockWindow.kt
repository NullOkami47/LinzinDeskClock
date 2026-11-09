package com.linzin.linzindeskclock.autostart

data class ClockLockWindow(val boot: Int, val lockedAt: Long) {
    val deadline: Long get() = lockedAt + ClockActivationStateMachine.WINDOW_MS
}
