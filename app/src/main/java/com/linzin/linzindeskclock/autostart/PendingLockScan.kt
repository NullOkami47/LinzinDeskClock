package com.linzin.linzindeskclock.autostart

/** Main-loop confined handoff of a live scan. No replay, retry or cached eligibility. */
class PendingLockScan {
    interface Waiter {
        fun showEligibleClock(token: String): Boolean
        fun scanUnavailable()
    }
    enum class Delivery { ACCEPTED, FALLBACK, ABORTED }
    private var window: ClockLockWindow? = null
    private var waiter: Waiter? = null
    fun begin(value: ClockLockWindow) {
        window?.let(::cancel)
        window = value
    }
    fun subscribe(value: ClockLockWindow, target: Waiter): Boolean {
        if(window != value || waiter != null) return false
        waiter = target
        return true
    }
    fun detach(target: Waiter) { if(waiter === target) waiter = null }
    fun cancel(value: ClockLockWindow) {
        if(window != value) return
        val target = waiter
        window = null; waiter = null
        target?.scanUnavailable()
    }
    fun deliver(value: ClockLockWindow, token: String, stillValid: () -> Boolean): Delivery {
        if(window != value) return Delivery.ABORTED
        val target = waiter
        window = null; waiter = null
        if(token.isEmpty() || !stillValid()) { target?.scanUnavailable(); return Delivery.ABORTED }
        if(target != null && target.showEligibleClock(token)) return Delivery.ACCEPTED
        target?.scanUnavailable()
        return if(stillValid()) Delivery.FALLBACK else Delivery.ABORTED
    }
}
