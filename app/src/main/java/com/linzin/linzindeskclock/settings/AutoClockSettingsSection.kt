package com.linzin.linzindeskclock.settings

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.linzin.linzindeskclock.R
import com.linzin.linzindeskclock.media.MediaNotificationAccess
import com.linzin.linzindeskclock.sensors.ChargingMode
import com.linzin.linzindeskclock.service.AutoClockService
import kotlinx.coroutines.delay

@Composable
fun AutoClockSettingsSection(hasHingeSensor: Boolean) {
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val settings=remember { AutoClockPreferences(context) }
    val foldable=hasHingeSensor || context.packageManager.hasSystemFeature("android.hardware.sensor.hinge_angle")
    val hasTilt=remember {
        val sensors=context.getSystemService(android.hardware.SensorManager::class.java)
        sensors.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY)!=null || sensors.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)!=null
    }
    var enabled by remember { mutableStateOf(settings.enabled) }
    var automatic by remember { mutableStateOf(settings.automaticLaunch) }
    var charging by remember { mutableStateOf(settings.chargingMode) }
    var revision by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(AutoClockService.running) }
    var startFailed by remember { mutableStateOf(false) }
    var explainAccess by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf(settings.lastActivation) }
    val startMonitor: () -> Unit = {
        if(settings.enabled) {
            startFailed=try { ContextCompat.startForegroundService(context,Intent(context,AutoClockService::class.java)); false }
                catch (_:RuntimeException) { true }
        }
        revision++
    }
    val notificationRequest=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startMonitor() }
    val noteExplicitEntry: () -> Unit = {
        com.linzin.linzindeskclock.autostart.ClockCycleStore(context).onExplicitUserEntry(
            context.getSystemService(android.os.PowerManager::class.java).isInteractive,
            context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
    }
    val enableMonitor: () -> Unit = {
        noteExplicitEntry()
        enabled=true; settings.enabled=true
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED &&
            !settings.preferences.getBoolean("auto_notification_asked",false)) {
            settings.preferences.edit().putBoolean("auto_notification_asked",true).apply()
            notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else startMonitor()
    }
    val latestStart by rememberUpdatedState(startMonitor)
    DisposableEffect(owner,settings) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_RESUME) {
            enabled=settings.enabled; automatic=settings.automaticLaunch; revision++
            if(settings.enabled && !AutoClockService.running) latestStart()
        } }
        val preferencesListener=SharedPreferences.OnSharedPreferenceChangeListener { _,_ -> lastResult=settings.lastActivation }
        owner.lifecycle.addObserver(observer)
        settings.preferences.registerOnSharedPreferenceChangeListener(preferencesListener)
        onDispose { owner.lifecycle.removeObserver(observer); settings.preferences.unregisterOnSharedPreferenceChangeListener(preferencesListener) }
    }
    LaunchedEffect(enabled,revision) { delay(500); running=AutoClockService.running }
    val manager=context.getSystemService(NotificationManager::class.java)
    val specialAccess=remember(revision) { BackgroundLaunchAccess.granted(context) }
    val notificationAccess=remember(revision) { MediaNotificationAccess.isGranted(context) }
    val noticesAllowed=remember(revision) { manager.areNotificationsEnabled() && manager.getNotificationChannel(AutoClockService.OFFER_CHANNEL)?.importance!=NotificationManager.IMPORTANCE_NONE }

    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.auto_title),fontSize=20.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.auto_description),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(stringResource(R.string.auto_enable),Modifier.weight(1f))
            Switch(checked=enabled,enabled=(!foldable && hasTilt)||enabled,onCheckedChange={ value ->
                if(value) enableMonitor() else {
                    enabled=false; settings.enabled=false
                    context.stopService(Intent(context,AutoClockService::class.java)); revision++
                }
            })
        }
        if(foldable) Text(stringResource(R.string.auto_foldable_unchanged),fontSize=13.sp)
        if(!hasTilt) Text(stringResource(R.string.auto_sensor_missing),fontSize=13.sp)
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(stringResource(R.string.auto_background_enable),Modifier.weight(1f))
            Switch(checked=automatic,enabled=!foldable && hasTilt,onCheckedChange={ value ->
                if(value) explainAccess=true else { automatic=false; settings.automaticLaunch=false; revision++ }
            })
        }
        Text(stringResource(if(specialAccess) R.string.auto_background_granted else R.string.auto_background_missing),fontSize=13.sp)
        if(automatic && !specialAccess) Button(onClick={ explainAccess=true }) { Text(stringResource(R.string.auto_background_setup)) }
        Text(stringResource(when {
            !enabled -> R.string.auto_status_disabled
            startFailed -> R.string.auto_status_failed
            !running -> R.string.auto_status_restart
            automatic && specialAccess -> R.string.auto_status_automatic_ready
            !noticesAllowed -> R.string.auto_status_notifications
            else -> R.string.auto_status_ready
        }),fontSize=13.sp)
        if(enabled && !running) TextButton(onClick={ noteExplicitEntry(); startMonitor() }) { Text(stringResource(R.string.auto_restart)) }
        if(!noticesAllowed) TextButton(onClick={ openSetup(context,Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName)) }) {
            Text(stringResource(R.string.auto_notification_setup))
        }
        val resultLabel=when(lastResult) {
            "SCANNING" -> R.string.auto_result_scanning
            "NO_MATCH" -> R.string.auto_result_no_match
            "NOTIFICATION" -> R.string.auto_result_notification
            "REQUESTED" -> R.string.auto_result_requested
            "SHOWN" -> R.string.auto_result_shown
            "BLOCKED", "MONITOR_STOPPED" -> R.string.auto_result_blocked
            else -> null
        }
        if(resultLabel!=null) Text(stringResource(resultLabel),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(ChargingMode.NOT_REQUIRED to R.string.auto_no_power,ChargingMode.WIRELESS_ONLY to R.string.auto_wireless,ChargingMode.ANY_POWER to R.string.auto_any_power).forEach { (mode,label) ->
            Row(Modifier.fillMaxWidth().clickable { charging=mode; settings.chargingMode=mode },verticalAlignment=Alignment.CenterVertically) {
                RadioButton(selected=charging==mode,onClick={ charging=mode; settings.chargingMode=mode })
                Text(stringResource(label),Modifier.weight(1f))
            }
        }
        Text(stringResource(if(notificationAccess) R.string.auto_media_granted else R.string.auto_media_missing),fontSize=13.sp)
        TextButton(onClick={ if(!MediaNotificationAccess.openSettings(context)) showUnavailable(context) }) { Text(stringResource(R.string.auto_media_setup)) }
        Text(stringResource(R.string.auto_media_lock_controls),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick={ openSetup(context,Intent(Settings.ACTION_DREAM_SETTINGS)) }) { Text(stringResource(R.string.auto_dream_setup)) }
        Text(stringResource(R.string.auto_dream_description),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick={ openSetup(context,Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+context.packageName))) }) {
            Text(stringResource(R.string.auto_app_settings))
        }
        Text(stringResource(R.string.auto_platform_limit),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if(explainAccess) AlertDialog(onDismissRequest={ explainAccess=false },
        title={ Text(stringResource(R.string.auto_background_enable)) },
        text={ Text(stringResource(R.string.auto_background_explanation)) },
        confirmButton={ TextButton(onClick={
            noteExplicitEntry()
            explainAccess=false; automatic=true; settings.automaticLaunch=true
            enabled=true; settings.enabled=true; startMonitor()
            if(!BackgroundLaunchAccess.granted(context) && !BackgroundLaunchAccess.openSettings(context)) showUnavailable(context)
            revision++
        }) { Text(stringResource(R.string.auto_background_setup)) } },
        dismissButton={ TextButton(onClick={ explainAccess=false }) { Text(stringResource(R.string.auto_cancel)) } })
}

private fun showUnavailable(context: Context) { android.widget.Toast.makeText(context,R.string.auto_settings_unavailable,android.widget.Toast.LENGTH_LONG).show() }
private fun openSetup(context:Context,intent:Intent) {
    if(context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
    try { context.startActivity(intent) } catch (_:RuntimeException) { showUnavailable(context) }
}
