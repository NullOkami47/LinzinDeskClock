package com.linzin.linzindeskclock.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.linzin.linzindeskclock.R
import com.linzin.linzindeskclock.calendar.CalendarEventOpenResult
import com.linzin.linzindeskclock.calendar.UpcomingCalendarEvent
import com.linzin.linzindeskclock.calendar.canShowCalendarEventDetails
import com.linzin.linzindeskclock.calendar.openCalendarEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun SideDateWidget(
    calendarEvents: List<UpcomingCalendarEvent> = emptyList(),
    modifier: Modifier = Modifier,
    interactionsEnabled: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedEvent by remember { mutableStateOf<UpcomingCalendarEvent?>(null) }
    val viewDetailsLabel = stringResource(R.string.calendar_view_details)
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Never retain private details when the host leaves the foreground or screen turns off.
    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                selectedEvent = null
            }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) selectedEvent = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }

    val activeEvent = selectedEvent?.takeIf {
        interactionsEnabled && it in calendarEvents && canShowCalendarEventDetails(context)
    }
    LaunchedEffect(activeEvent) {
        if (activeEvent == null) selectedEvent = null
    }
    if (activeEvent != null) {
        CalendarEventDetailsDialog(
            event = activeEvent,
            onDismissRequest = { selectedEvent = null },
            onOpenInCalendar = {
                if (interactionsEnabled) {
                    val result = openCalendarEvent(context, activeEvent)
                    if (result != CalendarEventOpenResult.UNAVAILABLE) selectedEvent = null
                } else {
                    selectedEvent = null
                }
            }
        )
    }

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
        val containerWidth = maxWidth
        val scale = (maxHeight / 360.dp).coerceIn(1.0f, 1.85f)
        val horizontalPad = (18.dp * scale).coerceAtMost(36.dp)

        val maxEventsToShow = when {
            maxHeight < 280.dp -> 1
            maxHeight < 360.dp -> 2
            else -> 3
        }
        val visibleEvents = calendarEvents.take(maxEventsToShow)
        val eventCount = visibleEvents.size
        val hasEvents = visibleEvents.isNotEmpty()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = horizontalPad,
                    vertical = when {
                        hasEvents -> 10.dp * scale
                        else -> 14.dp * scale
                    }
                ),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // =========================================================
            // 1. 頂部日期區塊（依據是否有行程自動切換單行 / 雙行排版）
            // =========================================================
            if (hasEvents) {
                // 👉 有行程：收納成一行 "Sun Nov 8"，字體等大，騰出空間給下方的行程卡片
                val singleLineFontSize = (22f * scale).sp
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp * scale)
                ) {
                    Text(
                        text = dayOfWeekStr,
                        color = colorThu,
                        fontSize = singleLineFontSize,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                    )
                    Spacer(modifier = Modifier.width(6.dp * scale))
                    Text(
                        text = monthShortStr,
                        color = Color.White,
                        fontSize = singleLineFontSize,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                    )
                    Spacer(modifier = Modifier.width(6.dp * scale))
                    Text(
                        text = dayOfMonthStr,
                        color = Color.White,
                        fontSize = singleLineFontSize,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                    )
                }
            } else {
                // 👉 無行程：維持原有的雙行制大日期排版
                Column {
                    val headerFontSize = (27f * scale).sp
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = dayOfWeekStr,
                            color = colorThu,
                            fontSize = headerFontSize,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                        Spacer(modifier = Modifier.width(8.dp * scale))
                        Text(
                            text = monthShortStr,
                            color = Color.White,
                            fontSize = headerFontSize,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                    }

                    val digitSize = (72f * scale).sp
                    Text(
                        text = dayOfMonthStr,
                        color = Color.White,
                        fontSize = digitSize,
                        lineHeight = digitSize,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                        modifier = Modifier.padding(top = 2.dp * scale)
                    )
                }
            }

            // =========================================================
            // 2. 中間近期行程卡片
            // =========================================================
            if (hasEvents) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp * scale),
                    verticalArrangement = Arrangement.spacedBy(4.dp * scale)
                ) {
                    visibleEvents.forEachIndexed { index, event ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("calendar_event_row_$index")
                                .clip(RoundedCornerShape((7 * scale).dp))
                                .background(Color(0xFF1E1E24))
                                .clickable(
                                    enabled = interactionsEnabled,
                                    role = Role.Button,
                                    onClickLabel = viewDetailsLabel
                                ) {
                                    selectedEvent = if (canShowCalendarEventDetails(context)) event else null
                                }
                                .padding(
                                    horizontal = 10.dp * scale,
                                    vertical = (if (eventCount >= 3) 3.5.dp else 4.5.dp) * scale
                                ),
                            verticalArrangement = Arrangement.spacedBy(1.5.dp * scale)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp * scale)
                                        .clip(CircleShape)
                                        .background(colorAccent)
                                )
                                Spacer(modifier = Modifier.width(5.dp * scale))
                                Text(
                                    text = event.timeLabel,
                                    color = colorThu,
                                    fontSize = (9.5f * scale).sp,
                                    fontWeight = FontWeight.Bold,
                                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                )
                            }

                            Text(
                                text = event.title,
                                color = Color.White,
                                fontSize = (11f * scale).sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                            )
                        }
                    }
                }
            }

            // =========================================================
            // 3. 底部月曆網格
            // =========================================================
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = monthFullStr,
                    color = colorAccent,
                    fontSize = (10f * scale).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(bottom = 3.dp * scale)
                )

                val availableWidth = containerWidth - (horizontalPad * 2)
                val cellSize = (availableWidth / 7.6f).coerceIn(16.dp, 32.dp)
                val cellFontSize = (cellSize.value * 0.52f).sp

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    daysOfWeek.forEach { dayName ->
                        Text(
                            text = dayName,
                            color = Color(0xFF8E8E93),
                            fontSize = (cellFontSize.value * 0.95f).sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(cellSize)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp * scale))

                val totalCells = ((firstDayOfWeek + maxDay + 6) / 7) * 7
                for (row in 0 until (totalCells / 7)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = (0.6f * scale).dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (col in 0 until 7) {
                            val cellIndex = row * 7 + col
                            val dayNum = cellIndex - firstDayOfWeek + 1

                            if (dayNum in 1..maxDay) {
                                val isToday = (dayNum == currentDay)
                                Box(
                                    modifier = Modifier
                                        .size(cellSize)
                                        .then(
                                            if (isToday) Modifier.border(
                                                width = (1.2f * scale).dp,
                                                color = colorAccent,
                                                shape = CircleShape
                                            ) else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$dayNum",
                                        color = if (isToday) colorAccent else Color.White,
                                        fontSize = cellFontSize,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                        textAlign = TextAlign.Center,
                                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(cellSize))
                            }
                        }
                    }
                }
            }
        }
    }
}
