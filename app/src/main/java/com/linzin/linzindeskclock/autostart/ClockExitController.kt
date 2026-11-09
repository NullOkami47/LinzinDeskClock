package com.linzin.linzindeskclock.autostart

import android.app.NotificationManager
import android.content.Context
import com.linzin.linzindeskclock.service.AutoClockService

class ClockExitController(private val context: Context, private val finishSession: () -> Unit) {
    private var exited = false
    fun exit() {
        if(exited) return
        exited = true
        ClockCycleStore(context).suppress()
        context.getSystemService(NotificationManager::class.java).cancel(AutoClockService.OFFER_ID)
        finishSession()
    }
}
