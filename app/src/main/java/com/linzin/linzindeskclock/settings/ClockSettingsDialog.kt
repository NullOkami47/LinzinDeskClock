package com.linzin.linzindeskclock.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.linzin.linzindeskclock.settings.AutoClockSettingsSection
import com.linzin.linzindeskclock.*

@Composable
fun ClockSettingsDialog(
    is24Hour: Boolean,
    on24HourChange: (Boolean) -> Unit,
    showBattery: Boolean,
    onShowBatteryChange: (Boolean) -> Unit,
    showCornerDate: Boolean,
    onShowCornerDateChange: (Boolean) -> Unit,
    showHingeAngle: Boolean,
    onShowHingeAngleChange: (Boolean) -> Unit,
    hasHingeSensor: Boolean = true,
    showCalendarEvents: Boolean,
    onShowCalendarEventsChange: (Boolean) -> Unit,
    manualBrightness: Boolean,
    onManualBrightnessChange: (Boolean) -> Unit,
    brightnessValue: Float,
    onBrightnessValueChange: (Float) -> Unit,
    timeoutMillis: Long,
    onTimeoutChange: (Long) -> Unit,
    layoutMode: ClockLayoutMode,
    onLayoutModeChange: (ClockLayoutMode) -> Unit,
    weatherEnabled: Boolean,
    onWeatherEnabledChange: (Boolean) -> Unit,
    isFahrenheit: Boolean,
    onFahrenheitChange: (Boolean) -> Unit,
    burnInProtectionEnabled: Boolean,
    onBurnInProtectionChange: (Boolean) -> Unit,
    onOpenTutorial: () -> Unit,
    onDismiss: () -> Unit
) {
    var showSuggestDualDialog by remember { mutableStateOf(false) }

    val handleWeatherToggle: (Boolean) -> Unit = { targetState ->
        onWeatherEnabledChange(targetState)
        if (targetState && layoutMode == ClockLayoutMode.SINGLE) {
            showSuggestDualDialog = true
        }
    }

    val handleCalendarToggle: (Boolean) -> Unit = { targetState ->
        onShowCalendarEventsChange(targetState)
        if (targetState && layoutMode == ClockLayoutMode.SINGLE) {
            showSuggestDualDialog = true
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E1E22)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 60.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF33333A)))

                    // 1. 區塊版面模式選擇
                    Text(
                        text = stringResource(R.string.layout_section_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                    )

                    val layoutOptions = listOf(
                        ClockLayoutMode.SINGLE to stringResource(R.string.layout_single),
                        ClockLayoutMode.DUAL_LEFT_LARGE to stringResource(R.string.layout_dual_left),
                        ClockLayoutMode.DUAL_RIGHT_LARGE to stringResource(R.string.layout_dual_right)
                    )

                    layoutOptions.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLayoutModeChange(mode) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (layoutMode == mode),
                                onClick = { onLayoutModeChange(mode) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                fontSize = 16.sp,
                                color = if (layoutMode == mode) Color.White else Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF33333A)))

                    // 2. 當地天氣資訊
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { handleWeatherToggle(!weatherEnabled) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.pref_weather_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.pref_weather_desc),
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = weatherEnabled,
                            onCheckedChange = null
                        )
                    }

                    if (weatherEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onFahrenheitChange(!isFahrenheit) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.temp_unit_switch),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isFahrenheit) stringResource(R.string.temp_unit_f) else stringResource(R.string.temp_unit_c),
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "°C",
                                    color = if (!isFahrenheit) Color(0xFF5DAC81) else Color(0xFF75757F),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Switch(
                                    checked = isFahrenheit,
                                    onCheckedChange = null,
                                    colors = SwitchDefaults.colors(
                                        checkedTrackColor = Color(0xFF81C7D4),
                                        checkedThumbColor = Color(0xFF041E49),
                                        checkedBorderColor = Color(0xFF81C7D4),
                                        uncheckedTrackColor = Color(0xFF5DAC81),
                                        uncheckedThumbColor = Color(0xFF041E49),
                                        uncheckedBorderColor = Color(0xFF5DAC81)
                                    )
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = "°F",
                                    color = if (isFahrenheit) Color(0xFF81C7D4) else Color(0xFF75757F),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF33333A)))

                    // 3. 行事曆近期行程
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { handleCalendarToggle(!showCalendarEvents) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.pref_calendar_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.pref_calendar_desc),
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = showCalendarEvents,
                            onCheckedChange = null
                        )
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF33333A)))

                    // 4. 12 / 24 小時制切換
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { on24HourChange(!is24Hour) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.pref_24h_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = if (is24Hour) stringResource(R.string.pref_24h_desc_on) else stringResource(R.string.pref_24h_desc_off),
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(checked = is24Hour, onCheckedChange = null)
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF33333A)))

                    // 5. 顯示元素開關 (電量、角落日期、轉軸角度)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onShowBatteryChange(!showBattery) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.pref_battery_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.pref_battery_desc),
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(checked = showBattery, onCheckedChange = null)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onShowCornerDateChange(!showCornerDate) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.pref_corner_date_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.pref_corner_date_desc),
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(checked = showCornerDate, onCheckedChange = null)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (hasHingeSensor) {
                                    Modifier.clickable { onShowHingeAngleChange(!showHingeAngle) }
                                } else Modifier
                            )
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.pref_hinge_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (hasHingeSensor) Color.White else Color(0xFF6E6E78)
                            )
                            Text(
                                text = if (hasHingeSensor) {
                                    stringResource(R.string.pref_hinge_desc)
                                } else {
                                    stringResource(R.string.pref_hinge_unsupported)
                                },
                                fontSize = 13.sp,
                                color = if (hasHingeSensor) Color.Gray else Color(0xFFE57373)
                            )
                        }
                        Switch(
                            checked = if (hasHingeSensor) showHingeAngle else false,
                            onCheckedChange = null,
                            enabled = hasHingeSensor
                        )
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF33333A)))

                    // 6. 防烙印開關
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onBurnInProtectionChange(!burnInProtectionEnabled) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.pref_burn_in_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.pref_burn_in_desc),
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = burnInProtectionEnabled,
                            onCheckedChange = null
                        )
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF33333A)))

                    // 7. 螢幕亮度手動控制
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onManualBrightnessChange(!manualBrightness) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.pref_brightness_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = if (manualBrightness) stringResource(R.string.pref_brightness_desc_manual) else stringResource(R.string.pref_brightness_desc_auto),
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(checked = manualBrightness, onCheckedChange = null)
                    }

                    if (manualBrightness) {
                        Column(modifier = Modifier.padding(bottom = 12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.brightness_value),
                                    color = Color.LightGray,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "${(brightnessValue * 100).toInt()}%",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = brightnessValue,
                                onValueChange = onBrightnessValueChange,
                                valueRange = 0.05f..1.0f
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF33333A)))

                    // 8. 螢幕關閉逾時
                    Text(
                        text = stringResource(R.string.pref_timeout_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                    )

                    Text(
                        text = stringResource(R.string.pref_timeout_desc),
                        fontSize = 12.sp,
                        color = Color(0xFF9E9EA8),
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val timeoutOptions = listOf(
                        60_000L to stringResource(R.string.timeout_1m),
                        180_000L to stringResource(R.string.timeout_3m),
                        600_000L to stringResource(R.string.timeout_10m),
                        1_800_000L to stringResource(R.string.timeout_30m),
                        3_600_000L to stringResource(R.string.timeout_1h),
                        10_800_000L to stringResource(R.string.timeout_3h),
                        -1L to stringResource(R.string.timeout_never)
                    )

                    timeoutOptions.forEach { (millis, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTimeoutChange(millis) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (timeoutMillis == millis),
                                onClick = { onTimeoutChange(millis) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                fontSize = 16.sp,
                                color = if (timeoutMillis == millis) Color.White else Color.Gray
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF33333A)))

                    AutoClockSettingsSection(hasHingeSensor = hasHingeSensor)

                    // 9. 查看手勢操作指南
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenTutorial() }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.pref_tutorial_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.pref_tutorial_desc),
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                        Text(
                            text = "➔",
                            color = Color(0xFF81C7D4),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }

                Surface(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF5DAC81),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp)
                        .size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.size(24.dp)) {
                            val strokeWidth = 3.5.dp.toPx()
                            val checkColor = Color(0xFF041E49)

                            val p1 = Offset(size.width * 0.20f, size.height * 0.52f)
                            val p2 = Offset(size.width * 0.42f, size.height * 0.74f)
                            val p3 = Offset(size.width * 0.82f, size.height * 0.28f)

                            drawLine(
                                color = checkColor,
                                start = p1,
                                end = p2,
                                strokeWidth = strokeWidth,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = checkColor,
                                start = p2,
                                end = p3,
                                strokeWidth = strokeWidth,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                if (showSuggestDualDialog) {
                    SwitchToDualLayoutDialog(
                        onDismiss = { showSuggestDualDialog = false },
                        onKeepSingle = {},
                        onSelectLeftLarge = {
                            onLayoutModeChange(ClockLayoutMode.DUAL_LEFT_LARGE)
                        },
                        onSelectRightLarge = {
                            onLayoutModeChange(ClockLayoutMode.DUAL_RIGHT_LARGE)
                        }
                    )
                }
            }
        }
    }
}

