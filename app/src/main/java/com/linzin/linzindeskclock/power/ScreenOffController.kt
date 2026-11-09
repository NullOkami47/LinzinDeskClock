package com.linzin.linzindeskclock.power

import android.app.NotificationManager
import android.content.Context
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import com.linzin.linzindeskclock.R
import com.linzin.linzindeskclock.autostart.ClockCycleStore
import com.linzin.linzindeskclock.service.AutoClockService

/** Each host releases its wake behaviour before the system lock, and then finishes. */
class ScreenOffController(
    private val context: Context,
    private val window: Window,
    private val prepareHost: () -> Unit,
    private val finishHost: () -> Unit,
) {
    private var requested = false

    fun request() {
        if (requested) return
        requested = true
        // Persist before SCREEN_OFF can reach the monitor. Its scan must not wake this cycle.
        try {
            ClockCycleStore(context).suppress()
            context.getSystemService(NotificationManager::class.java).cancel(AutoClockService.OFFER_ID)
            prepareHost()
            @Suppress("DEPRECATION")
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            val locked = ScreenOffAccess.lockNow(context)
            if (!locked) Toast.makeText(context, R.string.screen_off_timeout_fallback, Toast.LENGTH_LONG).show()
        } finally {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            finishHost()
        }
    }
}
