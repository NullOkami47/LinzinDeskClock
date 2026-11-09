package com.linzin.linzindeskclock.updates

import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.linzin.linzindeskclock.R
import kotlinx.coroutines.launch
import java.util.Locale

data class AppUpdateState(val info: AppUpdateInfo?, val checking: Boolean, val check: () -> Unit)

@Composable
fun rememberAppUpdateState(enabled: Boolean): AppUpdateState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateAvailableTemplate = stringResource(R.string.toast_update_available)
    val updateFailedMessage = stringResource(R.string.settings_update_failed)
    val updateCurrentTemplate = stringResource(R.string.settings_update_current)
    val version = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.0.4"
    }
    var info by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var checking by remember { mutableStateOf(false) }
    suspend fun check(manual: Boolean) {
        if (!enabled || checking) return
        checking = true
        try {
            val result = checkForAppUpdate(version)
            info = result
            val message = when {
                result?.hasUpdate == true -> String.format(Locale.getDefault(), updateAvailableTemplate, result.latestVersion)
                manual && result == null -> updateFailedMessage
                manual -> String.format(Locale.getDefault(), updateCurrentTemplate, version)
                else -> null
            }
            message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        } finally {
            checking = false
        }
    }
    LaunchedEffect(enabled, version) { if (enabled) check(false) }
    return AppUpdateState(info, checking) { scope.launch { check(true) } }
}
