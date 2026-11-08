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
                    visibleEvents.forEachIndexed { index, event ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("calendar_event_row_$index")
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color(0xFF1E1E24))
                                .clickable(
                                    enabled = interactionsEnabled,
                                    role = Role.Button,
                                    onClickLabel = viewDetailsLabel
                                ) {
                                    selectedEvent = if (canShowCalendarEventDetails(context)) event else null
                                }
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
