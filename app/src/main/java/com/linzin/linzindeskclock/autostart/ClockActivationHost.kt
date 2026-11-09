package com.linzin.linzindeskclock.autostart

import java.lang.ref.WeakReference

/** Only an existing app window may be offered automatic entry. Never launches another Activity. */
object ClockActivationHost {
    interface Host { fun enterLockedClock(): Boolean; fun onClockDeviceUnlocked() }
    private var host = WeakReference<Host>(null)
    fun attach(value: Host) { host = WeakReference(value) }
    fun detach(value: Host) { if(host.get() === value) host.clear() }
    fun current(): Host? = host.get()
    fun onDeviceUnlocked() { host.get()?.onClockDeviceUnlocked() }
}
