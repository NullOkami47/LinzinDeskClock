package com.linzin.linzindeskclock.calendar

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.junit.Assume.assumeTrue
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** No real calendar data, permission mutation, keyguard dismissal or external activity is used. */
internal class CalendarFixtureContext(base: Context) : ContextWrapper(base) {
    var permissionGranted = true
    var keyguardAvailable = true
    var startFailure: RuntimeException? = null
    val startedIntents = mutableListOf<Intent>()

    override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
        if (permission == Manifest.permission.READ_CALENDAR) {
            if (permissionGranted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        } else super.checkPermission(permission, pid, uid)

    override fun getSystemService(name: String): Any? =
        if (name == Context.KEYGUARD_SERVICE && !keyguardAvailable) null else super.getSystemService(name)

    override fun startActivity(intent: Intent) {
        startedIntents += Intent(intent)
        startFailure?.let { throw it }
    }
}

/** Checked before the Compose activity rule, so the suite cannot start a host on a phone. */
internal class CalendarEmulatorOnlyRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            assumeTrue("Calendar instrumentation is for an authorised emulator only",
                Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
            base.evaluate()
        }
    }
}
