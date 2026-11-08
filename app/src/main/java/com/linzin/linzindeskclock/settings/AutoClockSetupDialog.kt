package com.linzin.linzindeskclock.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.linzin.linzindeskclock.R

@Composable
fun AutoClockSetupDialog(hasHingeSensor:Boolean,onDismiss:()->Unit) {
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(shape=MaterialTheme.shapes.large,modifier=Modifier.fillMaxWidth(.9f).fillMaxHeight(.9f)) {
            Column(Modifier.padding(20.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { AutoClockSettingsSection(hasHingeSensor) }
                TextButton(onClick=onDismiss) { Text(stringResource(R.string.auto_done)) }
            }
        }
    }
}
