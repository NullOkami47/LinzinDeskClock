package com.linzin.linzindeskclock.autostart

/** Process-local live-scan coordination, used exclusively on the Android main looper. */
object ClockDreamBridge {
    val scans = PendingLockScan()
}
