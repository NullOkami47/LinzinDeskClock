package com.linzin.linzindeskclock.power

import androidx.core.content.ContextCompat

import com.linzin.linzindeskclock.compat.clockSystemService

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.linzin.linzindeskclock.R

/** Optional, user-enabled admin. The manifest declares only the force-lock policy. */
class ScreenOffAdminReceiver : DeviceAdminReceiver()

object ScreenOffAccess {
    fun component(context: Context) = ComponentName(context, ScreenOffAdminReceiver::class.java)

    fun isSupported(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN) &&
            ContextCompat.getSystemService(context, DevicePolicyManager::class.java) != null

    fun isGranted(context: Context): Boolean = isSupported(context) && runCatching {
        context.clockSystemService(DevicePolicyManager::class.java).isAdminActive(component(context))
    }.getOrDefault(false)

    fun activationIntent(context: Context): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.screen_off_admin_explanation))

    /** False means normal system timeout must take over; never substitute a black overlay. */
    fun lockNow(context: Context): Boolean {
        if (!isGranted(context)) return false
        return try {
            context.clockSystemService(DevicePolicyManager::class.java).lockNow()
            true
        } catch (error: RuntimeException) {
            Log.w("ScreenOff", "System lock unavailable; releasing the clock window", error)
            false
        }
    }
}
