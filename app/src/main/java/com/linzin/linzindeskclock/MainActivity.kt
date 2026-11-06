package com.linzin.linzindeskclock

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color as AndroidGraphicsColor
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import android.util.Log
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

// ==========================================
// 1. 主題配色與資料結構
// ==========================================

private val DeskClockColorScheme = darkColorScheme(
    primary = Color(0xFF81C7D4),
    onPrimary = Color(0xFF041E49),
    primaryContainer = Color(0xFF1E3A45),
    onPrimaryContainer = Color(0xFF81C7D4),
    secondary = Color(0xFF5DAC81),
    onSecondary = Color(0xFF041E49),
    surface = Color(0xFF1E1E22),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C34),
    onSurfaceVariant = Color(0xFFA0A0AA),
    outline = Color(0xFF5A5A62)
)

enum class ClockLayoutMode {
    SINGLE,
    DUAL_LEFT_LARGE,
    DUAL_RIGHT_LARGE
}

data class HourlyWeather(
    val timeLabel: String,
    val tempC: Float,
    val weatherCode: Int
)

data class WeatherInfo(
    val cityName: String,
    val currentTempC: Float,
    val currentWeatherCode: Int,
    val hourlyList: List<HourlyWeather>
)

data class BatteryState(
    val level: Int = 100,
    val isCharging: Boolean = false
)

data class UpcomingCalendarEvent(
    val title: String,
    val timeLabel: String
)

// ==========================================
// 2. 系統與硬體互動工具函式
// ==========================================

/**
 * 查詢未來 48 小時內的日曆行程（自動過濾已結束的事件與過去的全天行程）
 */
fun fetchUpcomingCalendarEvents(context: Context): List<UpcomingCalendarEvent> {
    val hasPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED
    if (!hasPermission) return emptyList()

    val eventList = mutableListOf<UpcomingCalendarEvent>()
    val now = System.currentTimeMillis()
    val endWindow = now + (48 * 60 * 60 * 1000L)

    val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
    ContentUris.appendId(builder, now)
    ContentUris.appendId(builder, endWindow)

    val projection = arrayOf(
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY
    )
    val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"

    try {
        context.contentResolver.query(
            builder.build(),
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val titleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val beginIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val allDayIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)

            val timeFormat = SimpleDateFormat("H:mm", Locale.US)
            val dayFormat = SimpleDateFormat("M/d", Locale.US)

            val nowCal = Calendar.getInstance().apply { timeInMillis = now }
            val currentYear = nowCal.get(Calendar.YEAR)
            val currentDayOfYear = nowCal.get(Calendar.DAY_OF_YEAR)

            while (cursor.moveToNext() && eventList.size < 3) {
                val title = cursor.getString(titleIdx) ?: context.getString(R.string.event_untitled)
                val beginMillis = cursor.getLong(beginIdx)
                val endMillis = cursor.getLong(endIdx)
                val isAllDay = cursor.getInt(allDayIdx) == 1

                if (isAllDay) {
                    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                        timeInMillis = beginMillis
                    }
                    val eventYear = utcCal.get(Calendar.YEAR)
                    val eventDayOfYear = utcCal.get(Calendar.DAY_OF_YEAR)

                    if (eventYear < currentYear || (eventYear == currentYear && eventDayOfYear < currentDayOfYear)) {
                        continue
                    }

                    val isToday = (eventYear == currentYear && eventDayOfYear == currentDayOfYear)
                    val label = if (isToday) {
                        context.getString(R.string.event_all_day_today)
                    } else {
                        dayFormat.timeZone = TimeZone.getTimeZone("UTC")
                        "${dayFormat.format(Date(beginMillis))} ${context.getString(R.string.event_all_day)}"
                    }

                    eventList.add(UpcomingCalendarEvent(title = title, timeLabel = label))
                } else {
                    if (endMillis <= now) {
                        continue
                    }

                    val eventCal = Calendar.getInstance().apply { timeInMillis = beginMillis }
                    val isToday = (eventCal.get(Calendar.DAY_OF_YEAR) == currentDayOfYear &&
                            eventCal.get(Calendar.YEAR) == currentYear)

                    dayFormat.timeZone = TimeZone.getDefault()
                    timeFormat.timeZone = TimeZone.getDefault()

                    val label = if (isToday) {
                        timeFormat.format(Date(beginMillis))
                    } else {
                        "${dayFormat.format(Date(beginMillis))} ${timeFormat.format(Date(beginMillis))}"
                    }

                    eventList.add(UpcomingCalendarEvent(title = title, timeLabel = label))
                }
            }
        }
    } catch (e: Exception) {
        Log.e("DeskClockCalendar", "Error querying calendar instances", e)
    }

    return eventList
}

/**
 * 非同步發送 Shell 指令至系統（如調用 cmd device_state）
 */
fun executeShellCommand(command: String) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val process = ProcessBuilder("sh", "-c", command).start()
            process.waitFor()
            Log.d("FoldDeskClock", "Executed: $command")
        } catch (e: Exception) {
            Log.e("FoldDeskClock", "Failed: $command", e)
        }
    }
}

/**
 * 查詢 Open-Meteo 天氣預報 API 並解析
 */
suspend fun fetchOpenMeteoWeather(lat: Double, lon: Double, cityName: String): WeatherInfo? = withContext(Dispatchers.IO) {
    try {
        val urlStr = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&hourly=temperature_2m,weather_code&timezone=auto"
        val connection = URL(urlStr).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8000
        connection.readTimeout = 8000

        val responseText = connection.inputStream.bufferedReader().use { it.readText() }
        val root = JSONObject(responseText)

        val currentObj = root.getJSONObject("current")
        val currentTemp = currentObj.getDouble("temperature_2m").toFloat()
        val currentCode = currentObj.getInt("weather_code")

        val hourlyObj = root.getJSONObject("hourly")
        val times = hourlyObj.getJSONArray("time")
        val temps = hourlyObj.getJSONArray("temperature_2m")
        val codes = hourlyObj.getJSONArray("weather_code")

        val nowCal = Calendar.getInstance()
        val currentHourInt = nowCal.get(Calendar.HOUR_OF_DAY)

        val hourlyResult = mutableListOf<HourlyWeather>()
        val inFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
        val outFormat = SimpleDateFormat("ha", Locale.US)

        for (i in 0 until times.length()) {
            val tStr = times.getString(i)
            val date = inFormat.parse(tStr)
            if (date != null) {
                val cal = Calendar.getInstance().apply { time = date }
                if (cal.get(Calendar.HOUR_OF_DAY) >= currentHourInt && cal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)) {
                    val label = outFormat.format(date).uppercase(Locale.US)
                    val t = temps.getDouble(i).toFloat()
                    val c = codes.getInt(i)
                    hourlyResult.add(HourlyWeather(timeLabel = label, tempC = t, weatherCode = c))
                    if (hourlyResult.size == 4) break
                }
            }
        }

        var fallbackIdx = 0
        while (hourlyResult.size < 4 && fallbackIdx < times.length()) {
            val tStr = times.getString(fallbackIdx)
            val date = inFormat.parse(tStr)
            if (date != null) {
                val label = outFormat.format(date).uppercase(Locale.US)
                val t = temps.getDouble(fallbackIdx).toFloat()
                val c = codes.getInt(fallbackIdx)
                if (hourlyResult.none { it.timeLabel == label }) {
                    hourlyResult.add(HourlyWeather(timeLabel = label, tempC = t, weatherCode = c))
                }
            }
            fallbackIdx++
        }

        WeatherInfo(
            cityName = cityName,
            currentTempC = currentTemp,
            currentWeatherCode = currentCode,
            hourlyList = hourlyResult
        )
    } catch (e: Exception) {
        Log.e("DeskClockWeather", "Failed to fetch Open-Meteo", e)
        null
    }
}

fun formatTemp(celsius: Float, isFahrenheit: Boolean): String {
    val temp = if (isFahrenheit) (celsius * 9f / 5f + 32f).roundToInt() else celsius.roundToInt()
    return "$temp°"
}

// ==========================================
// 3. 防烙印 Shader 修飾符
// ==========================================

fun Modifier.checkerboardBurnInProtection(isIdle: Boolean): Modifier = composed {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return@composed this
    }

    var phase by remember { mutableIntStateOf(0) }

    LaunchedEffect(isIdle) {
        if (isIdle) {
            while (true) {
                delay(300_000L)
                phase = (phase + 1) % 2
            }
        } else {
            phase = 0
        }
    }

    if (!isIdle) {
        return@composed this
    }

    val shader = remember {
        RuntimeShader(
            """
            uniform shader composable;
            uniform float phase;

            half4 main(float2 fragCoord) {
                float check = mod(floor(fragCoord.x) + floor(fragCoord.y) + phase, 2.0);
                if (check < 1.0) {
                    return composable.eval(fragCoord);
                }
                return half4(0.0, 0.0, 0.0, 1.0);
            }
            """.trimIndent()
        )
    }

    graphicsLayer {
        try {
            shader.setFloatUniform("phase", phase.toFloat())
            renderEffect = RenderEffect
                .createRuntimeShaderEffect(shader, "composable")
                .asComposeRenderEffect()
        } catch (e: Throwable) {
            renderEffect = null
        }
    }
}

// ==========================================
// 4. 通用 UI 元件與系統控制器
// ==========================================

@Composable
fun rememberBatteryState(): BatteryState {
    val context = LocalContext.current
    var batteryState by remember { mutableStateOf(BatteryState()) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent?.let {
                    val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                    val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
                    batteryState = BatteryState(level = pct, isCharging = isCharging)
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val stickyIntent = context.registerReceiver(receiver, filter)
        stickyIntent?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
            batteryState = BatteryState(level = pct, isCharging = isCharging)
        }

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    return batteryState
}

@Composable
fun SystemBarsController(hideSystemBars: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as? Activity)?.window ?: return
        DisposableEffect(hideSystemBars) {
            val controller = WindowCompat.getInsetsController(window, view)
            WindowCompat.setDecorFitsSystemWindows(window, !hideSystemBars)

            if (hideSystemBars) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                controller.show(WindowInsetsCompat.Type.systemBars())
            }

            onDispose {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

@Composable
fun BatteryIndicator(
    batteryLevel: Int,
    isCharging: Boolean,
    modifier: Modifier = Modifier
) {
    val textShadow = Shadow(
        color = Color.Black.copy(alpha = 0.65f),
        offset = Offset(1.5f, 2f),
        blurRadius = 5f
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$batteryLevel%",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(shadow = textShadow)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Canvas(modifier = Modifier.size(width = 26.dp, height = 13.dp)) {
            val w = size.width
            val h = size.height
            val strokeW = 1.3.dp.toPx()

            val bodyWidth = w - 3.5.dp.toPx()
            val bodyHeight = h
            val bodyRadius = 3.2.dp.toPx()

            drawRoundRect(
                color = Color.Black.copy(alpha = 0.40f),
                topLeft = Offset(0.8.dp.toPx(), 1.2.dp.toPx()),
                size = Size(bodyWidth, bodyHeight),
                cornerRadius = CornerRadius(bodyRadius, bodyRadius),
                style = Stroke(width = strokeW)
            )

            drawRoundRect(
                color = Color.White.copy(alpha = 0.90f),
                topLeft = Offset(0f, 0f),
                size = Size(bodyWidth, bodyHeight),
                cornerRadius = CornerRadius(bodyRadius, bodyRadius),
                style = Stroke(width = strokeW)
            )

            val capW = 2.dp.toPx()
            val capH = 5.dp.toPx()
            val capX = bodyWidth + 0.8.dp.toPx()
            val capY = (h - capH) / 2f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.90f),
                topLeft = Offset(capX, capY),
                size = Size(capW, capH),
                cornerRadius = CornerRadius(1.2.dp.toPx(), 1.2.dp.toPx())
            )

            val pad = 2.dp.toPx()
            val innerW = (bodyWidth - pad * 2f).coerceAtLeast(0f)
            val innerH = (bodyHeight - pad * 2f).coerceAtLeast(0f)
            val fillW = (innerW * (batteryLevel.coerceIn(0, 100) / 100f)).coerceAtLeast(0f)

            val fillColor = when {
                isCharging -> Color(0xFF34C759)
                batteryLevel <= 15 -> Color(0xFFFF3B30)
                batteryLevel <= 20 -> Color(0xFFFF9500)
                else -> Color(0xFF34C759)
            }

            if (fillW > 0f) {
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(pad, pad),
                    size = Size(fillW, innerH),
                    cornerRadius = CornerRadius(1.8.dp.toPx(), 1.8.dp.toPx())
                )
            }

            if (isCharging) {
                val boltPath = Path().apply {
                    val bcx = bodyWidth / 2f
                    val bcy = h / 2f
                    val bw = 6.dp.toPx()
                    val bh = 9.dp.toPx()

                    moveTo(bcx + bw * 0.15f, bcy - bh * 0.50f)
                    lineTo(bcx - bw * 0.45f, bcy + bh * 0.05f)
                    lineTo(bcx - bw * 0.05f, bcy + bh * 0.05f)
                    lineTo(bcx - bw * 0.20f, bcy + bh * 0.50f)
                    lineTo(bcx + bw * 0.45f, bcy - bh * 0.05f)
                    lineTo(bcx + bw * 0.05f, bcy - bh * 0.05f)
                    close()
                }

                drawPath(
                    path = boltPath,
                    color = Color.Black.copy(alpha = 0.5f),
                    style = Stroke(width = 1.2.dp.toPx())
                )
                drawPath(
                    path = boltPath,
                    color = Color(0xFFFFD600)
                )
            }
        }
    }
}

@Composable
fun MaterialLockIcon(
    isLocked: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val bodyW = w * 0.72f
        val bodyH = h * 0.48f
        val bodyLeft = (w - bodyW) / 2f
        val bodyTop = h * 0.46f
        val bodyRadius = 2.5.dp.toPx()

        val shackleW = bodyW * 0.58f
        val shackleRadius = shackleW / 2f
        val shackleStroke = 2.0.dp.toPx()

        if (isLocked) {
            val arcTop = h * 0.16f
            val arcLeft = (w - shackleW) / 2f

            val shacklePath = Path().apply {
                moveTo(arcLeft, bodyTop + 1.dp.toPx())
                lineTo(arcLeft, arcTop + shackleRadius)
                arcTo(
                    rect = Rect(arcLeft, arcTop, arcLeft + shackleW, arcTop + shackleW),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false
                )
                lineTo(arcLeft + shackleW, bodyTop + 1.dp.toPx())
            }

            drawPath(
                path = shacklePath,
                color = color,
                style = Stroke(width = shackleStroke, cap = StrokeCap.Round)
            )
        } else {
            val arcTop = h * 0.08f
            val arcLeft = (w - shackleW) / 2f

            val openShacklePath = Path().apply {
                moveTo(arcLeft, bodyTop - 2.dp.toPx())
                lineTo(arcLeft, arcTop + shackleRadius)
                arcTo(
                    rect = Rect(arcLeft, arcTop, arcLeft + shackleW, arcTop + shackleW),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false
                )
                lineTo(arcLeft + shackleW, arcTop + shackleRadius + 2.dp.toPx())
            }

            drawPath(
                path = openShacklePath,
                color = color,
                style = Stroke(width = shackleStroke, cap = StrokeCap.Round)
            )
        }

        drawRoundRect(
            color = color,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(bodyRadius, bodyRadius)
        )

        val keyHoleCx = w / 2f
        val keyHoleCy = bodyTop + bodyH * 0.42f
        val keyHoleR = 1.8.dp.toPx()
        val holeColor = Color(0xFF1E1E22)

        drawCircle(
            color = holeColor,
            radius = keyHoleR,
            center = Offset(keyHoleCx, keyHoleCy)
        )
        drawLine(
            color = holeColor,
            start = Offset(keyHoleCx, keyHoleCy),
            end = Offset(keyHoleCx, keyHoleCy + bodyH * 0.32f),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun LocationPinIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val r = w * 0.40f
        val cy = r + h * 0.04f
        val strokeW = 1.6.dp.toPx()

        val path = Path().apply {
            arcTo(
                rect = Rect(cx - r, cy - r, cx + r, cy + r),
                startAngleDegrees = -198f,
                sweepAngleDegrees = 216f,
                forceMoveTo = false
            )
            lineTo(cx, h - strokeW * 0.5f)
            close()
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        drawCircle(
            color = color,
            radius = r * 0.42f,
            center = Offset(cx, cy),
            style = Stroke(width = strokeW)
        )
    }
}

@Composable
fun MaterialWeatherIcon(
    weatherCode: Int,
    modifier: Modifier = Modifier
) {
    val sunYellow = Color(0xFFFFD54F)
    val cloudWhite = Color.White
    val rainBlue = Color(0xFF42A5F5)
    val drizzleBlue = Color(0xFF81D4FA)
    val fogGrey = Color(0xFFB0BEC5)
    val lightningGold = Color(0xFFFFCA28)
    val snowCyan = Color(0xFFE1F5FE)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        fun drawCloud(cx: Float, cy: Float, scaleW: Float = 1f, scaleH: Float = 1f, color: Color = cloudWhite) {
            val baseW = w * 0.60f * scaleW
            val baseH = h * 0.28f * scaleH
            val left = cx - baseW / 2f

            drawRoundRect(
                color = color,
                topLeft = Offset(left, cy),
                size = Size(baseW, baseH * 0.65f),
                cornerRadius = CornerRadius(baseH * 0.32f, baseH * 0.32f)
            )
            drawCircle(color = color, radius = baseH * 0.52f, center = Offset(cx - baseW * 0.22f, cy + baseH * 0.10f))
            drawCircle(color = color, radius = baseH * 0.70f, center = Offset(cx + baseW * 0.04f, cy - baseH * 0.05f))
            drawCircle(color = color, radius = baseH * 0.44f, center = Offset(cx + baseW * 0.28f, cy + baseH * 0.18f))
        }

        when (weatherCode) {
            0 -> {
                val cx = w / 2f
                val cy = h / 2f
                val r = w * 0.28f
                drawCircle(color = sunYellow, radius = r, center = Offset(cx, cy))

                val rayDist = r + w * 0.08f
                val rayLen = w * 0.13f
                val strokeW = w * 0.075f
                for (i in 0 until 8) {
                    val angle = (i * 45f) * (PI / 180f).toFloat()
                    val sx = cx + rayDist * cos(angle)
                    val sy = cy + rayDist * sin(angle)
                    val ex = cx + (rayDist + rayLen) * cos(angle)
                    val ey = cy + (rayDist + rayLen) * sin(angle)
                    drawLine(color = sunYellow, start = Offset(sx, sy), end = Offset(ex, ey), strokeWidth = strokeW, cap = StrokeCap.Round)
                }
            }

            1, 2 -> {
                val sunCx = w * 0.68f
                val sunCy = h * 0.34f
                val sunR = w * 0.22f
                drawCircle(color = sunYellow, radius = sunR, center = Offset(sunCx, sunCy))

                for (i in 0 until 6) {
                    val angle = (i * 60f) * (PI / 180f).toFloat()
                    val dist = sunR + w * 0.06f
                    val len = w * 0.09f
                    drawLine(
                        color = sunYellow,
                        start = Offset(sunCx + dist * cos(angle), sunCy + dist * sin(angle)),
                        end = Offset(sunCx + (dist + len) * cos(angle), sunCy + (dist + len) * sin(angle)),
                        strokeWidth = w * 0.055f,
                        cap = StrokeCap.Round
                    )
                }
                drawCloud(cx = w * 0.44f, cy = h * 0.58f, scaleW = 1.05f, scaleH = 1.05f)
            }

            3 -> {
                drawCloud(cx = w / 2f, cy = h * 0.48f, scaleW = 1.25f, scaleH = 1.25f)
            }

            45, 48 -> {
                drawCloud(cx = w / 2f, cy = h * 0.38f, scaleW = 1.15f, scaleH = 1.05f)
                val strokeW = w * 0.055f
                drawLine(color = fogGrey, start = Offset(w * 0.22f, h * 0.70f), end = Offset(w * 0.78f, h * 0.70f), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color = fogGrey, start = Offset(w * 0.32f, h * 0.84f), end = Offset(w * 0.68f, h * 0.84f), strokeWidth = strokeW, cap = StrokeCap.Round)
            }

            51, 53, 55 -> {
                drawCloud(cx = w / 2f, cy = h * 0.40f, scaleW = 1.15f, scaleH = 1.05f)
                val dotR = w * 0.042f
                drawCircle(color = drizzleBlue, radius = dotR, center = Offset(w * 0.35f, h * 0.73f))
                drawCircle(color = drizzleBlue, radius = dotR, center = Offset(w * 0.51f, h * 0.82f))
                drawCircle(color = drizzleBlue, radius = dotR, center = Offset(w * 0.67f, h * 0.73f))
            }

            80 -> {
                val sunCx = w * 0.70f
                val sunCy = h * 0.32f
                val sunR = w * 0.20f
                drawCircle(color = sunYellow, radius = sunR, center = Offset(sunCx, sunCy))

                for (i in 0 until 5) {
                    val angle = (i * 72f) * (PI / 180f).toFloat()
                    val dist = sunR + w * 0.06f
                    drawLine(
                        color = sunYellow,
                        start = Offset(sunCx + dist * cos(angle), sunCy + dist * sin(angle)),
                        end = Offset(sunCx + (dist + w * 0.08f) * cos(angle), sunCy + (dist + w * 0.08f) * sin(angle)),
                        strokeWidth = w * 0.05f,
                        cap = StrokeCap.Round
                    )
                }
                drawCloud(cx = w * 0.42f, cy = h * 0.52f, scaleW = 1.0f, scaleH = 1.0f)

                val strokeW = w * 0.05f
                drawLine(color = rainBlue, start = Offset(w * 0.38f, h * 0.74f), end = Offset(w * 0.33f, h * 0.88f), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color = rainBlue, start = Offset(w * 0.54f, h * 0.74f), end = Offset(w * 0.49f, h * 0.88f), strokeWidth = strokeW, cap = StrokeCap.Round)
            }

            61, 63, 65, 81, 82 -> {
                drawCloud(cx = w / 2f, cy = h * 0.40f, scaleW = 1.15f, scaleH = 1.05f)
                val strokeW = w * 0.055f
                val rainLen = h * 0.16f
                for (k in 0..2) {
                    val sx = w * (0.33f + k * 0.17f)
                    val sy = h * 0.70f
                    drawLine(
                        color = rainBlue,
                        start = Offset(sx, sy),
                        end = Offset(sx - w * 0.055f, sy + rainLen),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                }
            }

            95, 96, 99 -> {
                drawCloud(cx = w / 2f, cy = h * 0.38f, scaleW = 1.15f, scaleH = 1.05f)

                val strokeW = w * 0.045f
                drawLine(color = rainBlue, start = Offset(w * 0.28f, h * 0.70f), end = Offset(w * 0.23f, h * 0.84f), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color = rainBlue, start = Offset(w * 0.72f, h * 0.70f), end = Offset(w * 0.67f, h * 0.84f), strokeWidth = strokeW, cap = StrokeCap.Round)

                val boltPath = Path().apply {
                    moveTo(w * 0.54f, h * 0.58f)
                    lineTo(w * 0.43f, h * 0.74f)
                    lineTo(w * 0.52f, h * 0.74f)
                    lineTo(w * 0.45f, h * 0.94f)
                    lineTo(w * 0.60f, h * 0.71f)
                    lineTo(w * 0.51f, h * 0.71f)
                    close()
                }
                drawPath(path = boltPath, color = lightningGold)
            }

            71, 73, 75, 77, 85, 86 -> {
                drawCloud(cx = w / 2f, cy = h * 0.40f, scaleW = 1.15f, scaleH = 1.05f)

                fun drawSnowflake(scx: Float, scy: Float, sr: Float) {
                    val sStroke = w * 0.035f
                    for (i in 0 until 3) {
                        val angle = (i * 60f) * (PI / 180f).toFloat()
                        drawLine(
                            color = snowCyan,
                            start = Offset(scx - sr * cos(angle), scy - sr * sin(angle)),
                            end = Offset(scx + sr * cos(angle), scy + sr * sin(angle)),
                            strokeWidth = sStroke,
                            cap = StrokeCap.Round
                        )
                    }
                }

                drawSnowflake(scx = w * 0.34f, scy = h * 0.75f, sr = w * 0.065f)
                drawSnowflake(scx = w * 0.51f, scy = h * 0.83f, sr = w * 0.065f)
                drawSnowflake(scx = w * 0.68f, scy = h * 0.75f, sr = w * 0.065f)
            }

            else -> {
                drawCloud(cx = w / 2f, cy = h * 0.48f, scaleW = 1.25f, scaleH = 1.25f)
            }
        }
    }
}

@Composable
fun SocialMediaButton(
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color(0xFF1E1E24),
        modifier = modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ==========================================
// 5. 提示遮罩與設定對話框
// ==========================================

@Composable
fun GestureTutorialOverlay(
    showDontShowAgain: Boolean = true,
    onDismiss: (dontShowAgain: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var dontShowAgain by remember { mutableStateOf(false) }

    val gestures = listOf(
        Triple(R.string.tutorial_single_tap_title, R.string.tutorial_single_tap_desc, R.drawable.guide_single_click),
        Triple(R.string.tutorial_double_tap_title, R.string.tutorial_double_tap_desc, R.drawable.guide_click_twice),
        Triple(R.string.tutorial_two_finger_tap_title, R.string.tutorial_two_finger_tap_desc, R.drawable.guide_double_click),
        Triple(R.string.tutorial_swipe_title, R.string.tutorial_swipe_desc, R.drawable.guide_swipe)
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.85f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .padding(bottom = 14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.tutorial_title),
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(0.92f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    gestures.take(2).forEach { item ->
                        TutorialGestureCard(
                            title = stringResource(item.first),
                            desc = stringResource(item.second),
                            iconRes = item.third,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(0.92f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    gestures.drop(2).forEach { item ->
                        TutorialGestureCard(
                            title = stringResource(item.first),
                            desc = stringResource(item.second),
                            iconRes = item.third,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                if (showDontShowAgain) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { dontShowAgain = !dontShowAgain }
                            .padding(end = 12.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = dontShowAgain,
                            onCheckedChange = { dontShowAgain = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF81C7D4),
                                uncheckedColor = Color(0xFFA0A0AA),
                                checkmarkColor = Color(0xFF041E49)
                            )
                        )
                        Text(
                            text = stringResource(R.string.tutorial_do_not_show_again),
                            color = Color(0xFFC4C4CC),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Surface(
                    onClick = { onDismiss(dontShowAgain) },
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF81C7D4),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .height(40.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.tutorial_btn_close),
                            color = Color(0xFF041E49),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialGestureCard(
    title: String,
    desc: String,
    iconRes: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E1E24)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(38.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = title,
                    color = Color(0xFF81C7D4),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = desc,
                    color = Color(0xFFE8E8EE),
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
fun LayoutTipOverlay(
    onDismiss: (dontShowAgain: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var dontShowAgain by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.85f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .padding(bottom = 14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.layout_tip_title),
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.layout_tip_desc),
                    color = Color(0xFFC4C4CC),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.85f)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(0.90f),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LayoutPreviewCard(
                        title = stringResource(R.string.layout_tip_single),
                        mode = ClockLayoutMode.SINGLE,
                        modifier = Modifier.weight(1f)
                    )
                    LayoutPreviewCard(
                        title = stringResource(R.string.layout_tip_dual_left),
                        mode = ClockLayoutMode.DUAL_LEFT_LARGE,
                        modifier = Modifier.weight(1f)
                    )
                    LayoutPreviewCard(
                        title = stringResource(R.string.layout_tip_dual_right),
                        mode = ClockLayoutMode.DUAL_RIGHT_LARGE,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { dontShowAgain = !dontShowAgain }
                        .padding(end = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF81C7D4),
                            uncheckedColor = Color(0xFFA0A0AA),
                            checkmarkColor = Color(0xFF041E49)
                        )
                    )
                    Text(
                        text = stringResource(R.string.tutorial_do_not_show_again),
                        color = Color(0xFFC4C4CC),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Surface(
                    onClick = { onDismiss(dontShowAgain) },
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF81C7D4),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .height(40.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.tutorial_btn_close),
                            color = Color(0xFF041E49),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LayoutPreviewCard(
    title: String,
    mode: ClockLayoutMode,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF1E1E24)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF2C2C34))
                    .padding(3.dp)
            ) {
                when (mode) {
                    ClockLayoutMode.SINGLE -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF81C7D4))
                        )
                    }
                    ClockLayoutMode.DUAL_LEFT_LARGE -> {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(0.7f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFF81C7D4))
                            )
                            Box(
                                modifier = Modifier
                                    .weight(0.3f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFF5DAC81))
                            )
                        }
                    }
                    ClockLayoutMode.DUAL_RIGHT_LARGE -> {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(0.3f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFF5DAC81))
                            )
                            Box(
                                modifier = Modifier
                                    .weight(0.7f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFF81C7D4))
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SwitchToDualLayoutDialog(
    onDismiss: () -> Unit,
    onKeepSingle: () -> Unit,
    onSelectLeftLarge: () -> Unit,
    onSelectRightLarge: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E1E22)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.layout_switch_dialog_title),
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.layout_switch_dialog_desc),
                    color = Color(0xFFC4C4CC),
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Surface(
                    onClick = {
                        onSelectLeftLarge()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF81C7D4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.layout_switch_opt_left),
                            color = Color(0xFF041E49),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    onClick = {
                        onSelectRightLarge()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF5DAC81),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.layout_switch_opt_right),
                            color = Color(0xFF041E49),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    onClick = {
                        onKeepSingle()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2C2C34),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.layout_switch_opt_no),
                            color = Color(0xFFA0A0AA),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NonFoldableDialog(
    onDismiss: () -> Unit,
    onAccept: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val youtubeUrl = "https://www.youtube.com/@Linzin"
    val bilibiliUrl = "https://space.bilibili.com/360811714"

    val rejectToasts = listOf(
        stringResource(R.string.toast_reject_1),
        stringResource(R.string.toast_reject_2),
        stringResource(R.string.toast_reject_3)
    )

    val acceptThanksText = stringResource(R.string.toast_accept_thanks)
    val acceptPermanentText = stringResource(R.string.toast_accept_permanent)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1E1E22)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. 大字標題
                Text(
                    text = stringResource(R.string.non_fold_dialog_title),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 2. 小字內容
                Text(
                    text = stringResource(R.string.non_fold_dialog_desc),
                    color = Color(0xFFC4C4CC),
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                // 3. 社群圓形按鈕
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { uriHandler.openUri(youtubeUrl) },
                        shape = CircleShape,
                        color = Color(0xFF2C2C34),
                        modifier = Modifier.size(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_youtube),
                                contentDescription = "YouTube",
                                colorFilter = ColorFilter.tint(Color.White),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = { uriHandler.openUri(bilibiliUrl) },
                        shape = CircleShape,
                        color = Color(0xFF2C2C34),
                        modifier = Modifier.size(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_bilibili),
                                contentDescription = "Bilibili",
                                colorFilter = ColorFilter.tint(Color.White),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // 4. 拒絕按鈕
                Surface(
                    onClick = {
                        val randomToast = rejectToasts[Random.nextInt(rejectToasts.size)]
                        Toast.makeText(context, randomToast, Toast.LENGTH_SHORT).show()
                        (context as? Activity)?.finishAffinity()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2C2C34),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.non_fold_btn_reject),
                            color = Color(0xFFE57373),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 5. 同意放行按鈕（計數與 Toast 切換核心邏輯）
                Surface(
                    onClick = {
                        val prefs = context.getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE)
                        val currentCount = prefs.getInt("pref_non_fold_bypass_count", 0) + 1
                        prefs.edit().putInt("pref_non_fold_bypass_count", currentCount).apply()

                        // 第 3 次按下時，跳出解鎖終身放行的專屬 Toast
                        val toastMsg = if (currentCount >= 3) acceptPermanentText else acceptThanksText
                        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()

                        onDismiss()
                        onAccept()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF81C7D4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.non_fold_btn_accept),
                            color = Color(0xFF041E49),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

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

// ==========================================
// 6. 初始歡迎畫面 (直立、展開與小摺引導)
// ==========================================

@Composable
fun InitialScreen(
    hasHingeSensor: Boolean = true,
    isClamshell: Boolean = false,
    onEnterClockDirectly: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var showNonFoldableDialog by remember { mutableStateOf(false) }

    val toastLiarFoldableText = stringResource(R.string.toast_liar_foldable)

    val onNotFoldableClick: () -> Unit = {
        if (hasHingeSensor && !isClamshell) {
            Toast.makeText(context, toastLiarFoldableText, Toast.LENGTH_SHORT).show()
        } else {
            showNonFoldableDialog = true
        }
    }

    val appIconBitmap = remember(context) {
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        val bitmap = android.graphics.Bitmap.createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1),
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    }

    val youtubeUrl = "https://www.youtube.com/@Linzin"
    val facebookUrl = "https://www.facebook.com/linzin86/"
    val instagramUrl = "https://www.instagram.com/linzin.yt"
    val githubUrl = "https://github.com"

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            val isLandscape = maxWidth > maxHeight

            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            bitmap = appIconBitmap,
                            contentDescription = "App Icon",
                            modifier = Modifier.size(88.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = stringResource(R.string.initial_prompt_fold),
                            color = Color.White,
                            fontSize = 17.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            onClick = onNotFoldableClick,
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF24242B),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.btn_not_foldable),
                                color = Color(0xFF81C7D4),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Made by: Linzin 阿哲, MaRi",
                            color = Color(0xFF8E8E93),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SocialMediaButton(
                                iconRes = R.drawable.ic_youtube,
                                onClick = { uriHandler.openUri(youtubeUrl) }
                            )
                            SocialMediaButton(
                                iconRes = R.drawable.ic_facebook,
                                onClick = { uriHandler.openUri(facebookUrl) }
                            )
                            SocialMediaButton(
                                iconRes = R.drawable.ic_instagram,
                                onClick = { uriHandler.openUri(instagramUrl) }
                            )
                            SocialMediaButton(
                                iconRes = R.drawable.ic_github,
                                onClick = { uriHandler.openUri(githubUrl) }
                            )
                        }
                    }
                }
            } else {
                // 自適應單一垂直容器：防止方塊比例內螢幕文字與按鈕重疊
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(top = 20.dp)
                    ) {
                        Image(
                            bitmap = appIconBitmap,
                            contentDescription = "App Icon",
                            modifier = Modifier.size(80.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = stringResource(R.string.initial_prompt_fold),
                            color = Color.White,
                            fontSize = 19.sp,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Surface(
                            onClick = onNotFoldableClick,
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF24242B)
                        ) {
                            Text(
                                text = stringResource(R.string.btn_not_foldable),
                                color = Color(0xFF81C7D4),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }

                        Text(
                            text = "Made by: Linzin 阿哲, MaRi",
                            color = Color(0xFF8E8E93),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SocialMediaButton(
                                iconRes = R.drawable.ic_youtube,
                                onClick = { uriHandler.openUri(youtubeUrl) }
                            )
                            SocialMediaButton(
                                iconRes = R.drawable.ic_facebook,
                                onClick = { uriHandler.openUri(facebookUrl) }
                            )
                            SocialMediaButton(
                                iconRes = R.drawable.ic_instagram,
                                onClick = { uriHandler.openUri(instagramUrl) }
                            )
                            SocialMediaButton(
                                iconRes = R.drawable.ic_github,
                                onClick = { uriHandler.openUri(githubUrl) }
                            )
                        }
                    }
                }
            }

            if (showNonFoldableDialog) {
                NonFoldableDialog(
                    onDismiss = { showNonFoldableDialog = false },
                    onAccept = {
                        showNonFoldableDialog = false
                        onEnterClockDirectly()
                    }
                )
            }
        }
    }
}

// ==========================================
// 7. 桌面時鐘主介面 (包含單/雙區塊編排)
// ==========================================

@Composable
fun DeskClockScreen(
    foldState: String = "--°",
    targetStateId: String = "3",
    hasHingeSensor: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val window = activity?.window

    val defaultCityStr = stringResource(R.string.city_default)
    val currentLocationStr = stringResource(R.string.current_location)

    val prefs = remember { context.getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE) }

    var burnInProtectionEnabled by remember {
        mutableStateOf(prefs.getBoolean("pref_burn_in_enabled", true))
    }
    var showGestureTutorial by remember {
        mutableStateOf(!prefs.getBoolean("pref_dismiss_gesture_tutorial", false))
    }
    var showLayoutTip by remember {
        mutableStateOf(!prefs.getBoolean("pref_dismiss_layout_tip", false))
    }
    var isTutorialFromSettings by remember { mutableStateOf(false) }

    var is24Hour by remember { mutableStateOf(prefs.getBoolean("pref_24_hour", false)) }
    var showBattery by remember { mutableStateOf(prefs.getBoolean("pref_show_battery", true)) }
    var showCornerDate by remember { mutableStateOf(prefs.getBoolean("pref_show_corner_date", true)) }
    var showHingeAngle by remember { mutableStateOf(prefs.getBoolean("pref_show_hinge_angle", false)) }
    var manualBrightness by remember { mutableStateOf(prefs.getBoolean("pref_manual_brightness", false)) }
    var brightnessValue by remember { mutableFloatStateOf(prefs.getFloat("pref_brightness_val", 0.6f)) }
    var timeoutMillis by remember { mutableLongStateOf(prefs.getLong("pref_timeout_millis", -1L)) }
    var layoutMode by remember {
        val savedModeStr = prefs.getString("pref_layout_mode", ClockLayoutMode.SINGLE.name)
        val mode = try { ClockLayoutMode.valueOf(savedModeStr ?: ClockLayoutMode.SINGLE.name) } catch (e: Exception) { ClockLayoutMode.SINGLE }
        mutableStateOf(mode)
    }

    var weatherEnabled by remember { mutableStateOf(prefs.getBoolean("pref_weather_enabled", false)) }
    var isFahrenheit by remember { mutableStateOf(prefs.getBoolean("pref_is_fahrenheit", false)) }
    var weatherInfo by remember { mutableStateOf<WeatherInfo?>(null) }

    var showCalendarEvents by remember {
        mutableStateOf(prefs.getBoolean("pref_show_calendar_events", false))
    }
    var calendarEventsList by remember { mutableStateOf<List<UpcomingCalendarEvent>>(emptyList()) }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        showCalendarEvents = isGranted
        if (isGranted) {
            calendarEventsList = fetchUpcomingCalendarEvents(context)
        }
    }

    var isUiLocked by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(is24Hour, showBattery, showCornerDate, showHingeAngle, manualBrightness, brightnessValue, timeoutMillis, layoutMode, weatherEnabled, isFahrenheit, burnInProtectionEnabled, showCalendarEvents) {
        prefs.edit()
            .putBoolean("pref_24_hour", is24Hour)
            .putBoolean("pref_show_battery", showBattery)
            .putBoolean("pref_show_corner_date", showCornerDate)
            .putBoolean("pref_show_hinge_angle", showHingeAngle)
            .putBoolean("pref_manual_brightness", manualBrightness)
            .putFloat("pref_brightness_val", brightnessValue)
            .putLong("pref_timeout_millis", timeoutMillis)
            .putString("pref_layout_mode", layoutMode.name)
            .putBoolean("pref_weather_enabled", weatherEnabled)
            .putBoolean("pref_is_fahrenheit", isFahrenheit)
            .putBoolean("pref_burn_in_enabled", burnInProtectionEnabled)
            .putBoolean("pref_show_calendar_events", showCalendarEvents) // <-- 補上這行
            .apply()
    }

    var showSettingsIcon by remember { mutableStateOf(false) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isBurnInProtectionActive by remember { mutableStateOf(false) }
    var isFakeSleeping by remember { mutableStateOf(false) }

    val batteryState = rememberBatteryState()

    val actualMainPageCount = 3
    val actualSidePageCount = 2
    val loopMultiplier = 10_000

    val savedMainPage = remember { prefs.getInt("pref_main_page", 0).coerceIn(0, actualMainPageCount - 1) }
    val savedSidePage = remember { prefs.getInt("pref_side_page", 0).coerceIn(0, actualSidePageCount - 1) }

    val mainPagerState = rememberPagerState(
        initialPage = (loopMultiplier / 2) * actualMainPageCount + savedMainPage,
        pageCount = { loopMultiplier * actualMainPageCount }
    )
    val sidePagerState = rememberPagerState(
        initialPage = (loopMultiplier / 2) * actualSidePageCount + savedSidePage,
        pageCount = { loopMultiplier * actualSidePageCount }
    )

    LaunchedEffect(mainPagerState.settledPage) {
        prefs.edit().putInt("pref_main_page", mainPagerState.settledPage % actualMainPageCount).apply()
    }
    LaunchedEffect(sidePagerState.settledPage) {
        prefs.edit().putInt("pref_side_page", sidePagerState.settledPage % actualSidePageCount).apply()
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        weatherEnabled = granted
    }

    // 天氣更新：僅在雙區塊模式且功能開啟時在背景輪詢以省電
    LaunchedEffect(weatherEnabled, layoutMode) {
        val isDualLayout = (layoutMode != ClockLayoutMode.SINGLE)

        if (weatherEnabled && isDualLayout) {
            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

            if (hasFine || hasCoarse) {
                while (true) {
                    withContext(Dispatchers.IO) {
                        try {
                            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                            val location: Location? = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                                ?: locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)

                            val lat = location?.latitude ?: 24.838
                            val lon = location?.longitude ?: 121.017

                            var detectedCity = defaultCityStr
                            try {
                                val geocoder = Geocoder(context, Locale.getDefault())
                                val addresses = geocoder.getFromLocation(lat, lon, 1)
                                if (!addresses.isNullOrEmpty()) {
                                    val addr = addresses[0]
                                    detectedCity = addr.locality
                                        ?: addr.subAdminArea
                                        ?: addr.adminArea
                                        ?: currentLocationStr
                                }
                            } catch (e: Exception) {
                                // 保持預設
                            }

                            val data = fetchOpenMeteoWeather(lat, lon, detectedCity)
                            withContext(Dispatchers.Main) {
                                weatherInfo = data
                            }
                        } catch (e: Exception) {
                            Log.e("DeskClockWeather", "Location error", e)
                        }
                    }
                    delay(30 * 60 * 1000L)
                }
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
        }
    }

    // 行程更新：僅在雙區塊模式且功能開啟時定時背景查詢
    LaunchedEffect(showCalendarEvents, layoutMode) {
        val isDualLayout = (layoutMode != ClockLayoutMode.SINGLE)

        if (showCalendarEvents && isDualLayout) {
            val hasCalPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED

            if (hasCalPerm) {
                while (true) {
                    calendarEventsList = fetchUpcomingCalendarEvents(context)
                    delay(10 * 60 * 1000L)
                }
            } else {
                calendarEventsList = emptyList()
            }
        } else if (!isDualLayout) {
            // 單區塊模式暫停查詢
        } else {
            calendarEventsList = emptyList()
        }
    }

    SystemBarsController(hideSystemBars = true)

    LaunchedEffect(manualBrightness, brightnessValue, isFakeSleeping) {
        window?.let { w ->
            val layoutParams = w.attributes
            layoutParams.screenBrightness = when {
                isFakeSleeping -> 0.0f
                manualBrightness -> brightnessValue.coerceIn(0.01f, 1f)
                else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            w.attributes = layoutParams
        }
    }

    LaunchedEffect(showSettingsIcon, lastInteractionTime) {
        if (showSettingsIcon && !isSettingsOpen) {
            delay(5000L)
            showSettingsIcon = false
        }
    }

    LaunchedEffect(lastInteractionTime, timeoutMillis) {
        isBurnInProtectionActive = false
        isFakeSleeping = false

        launch {
            delay(60_000L)
            isBurnInProtectionActive = true
        }

        val systemTimeout = try {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                15_000
            ).toLong()
        } catch (e: Exception) {
            15_000L
        }

        when {
            timeoutMillis < 0L -> {
                window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            timeoutMillis >= systemTimeout -> {
                window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val appWaitMillis = (timeoutMillis - systemTimeout).coerceAtLeast(0L)
                delay(appWaitMillis)
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            else -> {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                delay(timeoutMillis)
                isFakeSleeping = true
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed || it.previousPressed != it.pressed }) {
                            lastInteractionTime = System.currentTimeMillis()
                            if (isFakeSleeping) {
                                isFakeSleeping = false
                            } else {
                                showSettingsIcon = true
                            }
                        }
                    }
                }
            }
            .checkerboardBurnInProtection(isIdle = isBurnInProtectionActive && burnInProtectionEnabled)
    ) {
        when (layoutMode) {
            ClockLayoutMode.SINGLE -> {
                MainClockSection(
                    state = mainPagerState,
                    foldState = foldState,
                    is24Hour = is24Hour,
                    showCornerDate = showCornerDate,
                    showHingeAngle = showHingeAngle,
                    showBattery = showBattery,
                    batteryState = batteryState,
                    isUiLocked = isUiLocked,
                    modifier = Modifier.fillMaxSize()
                )
            }
            ClockLayoutMode.DUAL_LEFT_LARGE -> {
                Row(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.70f)
                            .background(Color.Black)
                            .clip(RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 24.dp, bottomEnd = 24.dp))
                    ) {
                        MainClockSection(
                            state = mainPagerState,
                            foldState = foldState,
                            is24Hour = is24Hour,
                            showCornerDate = showCornerDate,
                            showHingeAngle = showHingeAngle,
                            showBattery = showBattery,
                            batteryState = batteryState,
                            isUiLocked = isUiLocked,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.30f)
                            .background(Color.Black)
                            .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 0.dp, bottomEnd = 0.dp))
                    ) {
                        SideWidgetPager(
                            state = sidePagerState,
                            weatherEnabled = weatherEnabled,
                            weatherInfo = weatherInfo,
                            isFahrenheit = isFahrenheit,
                            isUiLocked = isUiLocked,
                            calendarEvents = calendarEventsList,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            ClockLayoutMode.DUAL_RIGHT_LARGE -> {
                Row(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.30f)
                            .background(Color.Black)
                            .clip(RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 24.dp, bottomEnd = 24.dp))
                    ) {
                        SideWidgetPager(
                            state = sidePagerState,
                            weatherEnabled = weatherEnabled,
                            weatherInfo = weatherInfo,
                            isFahrenheit = isFahrenheit,
                            isUiLocked = isUiLocked,
                            calendarEvents = calendarEventsList,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.70f)
                            .background(Color.Black)
                            .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 0.dp, bottomEnd = 0.dp))
                    ) {
                        MainClockSection(
                            state = mainPagerState,
                            foldState = foldState,
                            is24Hour = is24Hour,
                            showCornerDate = showCornerDate,
                            showHingeAngle = showHingeAngle,
                            showBattery = showBattery,
                            batteryState = batteryState,
                            isUiLocked = isUiLocked,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showSettingsIcon && !isFakeSleeping,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 20.dp, top = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    onClick = {
                        isUiLocked = !isUiLocked
                        lastInteractionTime = System.currentTimeMillis()
                    },
                    color = if (isUiLocked) Color(0xFFC62828).copy(alpha = 0.75f) else Color.Black.copy(alpha = 0.65f),
                    shape = CircleShape,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MaterialLockIcon(
                            isLocked = isUiLocked,
                            modifier = Modifier.size(20.dp),
                            color = Color.White
                        )
                    }
                }

                Surface(
                    onClick = {
                        if (!isUiLocked) {
                            isSettingsOpen = true
                            showSettingsIcon = false
                        }
                    },
                    color = if (isUiLocked) Color(0xFF2C2C34).copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.65f),
                    shape = CircleShape,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "⚙",
                            fontSize = 24.sp,
                            color = if (isUiLocked) Color(0xFF75757F) else Color.White
                        )
                    }
                }
            }
        }

        if (isFakeSleeping) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }

        if (isSettingsOpen) {
            ClockSettingsDialog(
                is24Hour = is24Hour,
                on24HourChange = { is24Hour = it },
                showBattery = showBattery,
                onShowBatteryChange = { showBattery = it },
                showCornerDate = showCornerDate,
                onShowCornerDateChange = { showCornerDate = it },
                showHingeAngle = showHingeAngle,
                onShowHingeAngleChange = { showHingeAngle = it },
                hasHingeSensor = hasHingeSensor,
                manualBrightness = manualBrightness,
                onManualBrightnessChange = { manualBrightness = it },
                brightnessValue = brightnessValue,
                onBrightnessValueChange = { brightnessValue = it },
                timeoutMillis = timeoutMillis,
                onTimeoutChange = { timeoutMillis = it },
                layoutMode = layoutMode,
                onLayoutModeChange = { layoutMode = it },
                weatherEnabled = weatherEnabled,
                onWeatherEnabledChange = { enabled ->
                    if (enabled) {
                        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (hasFine || hasCoarse) {
                            weatherEnabled = true
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            )
                        }
                    } else {
                        weatherEnabled = false
                    }
                },
                isFahrenheit = isFahrenheit,
                onFahrenheitChange = { isFahrenheit = it },
                showCalendarEvents = showCalendarEvents,
                onShowCalendarEventsChange = { enabled ->
                    if (enabled) {
                        val hasCalPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_CALENDAR
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCalPerm) {
                            showCalendarEvents = true
                            calendarEventsList = fetchUpcomingCalendarEvents(context)
                        } else {
                            calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                        }
                    } else {
                        showCalendarEvents = false
                        calendarEventsList = emptyList()
                    }
                },
                burnInProtectionEnabled = burnInProtectionEnabled,
                onBurnInProtectionChange = { burnInProtectionEnabled = it },
                onOpenTutorial = {
                    isSettingsOpen = false
                    isTutorialFromSettings = true
                    showGestureTutorial = true
                },
                onDismiss = { isSettingsOpen = false }
            )
        }

        if (showGestureTutorial) {
            GestureTutorialOverlay(
                showDontShowAgain = !isTutorialFromSettings,
                onDismiss = { dontShowAgain ->
                    showGestureTutorial = false
                    if (!isTutorialFromSettings && dontShowAgain) {
                        prefs.edit().putBoolean("pref_dismiss_gesture_tutorial", true).apply()
                    }
                    isTutorialFromSettings = false
                }
            )
        } else if (showLayoutTip) {
            LayoutTipOverlay(
                onDismiss = { dontShowAgain ->
                    showLayoutTip = false
                    if (dontShowAgain) {
                        prefs.edit().putBoolean("pref_dismiss_layout_tip", true).apply()
                    }
                }
            )
        }
    }
}

@Composable
fun MainClockSection(
    state: PagerState,
    foldState: String,
    is24Hour: Boolean,
    showCornerDate: Boolean,
    showHingeAngle: Boolean,
    showBattery: Boolean,
    batteryState: BatteryState,
    isUiLocked: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        MainClockPager(
            state = state,
            foldState = foldState,
            is24Hour = is24Hour,
            showCornerDate = showCornerDate,
            showHingeAngle = showHingeAngle,
            isUiLocked = isUiLocked,
            modifier = Modifier.fillMaxSize()
        )

        if (showBattery) {
            BatteryIndicator(
                batteryLevel = batteryState.level,
                isCharging = batteryState.isCharging,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 24.dp, top = 16.dp)
            )
        }
    }
}

@Composable
fun MainClockPager(
    state: PagerState,
    foldState: String,
    is24Hour: Boolean,
    showCornerDate: Boolean,
    showHingeAngle: Boolean,
    isUiLocked: Boolean,
    modifier: Modifier = Modifier
) {
    VerticalPager(
        state = state,
        userScrollEnabled = !isUiLocked,
        modifier = modifier
    ) { page ->
        when (page % 3) {
            0 -> DigitalClockFace(
                foldState = foldState,
                is24Hour = is24Hour,
                showCornerDate = showCornerDate,
                showHingeAngle = showHingeAngle,
                isUiLocked = isUiLocked
            )
            1 -> AnalogClockFace(
                foldState = foldState,
                showCornerDate = showCornerDate,
                showHingeAngle = showHingeAngle,
                isUiLocked = isUiLocked
            )
            2 -> FlipClockFace(
                foldState = foldState,
                is24Hour = is24Hour,
                showCornerDate = showCornerDate,
                showHingeAngle = showHingeAngle,
                isUiLocked = isUiLocked
            )
        }
    }
}

@Composable
fun SideWidgetPager(
    state: PagerState,
    weatherEnabled: Boolean,
    weatherInfo: WeatherInfo?,
    isFahrenheit: Boolean,
    isUiLocked: Boolean,
    calendarEvents: List<UpcomingCalendarEvent>,
    modifier: Modifier = Modifier
) {
    VerticalPager(
        state = state,
        userScrollEnabled = !isUiLocked,
        modifier = modifier
    ) { page ->
        when (page % 2) {
            0 -> SideDateWidget(calendarEvents = calendarEvents)
            1 -> SideWeatherWidget(
                weatherEnabled = weatherEnabled,
                weatherInfo = weatherInfo,
                isFahrenheit = isFahrenheit
            )
        }
    }
}

// ==========================================
// 8. 側邊面板元件 (日曆與天氣)
// ==========================================

@Composable
fun SideDateWidget(
    calendarEvents: List<UpcomingCalendarEvent> = emptyList(),
    modifier: Modifier = Modifier
) {
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val calendar = remember(currentTime) {
        Calendar.getInstance().apply { timeInMillis = currentTime }
    }

    val dayOfWeekStr = remember(currentTime) {
        SimpleDateFormat("EEE", Locale.US).format(Date(currentTime))
    }
    val monthShortStr = remember(currentTime) {
        SimpleDateFormat("MMM", Locale.US).format(Date(currentTime))
    }
    val dayOfMonthStr = remember(currentTime) {
        SimpleDateFormat("d", Locale.US).format(Date(currentTime))
    }
    val monthFullStr = remember(currentTime) {
        SimpleDateFormat("MMMM", Locale.US).format(Date(currentTime)).uppercase(Locale.US)
    }

    val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
    val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = remember(currentTime) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = currentTime
            set(Calendar.DAY_OF_MONTH, 1)
        }
        cal.get(Calendar.DAY_OF_WEEK) - 1
    }

    val daysOfWeek = listOf("S", "M", "T", "W", "T", "F", "S")

    val colorThu = Color(0xFF81C7D4)
    val colorAccent = Color(0xFF5DAC81)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val maxEventsToShow = when {
            maxHeight < 260.dp -> 1
            maxHeight < 355.dp -> 2
            else -> 3
        }
        val visibleEvents = calendarEvents.take(maxEventsToShow)
        val eventCount = visibleEvents.size

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 18.dp,
                    vertical = when {
                        eventCount >= 3 -> 8.dp
                        eventCount == 2 -> 10.dp
                        else -> 14.dp
                    }
                ),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dayOfWeekStr,
                        color = colorThu,
                        fontSize = if (eventCount >= 3) 25.sp else 27.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = monthShortStr,
                        color = Color.White,
                        fontSize = if (eventCount >= 3) 25.sp else 27.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                }

                val digitSize = when {
                    visibleEvents.isEmpty() -> 72.sp
                    eventCount >= 3 -> 48.sp
                    eventCount == 2 -> 54.sp
                    else -> 64.sp
                }

                Text(
                    text = dayOfMonthStr,
                    color = Color.White,
                    fontSize = digitSize,
                    lineHeight = digitSize,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            if (visibleEvents.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    visibleEvents.forEach { event ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color(0xFF1E1E24))
                                .padding(
                                    horizontal = 8.dp,
                                    vertical = if (eventCount >= 3) 2.5.dp else 3.5.dp
                                ),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(4.5.dp)
                                        .clip(CircleShape)
                                        .background(colorAccent)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = event.timeLabel,
                                    color = colorThu,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = TextStyle(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    )
                                )
                            }

                            Text(
                                text = event.title,
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                                style = TextStyle(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                )
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = monthFullStr,
                    color = colorAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    daysOfWeek.forEach { dayName ->
                        Text(
                            text = dayName,
                            color = Color(0xFF8E8E93),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(1.dp))

                val totalCells = ((firstDayOfWeek + maxDay + 6) / 7) * 7
                for (row in 0 until (totalCells / 7)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 0.6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (col in 0 until 7) {
                            val cellIndex = row * 7 + col
                            val dayNum = cellIndex - firstDayOfWeek + 1

                            if (dayNum in 1..maxDay) {
                                val isToday = (dayNum == currentDay)
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .then(
                                            if (isToday) Modifier.border(
                                                width = 1.2.dp,
                                                color = colorAccent,
                                                shape = CircleShape
                                            ) else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$dayNum",
                                        color = if (isToday) colorAccent else Color.White,
                                        fontSize = 8.5.sp,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                        textAlign = TextAlign.Center,
                                        style = TextStyle(
                                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                                        )
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SideWeatherWidget(
    weatherEnabled: Boolean,
    weatherInfo: WeatherInfo?,
    isFahrenheit: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        if (!weatherEnabled || weatherInfo == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "⛅", fontSize = 38.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.weather_disabled_title),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.weather_disabled_desc),
                    color = Color(0xFF8E8E93),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                ) {
                    LocationPinIcon(
                        modifier = Modifier.size(width = 12.dp, height = 16.dp),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = weatherInfo.cityName,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.70f)
                ) {
                    val availableWidth = maxWidth
                    val availableHeight = maxHeight
                    val iconSize = min(availableHeight * 0.92f, availableWidth * 0.58f).coerceIn(100.dp, 138.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTemp(weatherInfo.currentTempC, isFahrenheit),
                            color = Color.White,
                            fontSize = 50.sp,
                            lineHeight = 50.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.offset(y = 12.dp)
                        )

                        MaterialWeatherIcon(
                            weatherCode = weatherInfo.currentWeatherCode,
                            modifier = Modifier.size(iconSize)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.30f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    weatherInfo.hourlyList.take(4).forEachIndexed { index, hourly ->
                        if (index > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight(0.70f)
                                    .width(1.dp)
                                    .background(Color(0xFF2C2C34))
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Text(
                                text = formatTemp(hourly.tempC, isFahrenheit),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            MaterialWeatherIcon(
                                weatherCode = hourly.weatherCode,
                                modifier = Modifier.size(26.dp)
                            )

                            Text(
                                text = hourly.timeLabel,
                                color = Color(0xFFA0A0AA),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 9. 錶盤實作 (數位、指針、翻頁時鐘)
// ==========================================

fun getDigitalDigitDrawable(char: Char, stylePrefix: String): Int? {
    return when (stylePrefix) {
        "tk" -> when (char) {
            '0' -> R.drawable.tk_0
            '1' -> R.drawable.tk_1
            '2' -> R.drawable.tk_2
            '3' -> R.drawable.tk_3
            '4' -> R.drawable.tk_4
            '5' -> R.drawable.tk_5
            '6' -> R.drawable.tk_6
            '7' -> R.drawable.tk_7
            '8' -> R.drawable.tk_8
            '9' -> R.drawable.tk_9
            ':' -> R.drawable.tk_cl
            else -> null
        }
        "ttn" -> when (char) {
            '0' -> R.drawable.ttn_0
            '1' -> R.drawable.ttn_1
            '2' -> R.drawable.ttn_2
            '3' -> R.drawable.ttn_3
            '4' -> R.drawable.ttn_4
            '5' -> R.drawable.ttn_5
            '6' -> R.drawable.ttn_6
            '7' -> R.drawable.ttn_7
            '8' -> R.drawable.ttn_8
            '9' -> R.drawable.ttn_9
            ':' -> R.drawable.ttn_cl
            else -> null
        }
        "rafm" -> when (char) {
            '0' -> R.drawable.rafm_0
            '1' -> R.drawable.rafm_1
            '2' -> R.drawable.rafm_2
            '3' -> R.drawable.rafm_3
            '4' -> R.drawable.rafm_4
            '5' -> R.drawable.rafm_5
            '6' -> R.drawable.rafm_6
            '7' -> R.drawable.rafm_7
            '8' -> R.drawable.rafm_8
            '9' -> R.drawable.rafm_9
            ':' -> R.drawable.rafm_cl
            else -> null
        }
        else -> null
    }
}

fun getDigitalAmPmDrawable(isAM: Boolean, stylePrefix: String): Int {
    return when (stylePrefix) {
        "tk" -> if (isAM) R.drawable.tk_am else R.drawable.tk_pm
        "ttn" -> if (isAM) R.drawable.ttn_am else R.drawable.ttn_pm
        "rafm" -> if (isAM) R.drawable.rafm_am else R.drawable.rafm_pm
        else -> if (isAM) R.drawable.tk_am else R.drawable.tk_pm
    }
}

@Composable
fun ClockDigitImage(
    char: Char,
    stylePrefix: String = "tk",
    height: Dp = 160.dp
) {
    val drawableRes = getDigitalDigitDrawable(char, stylePrefix)

    drawableRes?.let { resId ->
        Image(
            painter = painterResource(id = resId),
            contentDescription = char.toString(),
            contentScale = ContentScale.FillHeight,
            modifier = Modifier.height(height)
        )
    }
}

@Composable
fun DigitalClockFace(
    foldState: String,
    is24Hour: Boolean = false,
    showCornerDate: Boolean = true,
    showHingeAngle: Boolean = true,
    isUiLocked: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE) }

    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    val availableStyles = remember { listOf("tk", "ttn", "rafm") }
    var currentStyleIndex by remember {
        mutableIntStateOf(prefs.getInt("pref_digital_style_idx", 0).coerceIn(0, availableStyles.size - 1))
    }
    val currentStyle = availableStyles[currentStyleIndex % availableStyles.size]

    var showSeconds by remember {
        mutableStateOf(prefs.getBoolean("pref_digital_show_sec", false))
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val timeString = remember(currentTime, is24Hour, showSeconds) {
        val pattern = when {
            is24Hour && showSeconds -> "HH:mm:ss"
            is24Hour && !showSeconds -> "HH:mm"
            !is24Hour && showSeconds -> "hh:mm:ss"
            else -> "hh:mm"
        }
        SimpleDateFormat(pattern, Locale.US).format(Date(currentTime))
    }

    val isAM = remember(currentTime) {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTime }
        cal.get(Calendar.AM_PM) == Calendar.AM
    }

    val dateString = remember(currentTime) {
        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(currentTime))
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .pointerInput(availableStyles, isUiLocked) {
                awaitPointerEventScope {
                    var lastSingleTapTime = 0L

                    while (true) {
                        val downEvent = awaitPointerEvent(PointerEventPass.Main)
                        val hasNewDown = downEvent.changes.any { it.pressed && !it.previousPressed }
                        if (!hasNewDown) continue

                        val startTime = System.currentTimeMillis()
                        var maxPointers = downEvent.changes.count { it.pressed }
                        var movedTooMuch = false

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val activePointers = event.changes.count { it.pressed }
                            if (activePointers > maxPointers) {
                                maxPointers = activePointers
                            }

                            if (event.changes.any {
                                    val dx = it.position.x - it.previousPosition.x
                                    val dy = it.position.y - it.previousPosition.y
                                    (dx * dx + dy * dy) > 1200f
                                }) {
                                movedTooMuch = true
                            }

                            if (event.changes.none { it.pressed }) break
                            if (System.currentTimeMillis() - startTime > 450L) break
                        }

                        val elapsed = System.currentTimeMillis() - startTime

                        if (!isUiLocked && !movedTooMuch && elapsed in 30L..450L) {
                            when (maxPointers) {
                                2 -> {
                                    currentStyleIndex = (currentStyleIndex + 1) % availableStyles.size
                                    prefs.edit().putInt("pref_digital_style_idx", currentStyleIndex).apply()
                                    lastSingleTapTime = 0L
                                }
                                1 -> {
                                    val now = System.currentTimeMillis()
                                    if (now - lastSingleTapTime < 320L) {
                                        showSeconds = !showSeconds
                                        prefs.edit().putBoolean("pref_digital_show_sec", showSeconds).apply()
                                        lastSingleTapTime = 0L
                                    } else {
                                        lastSingleTapTime = now
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        val heightBasedSize = maxHeight * 0.70f

        val widthDivider = when {
            showSeconds && is24Hour -> 5.5f
            showSeconds && !is24Hour -> 6.8f
            !showSeconds && is24Hour -> 3.5f
            else -> 4.5f
        }

        val calculatedHeight = (maxWidth - 48.dp) / widthDivider
        val finalDigitHeight = min(heightBasedSize, calculatedHeight)

        val animatedDigitHeight by animateDpAsState(
            targetValue = finalDigitHeight,
            animationSpec = tween(durationMillis = 300)
        )

        val dynamicSpacing = if (showSeconds) animatedDigitHeight * 0.02f else animatedDigitHeight * 0.03f
        val spacingToAmPm = if (showSeconds) animatedDigitHeight * 0.03f else animatedDigitHeight * 0.045f

        val amPmHeight = if (currentStyle == "rafm") {
            animatedDigitHeight * (if (showSeconds) 0.32f else 0.28f)
        } else {
            animatedDigitHeight * (if (showSeconds) 0.24f else 0.22f)
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(
                    dynamicSpacing,
                    Alignment.CenterHorizontally
                )
            ) {
                timeString.forEach { char ->
                    ClockDigitImage(
                        char = char,
                        stylePrefix = currentStyle,
                        height = animatedDigitHeight
                    )
                }
            }

            if (!is24Hour) {
                Spacer(modifier = Modifier.width(spacingToAmPm))

                val verticalOffset = if (currentStyle == "rafm") {
                    amPmHeight * 0.12f
                } else {
                    0.dp
                }

                Box(
                    modifier = Modifier
                        .height(animatedDigitHeight)
                        .align(Alignment.Bottom),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Image(
                        painter = painterResource(
                            id = getDigitalAmPmDrawable(isAM, currentStyle)
                        ),
                        contentDescription = if (isAM) "AM" else "PM",
                        contentScale = ContentScale.FillHeight,
                        modifier = Modifier
                            .height(amPmHeight)
                            .offset(y = verticalOffset)
                    )
                }
            }
        }

        if (showCornerDate) {
            Text(
                text = dateString,
                color = Color(0xFFC4C4CC),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 8.dp)
            )
        }

        if (showHingeAngle) {
            Text(
                text = "Hinge: $foldState",
                color = Color(0xFF5A5A62),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
fun AnalogClockFace(
    foldState: String,
    showCornerDate: Boolean = true,
    showHingeAngle: Boolean = true,
    isUiLocked: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE) }

    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    var analogStyleIndex by remember {
        mutableIntStateOf(prefs.getInt("pref_analog_style_idx", 0).coerceIn(0, 2))
    }

    var showSeconds by remember {
        mutableStateOf(prefs.getBoolean("pref_analog_show_sec", true))
    }

    val painterAbw12 = painterResource(id = R.drawable.abw_12)
    val painterAbw6 = painterResource(id = R.drawable.abw_6)
    val painterAbw3 = painterResource(id = R.drawable.abw_3)
    val painterAbw9 = painterResource(id = R.drawable.abw_9)

    val painterOet12 = painterResource(id = R.drawable.oet_12)
    val painterOet6 = painterResource(id = R.drawable.oet_6)
    val painterOet3 = painterResource(id = R.drawable.oet_3)
    val painterOet9 = painterResource(id = R.drawable.oet_9)

    val painterZmg12 = painterResource(id = R.drawable.zmg_12)
    val painterZmg6 = painterResource(id = R.drawable.zmg_6)
    val painterZmg3 = painterResource(id = R.drawable.zmg_3)
    val painterZmg9 = painterResource(id = R.drawable.zmg_9)

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val calendar = remember(currentTime) {
        Calendar.getInstance().apply { timeInMillis = currentTime }
    }
    val hours = calendar.get(Calendar.HOUR)
    val minutes = calendar.get(Calendar.MINUTE)
    val seconds = calendar.get(Calendar.SECOND)
    val dateString = remember(currentTime) {
        SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(currentTime)).uppercase()
    }

    val currentBgColor = when (analogStyleIndex) {
        0 -> Color(0xFFEED55E)
        1 -> Color(0xFF0D0D11)
        else -> Color(0xFF161413)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(currentBgColor)
            .pointerInput(isUiLocked) {
                awaitPointerEventScope {
                    var lastSingleTapTime = 0L

                    while (true) {
                        val downEvent = awaitPointerEvent(PointerEventPass.Main)
                        val hasNewDown = downEvent.changes.any { it.pressed && !it.previousPressed }
                        if (!hasNewDown) continue

                        val startTime = System.currentTimeMillis()
                        var maxPointers = downEvent.changes.count { it.pressed }
                        var movedTooMuch = false

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val activePointers = event.changes.count { it.pressed }
                            if (activePointers > maxPointers) {
                                maxPointers = activePointers
                            }

                            if (event.changes.any {
                                    val dx = it.position.x - it.previousPosition.x
                                    val dy = it.position.y - it.previousPosition.y
                                    (dx * dx + dy * dy) > 1200f
                                }) {
                                movedTooMuch = true
                            }

                            if (event.changes.none { it.pressed }) break
                            if (System.currentTimeMillis() - startTime > 450L) break
                        }

                        val elapsed = System.currentTimeMillis() - startTime
                        if (!isUiLocked && !movedTooMuch && elapsed in 30L..450L) {
                            when (maxPointers) {
                                2 -> {
                                    analogStyleIndex = (analogStyleIndex + 1) % 3
                                    prefs.edit().putInt("pref_analog_style_idx", analogStyleIndex).apply()
                                    lastSingleTapTime = 0L
                                }
                                1 -> {
                                    val now = System.currentTimeMillis()
                                    if (now - lastSingleTapTime < 320L) {
                                        showSeconds = !showSeconds
                                        prefs.edit().putBoolean("pref_analog_show_sec", showSeconds).apply()
                                        lastSingleTapTime = 0L
                                    } else {
                                        lastSingleTapTime = now
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val cx = width / 2f
            val cy = height / 2f
            val baseRadius = minOf(width, height) / 2f

            val hourAngleDeg = (hours + minutes / 60f + seconds / 3600f) * 30f - 90f
            val minAngleDeg = (minutes + seconds / 60f) * 6f - 90f
            val secAngleDeg = seconds * 6f - 90f

            when (analogStyleIndex) {
                0 -> {
                    for (i in 0 until 60) {
                        val angleDeg = i * 6f - 90f
                        val angleRad = (angleDeg * (PI / 180f)).toFloat()
                        val ux = cos(angleRad)
                        val uy = sin(angleRad)

                        val tx = if (ux > 0.00001f) (width / 2f) / ux else if (ux < -0.00001f) (-width / 2f) / ux else Float.MAX_VALUE
                        val ty = if (uy > 0.00001f) (height / 2f) / uy else if (uy < -0.00001f) (-height / 2f) / uy else Float.MAX_VALUE
                        val t = minOf(tx, ty)

                        val isHourTick = (i % 5 == 0)
                        val tickLength = if (isHourTick) 32.dp.toPx() else 18.dp.toPx()
                        val tickStroke = if (isHourTick) 3.5.dp.toPx() else 1.8.dp.toPx()
                        val tickColor = if (isHourTick) Color(0xFF181818) else Color(0xFF181818).copy(alpha = 0.55f)

                        val startX = cx + t * ux
                        val startY = cy + t * uy
                        val endX = startX - tickLength * ux
                        val endY = startY - tickLength * uy

                        drawLine(
                            color = tickColor,
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = tickStroke,
                            cap = StrokeCap.Square
                        )
                    }

                    val digitHeight = (minOf(width, height) * 0.16f).coerceIn(38.dp.toPx(), 54.dp.toPx())

                    val aspect12 = if (painterAbw12.intrinsicSize.height > 0) painterAbw12.intrinsicSize.width / painterAbw12.intrinsicSize.height else 0.80f
                    val w12 = digitHeight * aspect12
                    val y12 = 32.dp.toPx() + 8.dp.toPx() + digitHeight / 2f
                    translate(left = cx - w12 / 2f, top = y12 - digitHeight / 2f) {
                        with(painterAbw12) { draw(size = Size(w12, digitHeight)) }
                    }

                    val aspect6 = if (painterAbw6.intrinsicSize.height > 0) painterAbw6.intrinsicSize.width / painterAbw6.intrinsicSize.height else 0.65f
                    val w6 = digitHeight * aspect6
                    val y6 = height - (32.dp.toPx() + 8.dp.toPx() + digitHeight / 2f)
                    translate(left = cx - w6 / 2f, top = y6 - digitHeight / 2f) {
                        with(painterAbw6) { draw(size = Size(w6, digitHeight)) }
                    }

                    val aspect9 = if (painterAbw9.intrinsicSize.height > 0) painterAbw9.intrinsicSize.width / painterAbw9.intrinsicSize.height else 0.65f
                    val w9 = digitHeight * aspect9
                    val x9 = 32.dp.toPx() + 8.dp.toPx() + w9 / 2f
                    translate(left = x9 - w9 / 2f, top = cy - digitHeight / 2f) {
                        with(painterAbw9) { draw(size = Size(w9, digitHeight)) }
                    }

                    val aspect3 = if (painterAbw3.intrinsicSize.height > 0) painterAbw3.intrinsicSize.width / painterAbw3.intrinsicSize.height else 0.65f
                    val w3 = digitHeight * aspect3
                    val x3 = width - (32.dp.toPx() + 8.dp.toPx() + w3 / 2f)
                    translate(left = x3 - w3 / 2f, top = cy - digitHeight / 2f) {
                        with(painterAbw3) { draw(size = Size(w3, digitHeight)) }
                    }

                    val hourRad = (hourAngleDeg * (PI / 180f)).toFloat()
                    val hourLength = baseRadius * 0.52f
                    drawLine(
                        color = Color.White,
                        start = Offset(cx, cy),
                        end = Offset(cx + hourLength * cos(hourRad), cy + hourLength * sin(hourRad)),
                        strokeWidth = 8.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    val minRad = (minAngleDeg * (PI / 180f)).toFloat()
                    val minLength = baseRadius * 0.78f
                    drawLine(
                        color = Color.White,
                        start = Offset(cx, cy),
                        end = Offset(cx + minLength * cos(minRad), cy + minLength * sin(minRad)),
                        strokeWidth = 5.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    if (showSeconds) {
                        val secRad = (secAngleDeg * (PI / 180f)).toFloat()
                        val secLength = baseRadius * 0.88f
                        val tailLength = 22.dp.toPx()
                        drawLine(
                            color = Color(0xFF181818),
                            start = Offset(cx - tailLength * cos(secRad), cy - tailLength * sin(secRad)),
                            end = Offset(cx + secLength * cos(secRad), cy + secLength * sin(secRad)),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    drawCircle(color = Color.White, radius = 8.dp.toPx(), center = Offset(cx, cy))
                    if (showSeconds) {
                        drawCircle(color = Color(0xFF181818), radius = 3.5.dp.toPx(), center = Offset(cx, cy))
                    }
                }

                1 -> {
                    val vintageGold = Color(0xFFD4AF37)
                    val goldDim = Color(0xFF8C7326)
                    val steelWhite = Color(0xFFE8E8EE)

                    val outerTrackR = baseRadius * 0.92f
                    val innerTrackR = baseRadius * 0.85f

                    drawCircle(
                        color = goldDim.copy(alpha = 0.45f),
                        radius = outerTrackR,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                    drawCircle(
                        color = goldDim.copy(alpha = 0.45f),
                        radius = innerTrackR,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.2.dp.toPx())
                    )

                    for (i in 0 until 60) {
                        val angleDeg = i * 6f - 90f
                        val angleRad = (angleDeg * (PI / 180f)).toFloat()
                        val cosA = cos(angleRad)
                        val sinA = sin(angleRad)

                        if (i % 5 == 0) {
                            val diamondCenterR = (outerTrackR + innerTrackR) / 2f
                            val dx = cx + diamondCenterR * cosA
                            val dy = cy + diamondCenterR * sinA
                            val dSize = 3.2.dp.toPx()

                            val dPath = Path().apply {
                                moveTo(dx, dy - dSize)
                                lineTo(dx + dSize * 0.7f, dy)
                                lineTo(dx, dy + dSize)
                                lineTo(dx - dSize * 0.7f, dy)
                                close()
                            }
                            drawPath(path = dPath, color = vintageGold)
                        } else {
                            val startX = cx + innerTrackR * cosA
                            val startY = cy + innerTrackR * sinA
                            val endX = cx + outerTrackR * cosA
                            val endY = cy + outerTrackR * sinA

                            drawLine(
                                color = goldDim.copy(alpha = 0.5f),
                                start = Offset(startX, startY),
                                end = Offset(endX, endY),
                                strokeWidth = 1.dp.toPx(),
                                cap = StrokeCap.Square
                            )
                        }
                    }

                    val digitHeight = (baseRadius * 0.30f).coerceIn(40.dp.toPx(), 62.dp.toPx())
                    val numberOffsetR = innerTrackR * 0.76f

                    val aspect12 = if (painterOet12.intrinsicSize.height > 0) painterOet12.intrinsicSize.width / painterOet12.intrinsicSize.height else 0.85f
                    val w12 = digitHeight * aspect12
                    val y12 = cy - numberOffsetR
                    translate(left = cx - w12 / 2f, top = y12 - digitHeight / 2f) {
                        with(painterOet12) {
                            draw(size = Size(w12, digitHeight), colorFilter = ColorFilter.tint(vintageGold))
                        }
                    }

                    val aspect6 = if (painterOet6.intrinsicSize.height > 0) painterOet6.intrinsicSize.width / painterOet6.intrinsicSize.height else 0.65f
                    val w6 = digitHeight * aspect6
                    val y6 = cy + numberOffsetR
                    translate(left = cx - w6 / 2f, top = y6 - digitHeight / 2f) {
                        with(painterOet6) {
                            draw(size = Size(w6, digitHeight), colorFilter = ColorFilter.tint(vintageGold))
                        }
                    }

                    val aspect9 = if (painterOet9.intrinsicSize.height > 0) painterOet9.intrinsicSize.width / painterOet9.intrinsicSize.height else 0.65f
                    val w9 = digitHeight * aspect9
                    val x9 = cx - numberOffsetR
                    translate(left = x9 - w9 / 2f, top = cy - digitHeight / 2f) {
                        with(painterOet9) {
                            draw(size = Size(w9, digitHeight), colorFilter = ColorFilter.tint(vintageGold))
                        }
                    }

                    val aspect3 = if (painterOet3.intrinsicSize.height > 0) painterOet3.intrinsicSize.width / painterOet3.intrinsicSize.height else 0.65f
                    val w3 = digitHeight * aspect3
                    val x3 = cx + numberOffsetR
                    translate(left = x3 - w3 / 2f, top = cy - digitHeight / 2f) {
                        with(painterOet3) {
                            draw(size = Size(w3, digitHeight), colorFilter = ColorFilter.tint(vintageGold))
                        }
                    }

                    fun drawSwordHand(angleDeg: Float, length: Float, baseW: Float, color: Color) {
                        val rad = (angleDeg * (PI / 180f)).toFloat()
                        val perpRad = rad + (PI / 2f).toFloat()

                        val tipX = cx + length * cos(rad)
                        val tipY = cy + length * sin(rad)
                        val waistR = length * 0.72f
                        val waistW = baseW * 0.85f

                        val w1X = cx + waistR * cos(rad) + waistW * cos(perpRad)
                        val w1Y = cy + waistR * sin(rad) + waistW * sin(perpRad)
                        val w2X = cx + waistR * cos(rad) - waistW * cos(perpRad)
                        val w2Y = cy + waistR * sin(rad) - waistW * sin(perpRad)

                        val b1X = cx + baseW * cos(perpRad)
                        val b1Y = cy + baseW * sin(perpRad)
                        val b2X = cx - baseW * cos(perpRad)
                        val b2Y = cy - baseW * sin(perpRad)

                        val tailX = cx - (length * 0.18f) * cos(rad)
                        val tailY = cy - (length * 0.18f) * sin(rad)

                        val path = Path().apply {
                            moveTo(tipX, tipY)
                            lineTo(w1X, w1Y)
                            lineTo(b1X, b1Y)
                            lineTo(tailX, tailY)
                            lineTo(b2X, b2Y)
                            lineTo(w2X, w2Y)
                            close()
                        }

                        drawPath(path = path, color = color)
                        drawPath(path = path, color = vintageGold, style = Stroke(width = 1.2.dp.toPx()))
                    }

                    drawSwordHand(hourAngleDeg, baseRadius * 0.50f, 4.8.dp.toPx(), steelWhite)
                    drawSwordHand(minAngleDeg, baseRadius * 0.75f, 3.8.dp.toPx(), steelWhite)

                    if (showSeconds) {
                        val secRad = (secAngleDeg * (PI / 180f)).toFloat()
                        val secLength = baseRadius * 0.86f
                        val secTail = baseRadius * 0.22f
                        val secEndX = cx + secLength * cos(secRad)
                        val secEndY = cy + secLength * sin(secRad)
                        val secStartX = cx - secTail * cos(secRad)
                        val secStartY = cy - secTail * sin(secRad)

                        drawLine(
                            color = vintageGold,
                            start = Offset(secStartX, secStartY),
                            end = Offset(secEndX, secEndY),
                            strokeWidth = 1.6.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        val counterbalanceR = secTail * 0.65f
                        val cbX = cx - counterbalanceR * cos(secRad)
                        val cbY = cy - counterbalanceR * sin(secRad)
                        drawCircle(color = vintageGold, radius = 3.8.dp.toPx(), center = Offset(cbX, cbY))
                    }

                    drawCircle(color = vintageGold, radius = 6.5.dp.toPx(), center = Offset(cx, cy))
                    drawCircle(color = Color(0xFF0D0D11), radius = 2.8.dp.toPx(), center = Offset(cx, cy))
                }

                2 -> {
                    val color12 = Color(0xFFFFD166)
                    val color3 = Color(0xFF81C784)
                    val color6 = Color(0xFFFF708D)
                    val color9 = Color(0xFF90CAF9)

                    val handHourColor = Color(0xFFF3EEE6)
                    val handMinColor = Color(0xFFE09F67)
                    val lollipopRed = Color(0xFFFF5252)

                    val dotMajor = Color(0xFFE6DCCD)
                    val dotMinor = Color(0xFF4A403A)

                    val baseTrackR = baseRadius * 0.86f
                    val squircleExponent = 3.6f

                    for (i in 0 until 60) {
                        val angleDeg = i * 6f - 90f
                        val angleRad = (angleDeg * (PI / 180f)).toFloat()
                        val cosA = cos(angleRad)
                        val sinA = sin(angleRad)

                        val denominator = Math.pow(
                            Math.pow(Math.abs(cosA.toDouble()), squircleExponent.toDouble()) +
                                    Math.pow(Math.abs(sinA.toDouble()), squircleExponent.toDouble()),
                            1.0 / squircleExponent
                        ).toFloat()

                        val squircleR = if (denominator > 0.001f) (baseTrackR / denominator) else baseTrackR
                        val px = cx + squircleR * cosA
                        val py = cy + squircleR * sinA

                        if (i % 5 == 0) {
                            drawCircle(
                                color = dotMajor,
                                radius = 3.4.dp.toPx(),
                                center = Offset(px, py)
                            )
                        } else {
                            drawCircle(
                                color = dotMinor,
                                radius = 1.6.dp.toPx(),
                                center = Offset(px, py)
                            )
                        }
                    }

                    val digitHeight = (baseRadius * 0.28f).coerceIn(38.dp.toPx(), 58.dp.toPx())
                    val numberOffsetR = baseTrackR * 0.74f

                    val aspect12 = if (painterZmg12.intrinsicSize.height > 0) painterZmg12.intrinsicSize.width / painterZmg12.intrinsicSize.height else 0.80f
                    val w12 = digitHeight * aspect12
                    val y12 = cy - numberOffsetR
                    translate(left = cx - w12 / 2f, top = y12 - digitHeight / 2f) {
                        with(painterZmg12) {
                            draw(size = Size(w12, digitHeight), colorFilter = ColorFilter.tint(color12))
                        }
                    }

                    val aspect6 = if (painterZmg6.intrinsicSize.height > 0) painterZmg6.intrinsicSize.width / painterZmg6.intrinsicSize.height else 0.65f
                    val w6 = digitHeight * aspect6
                    val y6 = cy + numberOffsetR
                    translate(left = cx - w6 / 2f, top = y6 - digitHeight / 2f) {
                        with(painterZmg6) {
                            draw(size = Size(w6, digitHeight), colorFilter = ColorFilter.tint(color6))
                        }
                    }

                    val aspect9 = if (painterZmg9.intrinsicSize.height > 0) painterZmg9.intrinsicSize.width / painterZmg9.intrinsicSize.height else 0.65f
                    val w9 = digitHeight * aspect9
                    val x9 = cx - numberOffsetR
                    translate(left = x9 - w9 / 2f, top = cy - digitHeight / 2f) {
                        with(painterZmg9) {
                            draw(size = Size(w9, digitHeight), colorFilter = ColorFilter.tint(color9))
                        }
                    }

                    val aspect3 = if (painterZmg3.intrinsicSize.height > 0) painterZmg3.intrinsicSize.width / painterZmg3.intrinsicSize.height else 0.65f
                    val w3 = digitHeight * aspect3
                    val x3 = cx + numberOffsetR
                    translate(left = x3 - w3 / 2f, top = cy - digitHeight / 2f) {
                        with(painterZmg3) {
                            draw(size = Size(w3, digitHeight), colorFilter = ColorFilter.tint(color3))
                        }
                    }

                    val hourRad = (hourAngleDeg * (PI / 180f)).toFloat()
                    val hourLength = baseRadius * 0.48f
                    val hourTail = baseRadius * 0.10f
                    drawLine(
                        color = handHourColor,
                        start = Offset(cx - hourTail * cos(hourRad), cy - hourTail * sin(hourRad)),
                        end = Offset(cx + hourLength * cos(hourRad), cy + hourLength * sin(hourRad)),
                        strokeWidth = 9.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    val minRad = (minAngleDeg * (PI / 180f)).toFloat()
                    val minLength = baseRadius * 0.72f
                    val minTail = baseRadius * 0.10f
                    drawLine(
                        color = handMinColor,
                        start = Offset(cx - minTail * cos(minRad), cy - minTail * sin(minRad)),
                        end = Offset(cx + minLength * cos(minRad), cy + minLength * sin(minRad)),
                        strokeWidth = 6.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    if (showSeconds) {
                        val secRad = (secAngleDeg * (PI / 180f)).toFloat()
                        val secLength = baseRadius * 0.84f
                        val secTail = baseRadius * 0.16f

                        drawLine(
                            color = lollipopRed,
                            start = Offset(cx - secTail * cos(secRad), cy - secTail * sin(secRad)),
                            end = Offset(cx + secLength * cos(secRad), cy + secLength * sin(secRad)),
                            strokeWidth = 2.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        val lollipopHeadR = 5.2.dp.toPx()
                        val popX = cx + (secLength - lollipopHeadR * 0.4f) * cos(secRad)
                        val popY = cy + (secLength - lollipopHeadR * 0.4f) * sin(secRad)
                        drawCircle(
                            color = lollipopRed,
                            radius = lollipopHeadR,
                            center = Offset(popX, popY)
                        )
                    }

                    drawCircle(color = handHourColor, radius = 6.5.dp.toPx(), center = Offset(cx, cy))
                    if (showSeconds) {
                        drawCircle(color = lollipopRed, radius = 2.8.dp.toPx(), center = Offset(cx, cy))
                    }
                }
            }
        }

        if (showCornerDate) {
            val dateTextColor = when (analogStyleIndex) {
                0 -> Color(0xFF222222)
                1 -> Color(0xFF8E8E98)
                else -> Color(0xFF9E8F85)
            }
            Text(
                text = dateString,
                color = dateTextColor,
                fontSize = if (analogStyleIndex == 1) 13.sp else 14.sp,
                fontWeight = if (analogStyleIndex == 1) FontWeight.SemiBold else FontWeight.Bold,
                letterSpacing = if (analogStyleIndex == 1) 1.2.sp else 0.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 28.dp, bottom = 24.dp)
            )
        }

        if (showHingeAngle) {
            val hingeTextColor = when (analogStyleIndex) {
                0 -> Color(0xFF555555)
                1 -> Color(0xFF4A4A52)
                else -> Color(0xFF5C534D)
            }
            Text(
                text = "Hinge: $foldState",
                color = hingeTextColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 28.dp, bottom = 24.dp)
            )
        }
    }
}

fun getFlipDigitDrawable(char: Char, isTop: Boolean, stylePrefix: String = "pp"): Int? {
    return when (stylePrefix) {
        "pp" -> when (char) {
            '0' -> if (isTop) R.drawable.pp_0_u else R.drawable.pp_0_d
            '1' -> if (isTop) R.drawable.pp_1_u else R.drawable.pp_1_d
            '2' -> if (isTop) R.drawable.pp_2_u else R.drawable.pp_2_d
            '3' -> if (isTop) R.drawable.pp_3_u else R.drawable.pp_3_d
            '4' -> if (isTop) R.drawable.pp_4_u else R.drawable.pp_4_d
            '5' -> if (isTop) R.drawable.pp_5_u else R.drawable.pp_5_d
            '6' -> if (isTop) R.drawable.pp_6_u else R.drawable.pp_6_d
            '7' -> if (isTop) R.drawable.pp_7_u else R.drawable.pp_7_d
            '8' -> if (isTop) R.drawable.pp_8_u else R.drawable.pp_8_d
            '9' -> if (isTop) R.drawable.pp_9_u else R.drawable.pp_9_d
            else -> null
        }
        "fft" -> when (char) {
            '0' -> if (isTop) R.drawable.fft_0_u else R.drawable.fft_0_d
            '1' -> if (isTop) R.drawable.fft_1_u else R.drawable.fft_1_d
            '2' -> if (isTop) R.drawable.fft_2_u else R.drawable.fft_2_d
            '3' -> if (isTop) R.drawable.fft_3_u else R.drawable.fft_3_d
            '4' -> if (isTop) R.drawable.fft_4_u else R.drawable.fft_4_d
            '5' -> if (isTop) R.drawable.fft_5_u else R.drawable.fft_5_d
            '6' -> if (isTop) R.drawable.fft_6_u else R.drawable.fft_6_d
            '7' -> if (isTop) R.drawable.fft_7_u else R.drawable.fft_7_d
            '8' -> if (isTop) R.drawable.fft_8_u else R.drawable.fft_8_d
            '9' -> if (isTop) R.drawable.fft_9_u else R.drawable.fft_9_d
            else -> null
        }
        else -> null
    }
}

fun getFlipColonDrawable(stylePrefix: String): Int {
    return when (stylePrefix) {
        "fft" -> R.drawable.ftt_cl
        else -> R.drawable.pp_cl
    }
}

fun getFlipAmPmDrawable(isAM: Boolean, stylePrefix: String): Int {
    return when (stylePrefix) {
        "fft" -> if (isAM) R.drawable.fft_am else R.drawable.fft_pm
        else -> if (isAM) R.drawable.pp_am else R.drawable.pp_pm
    }
}

@Composable
fun FlipTopHalf(
    char: Char,
    halfHeight: Dp,
    stylePrefix: String = "pp",
    modifier: Modifier = Modifier
) {
    val resId = getFlipDigitDrawable(char, isTop = true, stylePrefix = stylePrefix)
    if (resId != null) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = char.toString(),
            contentScale = ContentScale.FillHeight,
            modifier = modifier.height(halfHeight)
        )
    }
}

@Composable
fun FlipBottomHalf(
    char: Char,
    halfHeight: Dp,
    stylePrefix: String = "pp",
    modifier: Modifier = Modifier
) {
    val resId = getFlipDigitDrawable(char, isTop = false, stylePrefix = stylePrefix)
    if (resId != null) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = char.toString(),
            contentScale = ContentScale.FillHeight,
            modifier = modifier.height(halfHeight)
        )
    }
}

@Composable
fun SingleFlipDigitCard(
    digit: Char,
    halfHeight: Dp,
    stylePrefix: String = "pp",
    modifier: Modifier = Modifier
) {
    var previousDigit by remember { mutableStateOf(digit) }
    var displayedDigit by remember { mutableStateOf(digit) }
    val rotation = remember { Animatable(0f) }
    val density = LocalDensity.current.density

    LaunchedEffect(digit) {
        if (digit != displayedDigit) {
            previousDigit = displayedDigit
            rotation.snapTo(0f)
            rotation.animateTo(
                targetValue = 180f,
                animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
            )
            displayedDigit = digit
            rotation.snapTo(0f)
        }
    }

    val currentRotation = rotation.value
    val isFlippingTop = currentRotation <= 90f
    val isAnimating = rotation.isRunning

    Box(
        modifier = modifier.wrapContentSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.wrapContentSize()
        ) {
            val staticTopDigit = if (isAnimating) digit else displayedDigit
            FlipTopHalf(char = staticTopDigit, halfHeight = halfHeight, stylePrefix = stylePrefix)

            Spacer(modifier = Modifier.height(0.5.dp))

            val staticBottomDigit = if (isAnimating) previousDigit else displayedDigit
            FlipBottomHalf(char = staticBottomDigit, halfHeight = halfHeight, stylePrefix = stylePrefix)
        }

        if (isAnimating) {
            if (isFlippingTop) {
                val topAngle = -currentRotation
                val shadowAlpha = (currentRotation / 90f) * 0.45f

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            cameraDistance = 16f * density
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            rotationX = topAngle
                        }
                ) {
                    FlipTopHalf(char = previousDigit, halfHeight = halfHeight, stylePrefix = stylePrefix)
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = shadowAlpha))
                    )
                }
            } else {
                val bottomAngle = 180f - currentRotation
                val shadowAlpha = ((180f - currentRotation) / 90f) * 0.45f

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .graphicsLayer {
                            cameraDistance = 16f * density
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            rotationX = bottomAngle
                        }
                ) {
                    FlipBottomHalf(char = digit, halfHeight = halfHeight, stylePrefix = stylePrefix)
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = shadowAlpha))
                    )
                }
            }
        }
    }
}

@Composable
fun FlipCard(
    value: String,
    halfHeight: Dp,
    stylePrefix: String = "pp",
    digitSpacing: Dp = 1.5.dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.wrapContentSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(digitSpacing)
    ) {
        value.forEach { char ->
            SingleFlipDigitCard(
                digit = char,
                halfHeight = halfHeight,
                stylePrefix = stylePrefix
            )
        }
    }
}

@Composable
fun FlipClockFace(
    foldState: String,
    is24Hour: Boolean = true,
    showCornerDate: Boolean = true,
    showHingeAngle: Boolean = true,
    isUiLocked: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE) }

    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    val availableStyles = remember { listOf("pp", "fft") }
    var currentStyleIndex by remember {
        mutableIntStateOf(prefs.getInt("pref_flip_style_idx", 0).coerceIn(0, availableStyles.size - 1))
    }
    val currentStyle = availableStyles[currentStyleIndex % availableStyles.size]

    var showSeconds by remember {
        mutableStateOf(prefs.getBoolean("pref_flip_show_sec", false))
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val hourPattern = if (is24Hour) "HH" else "hh"
    val hourString = remember(currentTime, is24Hour) {
        SimpleDateFormat(hourPattern, Locale.US).format(Date(currentTime))
    }
    val minString = remember(currentTime) {
        SimpleDateFormat("mm", Locale.US).format(Date(currentTime))
    }
    val secString = remember(currentTime) {
        SimpleDateFormat("ss", Locale.US).format(Date(currentTime))
    }
    val isAM = remember(currentTime) {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTime }
        cal.get(Calendar.AM_PM) == Calendar.AM
    }
    val dateString = remember(currentTime) {
        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(currentTime))
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .pointerInput(availableStyles, isUiLocked) {
                awaitPointerEventScope {
                    var lastSingleTapTime = 0L

                    while (true) {
                        val downEvent = awaitPointerEvent(PointerEventPass.Main)
                        val hasNewDown = downEvent.changes.any { it.pressed && !it.previousPressed }
                        if (!hasNewDown) continue

                        val startTime = System.currentTimeMillis()
                        var maxPointers = downEvent.changes.count { it.pressed }
                        var movedTooMuch = false

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val activePointers = event.changes.count { it.pressed }
                            if (activePointers > maxPointers) {
                                maxPointers = activePointers
                            }

                            if (event.changes.any {
                                    val dx = it.position.x - it.previousPosition.x
                                    val dy = it.position.y - it.previousPosition.y
                                    (dx * dx + dy * dy) > 1200f
                                }) {
                                movedTooMuch = true
                            }

                            if (event.changes.none { it.pressed }) break
                            if (System.currentTimeMillis() - startTime > 450L) break
                        }

                        val elapsed = System.currentTimeMillis() - startTime

                        if (!isUiLocked && !movedTooMuch && elapsed in 30L..450L) {
                            when (maxPointers) {
                                2 -> {
                                    currentStyleIndex = (currentStyleIndex + 1) % availableStyles.size
                                    prefs.edit().putInt("pref_flip_style_idx", currentStyleIndex).apply()
                                    lastSingleTapTime = 0L
                                }
                                1 -> {
                                    val now = System.currentTimeMillis()
                                    if (now - lastSingleTapTime < 320L) {
                                        showSeconds = !showSeconds
                                        prefs.edit().putBoolean("pref_flip_show_sec", showSeconds).apply()
                                        lastSingleTapTime = 0L
                                    } else {
                                        lastSingleTapTime = now
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        val targetWidth = maxWidth * 0.85f

        val widthDivider = if (showSeconds) {
            if (is24Hour) 5.15f else 5.65f
        } else {
            if (is24Hour) 3.30f else 3.80f
        }

        val calculatedCardHeight = targetWidth / widthDivider
        val finalCardHeight = min(calculatedCardHeight, maxHeight * 0.85f)

        val animatedCardHeight by animateDpAsState(
            targetValue = finalCardHeight,
            animationSpec = tween(durationMillis = 350)
        )
        val halfHeight = animatedCardHeight / 2f
        val cardSpacing = animatedCardHeight * 0.035f

        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            FlipCard(value = hourString, halfHeight = halfHeight, stylePrefix = currentStyle)

            Spacer(modifier = Modifier.width(cardSpacing))

            Image(
                painter = painterResource(id = getFlipColonDrawable(currentStyle)),
                contentDescription = ":",
                contentScale = ContentScale.FillHeight,
                modifier = Modifier.height(animatedCardHeight)
            )

            Spacer(modifier = Modifier.width(cardSpacing))

            FlipCard(value = minString, halfHeight = halfHeight, stylePrefix = currentStyle)

            if (showSeconds) {
                Spacer(modifier = Modifier.width(cardSpacing))

                Image(
                    painter = painterResource(id = getFlipColonDrawable(currentStyle)),
                    contentDescription = ":",
                    contentScale = ContentScale.FillHeight,
                    modifier = Modifier.height(animatedCardHeight)
                )

                Spacer(modifier = Modifier.width(cardSpacing))

                FlipCard(value = secString, halfHeight = halfHeight, stylePrefix = currentStyle)
            }

            if (!is24Hour) {
                Spacer(modifier = Modifier.width(cardSpacing * 1.2f))
                Box(
                    modifier = Modifier
                        .height(animatedCardHeight)
                        .align(Alignment.Bottom)
                ) {
                    Image(
                        painter = painterResource(
                            id = getFlipAmPmDrawable(isAM, currentStyle)
                        ),
                        contentDescription = if (isAM) "AM" else "PM",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(animatedCardHeight * 0.32f)
                            .align(Alignment.BottomStart)
                    )
                }
            }
        }

        if (showCornerDate) {
            Text(
                text = dateString,
                color = Color(0xFFC4C4CC),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = 10.dp)
            )
        }

        if (showHingeAngle) {
            Text(
                text = "Hinge: $foldState",
                color = Color(0xFF5A5A62),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 10.dp)
            )
        }
    }
}

// ==========================================
// 10. 主 Activity 實作 (轉軸監聽與狀態機調度)
// ==========================================

class MainActivity : ComponentActivity(), SensorEventListener {

    // 各品牌外螢幕狀態 ID 適配
    private val targetStateId: String by lazy {
        val brand = Build.MANUFACTURER.lowercase()
        when {
            brand.contains("honor") -> "4"
            brand.contains("google") -> "3"
            brand.contains("samsung") -> "1"
            brand.contains("oppo") || brand.contains("oneplus") -> "1"
            brand.contains("vivo") -> "1"
            brand.contains("xiaomi") -> "1"
            else -> "3"
        }
    }

    private val isHonorDevice: Boolean by lazy {
        Build.MANUFACTURER.lowercase().contains("honor")
    }

    /**
     * 判定是否為「開啟了三大虛擬導航按鍵」的三星裝置
     * 用於精準分流導覽列重置排程，避免全螢幕手勢機型產生黑塊過渡
     */
    private val isSamsungWithButtonNav: Boolean by lazy {
        val isSamsung = Build.MANUFACTURER.lowercase().contains("samsung")
        if (!isSamsung) return@lazy false

        try {
            val navMode = Settings.Secure.getInt(contentResolver, "navigation_mode", 0)
            val samsungGesture = Settings.Global.getInt(contentResolver, "navigation_bar_gesture_while_hidden", 0)
            navMode == 0 && samsungGesture == 0
        } catch (e: Exception) {
            true
        }
    }

    /**
     * 安全釋放全域覆寫鎖定 (Reset Device State)
     * 針對 HONOR (MagicOS) 採用雙重間隔補發機制以確保退出鎖死
     */
    private fun safelyResetDeviceState() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val p1 = ProcessBuilder("sh", "-c", "cmd device_state state reset").start()
                p1.waitFor()
                Log.d("FoldDeskClock", "Executed: cmd device_state state reset")

                if (isHonorDevice) {
                    delay(120L)
                    val p2 = ProcessBuilder("sh", "-c", "cmd device_state state reset").start()
                    p2.waitFor()
                }
            } catch (e: Exception) {
                Log.e("FoldDeskClock", "Failed to reset device state", e)
            }
        }
    }

    /**
     * 導覽列收合排程：
     * 僅針對「啟用三大虛擬導航按鍵的三星摺疊機」重置 Insets 狀態機以壓制幽靈按鍵；
     * 其他機型 (含手勢導航三星) 直接全螢幕隱藏，保證零閃爍、無過渡黑塊。
     */
    private fun triggerImmersiveCleanUp() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (!isSamsungWithButtonNav) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            window.navigationBarColor = AndroidGraphicsColor.BLACK
            window.statusBarColor = AndroidGraphicsColor.BLACK

            delay(350L)
            controller.show(WindowInsetsCompat.Type.navigationBars())
            window.decorView.requestApplyInsets()

            delay(150L)
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private var hasHingeSensor = false
    private var isClamshell = false
    private var lastRecordedHingeAngle: Float = -1f
    private var isNonFoldableBypassed = false

    private var foldState by mutableStateOf("--°")
    private var showClockScreen by mutableStateOf(false)
    private lateinit var sensorManager: SensorManager
    private var hingeAngleSensor: Sensor? = null

    private var isRearDisplayActive = false

    private fun isTallAspectRatio(): Boolean {
        val metrics = resources.displayMetrics
        val maxDim = maxOf(metrics.widthPixels, metrics.heightPixels).toFloat()
        val minDim = minOf(metrics.widthPixels, metrics.heightPixels).toFloat()
        return (maxDim / minDim) >= 2.05f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 鎖死視窗底色為純黑，防止螢幕切換/旋轉瞬間漏出系統白底
        window.setBackgroundDrawableResource(android.R.color.black)
        window.statusBarColor = AndroidGraphicsColor.BLACK
        window.navigationBarColor = AndroidGraphicsColor.BLACK

        // 允許畫面延伸至挖孔/瀏海區域
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        hingeAngleSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        hasHingeSensor = (hingeAngleSensor != null)

        if (!hasHingeSensor) {
            foldState = "NO_HINGE"
            val prefs = getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE)
            val bypassCount = prefs.getInt("pref_non_fold_bypass_count", 0)
            if (bypassCount >= 3) {
                isNonFoldableBypassed = true
                showClockScreen = true
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }

        setContent {
            MaterialTheme(colorScheme = DeskClockColorScheme) {
                BackHandler(enabled = showClockScreen) {
                    val prefs = getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE)
                    val bypassCount = prefs.getInt("pref_non_fold_bypass_count", 0)

                    // 若是非摺疊機且已達成永久放行門檻，按返回鍵直接退出整個 App
                    if (!hasHingeSensor && bypassCount >= 3) {
                        finish()
                    } else {
                        // 一般大摺疊機：恢復原本狀態並退回引導頁面
                        isRearDisplayActive = false
                        isNonFoldableBypassed = false
                        showClockScreen = false
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        safelyResetDeviceState()
                    }
                }

                if (showClockScreen) {
                    DeskClockScreen(
                        foldState = foldState,
                        targetStateId = targetStateId,
                        hasHingeSensor = hasHingeSensor
                    )
                } else {
                    InitialScreen(
                        hasHingeSensor = hasHingeSensor,
                        isClamshell = isClamshell || (hasHingeSensor && isTallAspectRatio() && lastRecordedHingeAngle >= 160f),
                        onEnterClockDirectly = {
                            isNonFoldableBypassed = true
                            showClockScreen = true
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hingeAngleSensor?.let {
            sensorManager.unregisterListener(this)
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        // 從後台切回 APP 時，若正在時鐘介面，重新恢復全螢幕沉浸模式
        if (showClockScreen) {
            setImmersiveMode(window, true)
            val prefs = getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE)
            val isPermanentBypass = !hasHingeSensor && prefs.getInt("pref_non_fold_bypass_count", 0) >= 3
            if (isPermanentBypass) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 只要視窗重新拿到焦點 (從桌面切回、手勢動畫結束等)，且處於時鐘畫面，就強制壓下狀態欄
        if (hasFocus && showClockScreen) {
            setImmersiveMode(window, true)
        }
    }

    override fun onPause() {
        super.onPause()
        // 若處於外螢幕時鐘畫面，保留感應器監聽以確保能感應後續展開動作
        if (!showClockScreen) {
            sensorManager.unregisterListener(this)
        }
    }

    override fun onStop() {
        super.onStop()
        sensorManager.unregisterListener(this)

        val prefs = getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE)
        val isPermanentBypass = !hasHingeSensor && prefs.getInt("pref_non_fold_bypass_count", 0) >= 3

        // 如果是已永久放行的非摺疊機，按 Home 鍵退回後台時，不要重置 showClockScreen
        if (isPermanentBypass) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setImmersiveMode(window, false)
            return
        }

        // 一般大摺疊機：離開 APP 時釋放全域覆寫鎖定並重置狀態
        if (isRearDisplayActive || showClockScreen) {
            isRearDisplayActive = false
            showClockScreen = false
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            setImmersiveMode(window, false)
            safelyResetDeviceState()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        setImmersiveMode(window, false)
        safelyResetDeviceState()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_HINGE_ANGLE) {
            val currentAngle = event.values[0]
            lastRecordedHingeAngle = currentAngle
            foldState = "${currentAngle.toInt()}°"

            if (isClamshell || isNonFoldableBypassed) {
                return
            }

            // 完全展開 (> 150°)：無條件退出外螢幕並回歸內螢幕
            if (currentAngle > 150f) {
                if (showClockScreen || isRearDisplayActive) {
                    isRearDisplayActive = false
                    showClockScreen = false
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    setImmersiveMode(window, false)
                    safelyResetDeviceState()
                }
                return
            }

            // 完全閉合 (<= 5°)：退出時鐘，恢復預設外螢幕狀態
            if (currentAngle <= 5f) {
                if (showClockScreen || isRearDisplayActive) {
                    isRearDisplayActive = false
                    showClockScreen = false
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    setImmersiveMode(window, false)
                    safelyResetDeviceState()
                }
                return
            }

            // 半摺角度 (30° ~ 120°)：觸發外螢幕橫向時鐘
            if (currentAngle in 30f..120f) {
                if (!showClockScreen) {
                    showClockScreen = true
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE

                    val needsStateOverride = !isTallAspectRatio() || !isRearDisplayActive
                    if (needsStateOverride) {
                        isRearDisplayActive = true
                        executeShellCommand("cmd device_state state $targetStateId")
                    }

                    triggerImmersiveCleanUp()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        fun setImmersiveMode(window: Window, enable: Boolean) {
            WindowCompat.setDecorFitsSystemWindows(window, !enable)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (enable) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}