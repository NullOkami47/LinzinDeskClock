package com.linzin.linzindeskclock.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.linzin.linzindeskclock.R
import com.linzin.linzindeskclock.calendar.UpcomingCalendarEvent
import com.linzin.linzindeskclock.calendar.buildCalendarEventViewIntent
import com.linzin.linzindeskclock.calendar.formatCalendarEventDateTimes
import java.util.Locale

@Composable
fun CalendarEventDetailsDialog(
    event: UpcomingCalendarEvent,
    onDismissRequest: () -> Unit,
    onOpenInCalendar: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0] ?: Locale.ROOT
    val dateTimes = formatCalendarEventDateTimes(
        event, locale = locale, use24Hour = DateFormat.is24HourFormat(context)
    )
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = (configuration.screenHeightDp.dp - 48.dp).coerceAtLeast(120.dp))
                .testTag("calendar_event_details"),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1E1E22),
            contentColor = Color.White
        ) {
            // Title, fields and actions share a scroll container, including at large font scales.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.calendar_event_details),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF81C7D4),
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.testTag("calendar_detail_title")
                )
                if (event.isAllDay) {
                    Text(stringResource(R.string.event_all_day), color = Color(0xFF81C7D4))
                }
                if (dateTimes != null) {
                    CalendarDetailField(stringResource(R.string.calendar_start), dateTimes.start, "calendar_detail_start")
                    CalendarDetailField(stringResource(R.string.calendar_end), dateTimes.end, "calendar_detail_end")
                } else {
                    Text(event.timeLabel)
                }
                event.location?.takeIf { it.isNotBlank() }?.let {
                    CalendarDetailField(stringResource(R.string.calendar_location), it, "calendar_detail_location")
                }
                event.calendarName?.takeIf { it.isNotBlank() }?.let {
                    CalendarDetailField(stringResource(R.string.calendar_name), it, "calendar_detail_calendar")
                }
                event.description?.takeIf { it.isNotBlank() }?.let {
                    CalendarDetailField(stringResource(R.string.calendar_description), it, "calendar_detail_description")
                }
                if (buildCalendarEventViewIntent(event) != null) {
                    TextButton(
                        onClick = onOpenInCalendar,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("calendar_open_in_app")
                    ) {
                        Text(stringResource(R.string.calendar_open_in_app), color = Color(0xFF81C7D4))
                    }
                }
                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("calendar_details_close")
                ) {
                    Text(stringResource(R.string.calendar_close), color = Color(0xFF81C7D4))
                }
            }
        }
    }
}

@Composable
private fun CalendarDetailField(label: String, value: String, tag: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color(0xFF81C7D4))
        Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag(tag))
    }
}
