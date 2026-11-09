package com.linzin.linzindeskclock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.linzin.linzindeskclock.R
import kotlin.math.abs

/** Drawn above the sleeping/UI-lock layers. Only the bottom edge owns the exit drag. */
@Composable
fun ClockExitOverlay(onExit: () -> Unit, modifier: Modifier = Modifier) {
    val exit by rememberUpdatedState(onExit)
    val label = stringResource(R.string.auto_exit)
    Box(modifier.fillMaxSize()) {
        IconButton(onClick = exit, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            .size(48.dp).testTag("clock_exit_button").semantics { contentDescription = label }) {
            Canvas(Modifier.size(18.dp)) {
                drawLine(Color.LightGray,Offset(2f,2f),Offset(size.width-2,size.height-2),2.dp.toPx(),StrokeCap.Round)
                drawLine(Color.LightGray,Offset(size.width-2,2f),Offset(2f,size.height-2),2.dp.toPx(),StrokeCap.Round)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(12.dp).testTag("clock_exit_edge")
            .semantics { contentDescription = label; onClick { exit(); true } }
            .pointerInput(Unit) {
                val threshold = 80.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    down.consume()
                    var latest = down.position
                    var valid = true
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if(event.changes.count { it.pressed } > 1) valid = false
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        latest = change.position
                        change.consume()
                        if(!change.pressed) break
                    } while(true)
                    val dy = down.position.y - latest.y
                    val dx = abs(latest.x-down.position.x)
                    if(valid && dy >= threshold && dy > dx * 1.5f) exit()
                }
            },contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.padding(bottom=4.dp)) {
                Box(Modifier.width(36.dp).height(2.dp).testTag("clock_exit_handle").background(Color.LightGray.copy(alpha=.6f),RoundedCornerShape(2.dp)))
            }
        }
    }
}
