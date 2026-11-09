package com.linzin.linzindeskclock.settings

import android.app.KeyguardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.linzin.linzindeskclock.R
import com.linzin.linzindeskclock.power.ScreenOffAccess

@Composable
fun ScreenOffPermissionSection(timeoutMillis: Long) {
    if (timeoutMillis < 0L) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var supported by remember(context) { mutableStateOf(ScreenOffAccess.isSupported(context)) }
    var granted by remember(context) { mutableStateOf(supported && ScreenOffAccess.isGranted(context)) }
    var requestInProgress by remember(context) { mutableStateOf(false) }
    var permissionError by remember(context) { mutableStateOf<Int?>(null) }

    fun refreshPermission() {
        supported = ScreenOffAccess.isSupported(context)
        granted = supported && ScreenOffAccess.isGranted(context)
        if (granted) permissionError = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        requestInProgress = false
        refreshPermission()
    }

    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .testTag("screen_off_permission_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.screen_off_permission_title),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Text(
            text = stringResource(R.string.screen_off_admin_explanation),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = Color(0xFF9E9EA8)
        )
        Text(
            text = stringResource(
                when {
                    !supported -> R.string.screen_off_permission_unsupported
                    granted -> R.string.screen_off_permission_granted
                    else -> R.string.screen_off_permission_not_granted
                }
            ),
            fontSize = 13.sp,
            color = Color.White,
            modifier = Modifier
                .testTag("screen_off_permission_status")
                .semantics { liveRegion = LiveRegionMode.Polite }
        )
        if (!granted) {
            Text(
                text = stringResource(R.string.screen_off_fallback_message),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = Color(0xFF9E9EA8),
                modifier = Modifier.testTag("screen_off_fallback_message")
            )
        }
        if (supported && !granted) {
            Button(
                onClick = {
                    if (!requestInProgress) {
                        refreshPermission()
                        val keyguard = context.getSystemService(KeyguardManager::class.java)
                        when {
                            granted -> Unit
                            !supported -> permissionError = R.string.screen_off_permission_unsupported
                            keyguard == null -> permissionError = R.string.screen_off_permission_failed
                            keyguard.isKeyguardLocked || keyguard.isDeviceLocked ->
                                permissionError = R.string.screen_off_permission_locked
                            else -> {
                                permissionError = null
                                requestInProgress = true
                                try {
                                    permissionLauncher.launch(ScreenOffAccess.activationIntent(context))
                                } catch (_: RuntimeException) {
                                    requestInProgress = false
                                    permissionError = R.string.screen_off_permission_failed
                                }
                            }
                        }
                    }
                },
                enabled = !requestInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("screen_off_permission_enable")
            ) {
                Text(stringResource(R.string.screen_off_permission_enable))
            }
        }
        permissionError?.let { message ->
            Text(
                text = stringResource(message),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .testTag("screen_off_permission_error")
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}
