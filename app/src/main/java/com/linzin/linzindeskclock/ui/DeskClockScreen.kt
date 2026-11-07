package com.linzin.linzindeskclock.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.provider.Settings
import android.util.Log
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.linzin.linzindeskclock.autostart.ClockEntryMode
import com.linzin.linzindeskclock.settings.AutoClockPreferences
import com.linzin.linzindeskclock.ui.ClockExitOverlay
import com.linzin.linzindeskclock.ui.ResizableDualLayout
import com.linzin.linzindeskclock.layout.ClockSide
import com.linzin.linzindeskclock.layout.SplitLayoutPreferences
import com.linzin.linzindeskclock.layout.SplitLayoutState
import com.linzin.linzindeskclock.*
import com.linzin.linzindeskclock.settings.ClockSettingsDialog

@Composable
fun DeskClockScreen(
    foldState: String = "--°",
    targetStateId: String = "3",
    hasHingeSensor: Boolean = true,
    modifier: Modifier = Modifier,
    entryMode: ClockEntryMode = ClockEntryMode.MANUAL,
    windowOverride: Window? = null,
    onExit: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val window = windowOverride ?: activity?.window
    val session = entryMode == ClockEntryMode.LOCK_SCREEN || entryMode == ClockEntryMode.DREAM
    val clockContentModifier = Modifier.fillMaxSize().padding(bottom = if(session) 32.dp else 0.dp)
    val allowSettings = !session
    val uiLockDescription = stringResource(R.string.auto_ui_lock)
    val settingsDescription = stringResource(R.string.settings_title)
    val keyguard = remember { context.getSystemService(android.app.KeyguardManager::class.java) }
    val autoPreferences = remember { AutoClockPreferences(context) }
    var hideMediaWhenLocked by remember { mutableStateOf(autoPreferences.hideMediaWhenLocked) }
    DisposableEffect(autoPreferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _,_ ->
            hideMediaWhenLocked = autoPreferences.hideMediaWhenLocked
        }
        autoPreferences.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { autoPreferences.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    DisposableEffect(window) {
        val oldBrightness = window?.attributes?.screenBrightness
        val oldKeepScreen = (window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        onDispose {
            if(entryMode != ClockEntryMode.LOCK_SCREEN) window?.let { w ->
                w.attributes = w.attributes.apply { screenBrightness = oldBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
                if(!oldKeepScreen) w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    val defaultCityStr = stringResource(R.string.city_default)
    val currentLocationStr = stringResource(R.string.current_location)

    val prefs = remember { context.getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE) }

    var burnInProtectionEnabled by remember {
        mutableStateOf(prefs.getBoolean("pref_burn_in_enabled", true))
    }
    var showGestureTutorial by remember {
        mutableStateOf(allowSettings && !prefs.getBoolean("pref_dismiss_gesture_tutorial", false))
    }
    var showLayoutTip by remember {
        mutableStateOf(allowSettings && !prefs.getBoolean("pref_dismiss_layout_tip", false))
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

    var splitState by remember { mutableStateOf(SplitLayoutPreferences.load(prefs)) }
    val selectLayout: (ClockLayoutMode) -> Unit = { mode ->
        layoutMode = mode
        if (mode != ClockLayoutMode.SINGLE) {
            val requestedSide = if (mode == ClockLayoutMode.DUAL_LEFT_LARGE) ClockSide.LEFT else ClockSide.RIGHT
            if (splitState.clockSide != requestedSide) {
                splitState = SplitLayoutState(1f - splitState.ratio, requestedSide)
                SplitLayoutPreferences.save(prefs, splitState)
            }
        }
    }

    var weatherEnabled by remember { mutableStateOf(prefs.getBoolean("pref_weather_enabled", false)) }
    var isFahrenheit by remember { mutableStateOf(prefs.getBoolean("pref_is_fahrenheit", false)) }
    var weatherInfo by remember { mutableStateOf<WeatherInfo?>(null) }

    var showCalendarEvents by remember {
        mutableStateOf(prefs.getBoolean("pref_show_calendar_events", false))
    }
    var calendarEventsList by remember { mutableStateOf<List<UpcomingCalendarEvent>>(emptyList()) }

    val calendarPermissionLauncher = if (allowSettings) rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        showCalendarEvents = isGranted
        if (isGranted) {
            calendarEventsList = fetchUpcomingCalendarEvents(context)
        }
    } else null

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
    val actualSidePageCount = 3
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

    val locationPermissionLauncher = if (allowSettings) rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        weatherEnabled = granted
    } else null

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
            }
        }
    }

    // 行程更新：僅在雙區塊模式且功能開啟時定時背景查詢
    LaunchedEffect(showCalendarEvents, layoutMode) {
        val isDualLayout = (layoutMode != ClockLayoutMode.SINGLE)

        if (showCalendarEvents && isDualLayout && !session) {
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
                    modifier = clockContentModifier
                )
            }
            ClockLayoutMode.DUAL_LEFT_LARGE, ClockLayoutMode.DUAL_RIGHT_LARGE -> {
                ResizableDualLayout(
                    modifier = clockContentModifier,
                    state = splitState,
                    onStateChange = { splitState = it },
                    onDragFinished = {
                        SplitLayoutPreferences.save(prefs, splitState)
                        layoutMode = if (splitState.clockSide == ClockSide.LEFT) ClockLayoutMode.DUAL_LEFT_LARGE else ClockLayoutMode.DUAL_RIGHT_LARGE
                    },
                    isUiLocked = isUiLocked,
                    clock = {
                        Box(Modifier.fillMaxSize().padding(horizontal=4.dp).clip(RoundedCornerShape(24.dp))) {
                            MainClockSection(
                                state = mainPagerState, foldState = foldState, is24Hour = is24Hour,
                                showCornerDate = showCornerDate, showHingeAngle = showHingeAngle,
                                showBattery = showBattery, batteryState = batteryState,
                                isUiLocked = isUiLocked, modifier = Modifier.fillMaxSize()
                            )
                        }
                    },
                    widgets = {
                        Box(Modifier.fillMaxSize().padding(horizontal=4.dp).clip(RoundedCornerShape(24.dp))) {
                            SideWidgetPager(
                                state = sidePagerState, weatherEnabled = weatherEnabled, weatherInfo = weatherInfo,
                                isFahrenheit = isFahrenheit, isUiLocked = isUiLocked,
                                calendarEvents = if (session) emptyList() else calendarEventsList,
                                isLocked = session || keyguard.isKeyguardLocked,
                                hideMediaWhenLocked = hideMediaWhenLocked, modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = showSettingsIcon && !isFakeSleeping,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = if (session) 64.dp else 20.dp, top = 14.dp)
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
                    modifier = Modifier.size(46.dp).testTag("clock_ui_lock").semantics { contentDescription = uiLockDescription }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MaterialLockIcon(
                            isLocked = isUiLocked,
                            modifier = Modifier.size(20.dp),
                            color = Color.White
                        )
                    }
                }

                if (allowSettings) Surface(
                    onClick = {
                        if (!isUiLocked) {
                            isSettingsOpen = true
                            showSettingsIcon = false
                        }
                    },
                    color = if (isUiLocked) Color(0xFF2C2C34).copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.65f),
                    shape = CircleShape,
                    modifier = Modifier.size(46.dp).testTag("clock_settings").semantics { contentDescription = settingsDescription }
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

        if (isSettingsOpen && allowSettings) {
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
                onLayoutModeChange = selectLayout,
                weatherEnabled = weatherEnabled,
                onWeatherEnabledChange = { enabled ->
                    if (enabled) {
                        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (hasFine || hasCoarse) {
                            weatherEnabled = true
                        } else {
                            locationPermissionLauncher?.launch(
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
                            calendarPermissionLauncher?.launch(Manifest.permission.READ_CALENDAR)
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
        if (session) ClockExitOverlay(onExit = onExit)
    }
}

