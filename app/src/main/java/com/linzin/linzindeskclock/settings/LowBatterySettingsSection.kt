package com.linzin.linzindeskclock.settings

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
import com.linzin.linzindeskclock.R
import com.linzin.linzindeskclock.power.LowBatteryAction
import com.linzin.linzindeskclock.power.LowBatteryPreferences
import kotlin.math.roundToInt

@Composable
fun LowBatterySettingsSection() {
    val context=LocalContext.current
    val prefs=remember { LowBatteryPreferences(context) }
    var action by remember { mutableStateOf(prefs.action) }
    var threshold by remember { mutableFloatStateOf(prefs.threshold.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.low_battery_title),fontSize=20.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.low_battery_description),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        for((option,label) in listOf(LowBatteryAction.OFF to R.string.low_battery_off,LowBatteryAction.DIM to R.string.low_battery_dim,LowBatteryAction.EXIT to R.string.low_battery_exit)) {
            Row(Modifier.fillMaxWidth().clickable { action=option;prefs.action=option },verticalAlignment=Alignment.CenterVertically) {
                RadioButton(selected=action==option,onClick={ action=option;prefs.action=option })
                Text(stringResource(label))
            }
        }
        Text(stringResource(R.string.low_battery_threshold,threshold.roundToInt()))
        Slider(value=threshold,onValueChange={threshold=it},onValueChangeFinished={prefs.threshold=threshold.roundToInt()},valueRange=5f..50f,steps=8,enabled=action!=LowBatteryAction.OFF)
    }
}
