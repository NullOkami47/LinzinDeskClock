package com.linzin.linzindeskclock.settings

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.linzin.linzindeskclock.R
import com.linzin.linzindeskclock.sensors.ChargingMode
import com.linzin.linzindeskclock.service.AutoClockService
import kotlinx.coroutines.delay

@Composable
fun AutoClockSettingsSection(hasHingeSensor: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val foldable = hasHingeSensor || context.packageManager.hasSystemFeature("android.hardware.sensor.hinge_angle")
    val hasTiltSensor = remember {
        val sensors = context.getSystemService(android.hardware.SensorManager::class.java)
        sensors.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY) != null || sensors.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER) != null
    }
    val settings = remember { AutoClockPreferences(context) }
    var enabled by remember { mutableStateOf(settings.enabled) }
    var mode by remember { mutableStateOf(settings.chargingMode) }
    var privateMedia by remember { mutableStateOf(settings.hideMediaWhenLocked) }
    var revision by remember { mutableIntStateOf(0) }
    var monitorRunning by remember { mutableStateOf(AutoClockService.running) }
    var startFailed by remember { mutableStateOf(false) }
    val startMonitor = {
        startFailed = try {
            ContextCompat.startForegroundService(context,Intent(context,AutoClockService::class.java)); false
        } catch (_: RuntimeException) { true }
        revision++
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startMonitor() }
    DisposableEffect(lifecycleOwner) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _,event -> if(event == Lifecycle.Event.ON_RESUME) {
            enabled=settings.enabled; revision++
        } }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(enabled,revision) { delay(500); monitorRunning = AutoClockService.running }
    val notifications = context.getSystemService(NotificationManager::class.java)
    val notificationsAllowed = remember(revision,enabled) { notifications.areNotificationsEnabled() &&
        notifications.getNotificationChannel(AutoClockService.OFFER_CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE }
    val listenerAllowed = remember(revision) {
        val wanted = ComponentName(context,"com.linzin.linzindeskclock.media.ClockNotificationListenerService")
        notifications.isNotificationListenerAccessGranted(wanted)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.auto_title),fontSize=20.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.auto_description),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(stringResource(R.string.auto_enable),modifier=Modifier.weight(1f))
            Switch(checked=enabled,enabled=!foldable && hasTiltSensor,onCheckedChange={ next ->
                enabled=next; settings.enabled=next
                if(!next) context.stopService(Intent(context,AutoClockService::class.java)) else {
                    if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED &&
                        !settings.preferences.getBoolean("auto_notification_asked",false)) {
                        settings.preferences.edit().putBoolean("auto_notification_asked",true).apply()
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else startMonitor()
                }
                revision++
            })
        }
        if(foldable) Text(stringResource(R.string.auto_foldable_unchanged),fontSize=13.sp)
        if(!hasTiltSensor) Text(stringResource(R.string.auto_sensor_missing),fontSize=13.sp)
        listOf(ChargingMode.WIRELESS_ONLY to R.string.auto_wireless,ChargingMode.ANY_POWER to R.string.auto_any_power,ChargingMode.NOT_REQUIRED to R.string.auto_no_power).forEach { (choice,label) ->
            Row(Modifier.fillMaxWidth().clickable { mode=choice; settings.chargingMode=choice },verticalAlignment=Alignment.CenterVertically) {
                RadioButton(selected=mode==choice,onClick={ mode=choice; settings.chargingMode=choice })
                Text(stringResource(label),modifier=Modifier.weight(1f))
            }
        }
        Text(stringResource(when {
            !enabled -> R.string.auto_status_disabled
            startFailed -> R.string.auto_status_failed
            !monitorRunning -> R.string.auto_status_restart
            !notificationsAllowed -> R.string.auto_status_notifications
            else -> R.string.auto_status_ready
        }),fontSize=13.sp)
        if(enabled) TextButton(onClick={ startMonitor() }) { Text(stringResource(R.string.auto_restart)) }
        if(!notificationsAllowed) TextButton(onClick={
            openSetup(context,Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))
        }) { Text(stringResource(R.string.auto_notification_setup)) }
        Text(stringResource(if(listenerAllowed) R.string.auto_media_granted else R.string.auto_media_missing),fontSize=13.sp)
        TextButton(onClick={ openSetup(context,Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text(stringResource(R.string.auto_media_setup)) }
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(stringResource(R.string.auto_media_privacy),modifier=Modifier.weight(1f))
            Switch(checked=privateMedia,onCheckedChange={ privateMedia=it; settings.hideMediaWhenLocked=it })
        }
        TextButton(onClick={ openSetup(context,Intent(Settings.ACTION_DREAM_SETTINGS)) }) { Text(stringResource(R.string.auto_dream_setup)) }
        Text(stringResource(R.string.auto_dream_description),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.auto_platform_limit),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun openSetup(context: Context,intent: Intent) {
    try { context.startActivity(intent) } catch (_: RuntimeException) {
        android.widget.Toast.makeText(context,R.string.auto_settings_unavailable,android.widget.Toast.LENGTH_LONG).show()
    }
}
