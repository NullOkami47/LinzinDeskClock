package com.linzin.linzindeskclock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.linzin.linzindeskclock.media.SeekGeometry

/** The track and thumb are painted in one coordinate space, inside a 32/40 dp hit region. */
@Composable
internal fun MediaSeekBar(
    value: Float,
    enabled: Boolean,
    description: String,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onSeeking: (Float?) -> Unit = {},
) {
    var drag by remember(enabled) { mutableStateOf<Float?>(null) }
    val latestSeek by rememberUpdatedState(onSeek)
    val latestSeeking by rememberUpdatedState(onSeeking)
    val latestEnabled by rememberUpdatedState(enabled)
    val reversed = LocalLayoutDirection.current == LayoutDirection.Rtl
    val radiusPx = with(LocalDensity.current) { 4.dp.toPx() }
    val shownValue = (drag ?: value).let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }

    fun commit(fraction: Float) {
        if (latestEnabled && fraction.isFinite()) latestSeek(fraction.coerceIn(0f, 1f))
        drag = null
        latestSeeking(null)
    }

    Box(modifier.testTag("media_seek")
        .semantics {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo(shownValue, 0f..1f)
            if (!enabled) disabled()
            else setProgress { requested ->
                if (!latestEnabled || !requested.isFinite() || requested.coerceIn(0f, 1f) == shownValue) false
                else { commit(requested); true }
            }
        }
        .onKeyEvent { event ->
            if (!enabled || event.type != KeyEventType.KeyDown) false
            else {
                val direction = if (reversed) -1f else 1f
                val target = when (event.key) {
                    Key.DirectionLeft -> shownValue - 0.05f * direction
                    Key.DirectionRight -> shownValue + 0.05f * direction
                    Key.DirectionDown -> shownValue - 0.05f
                    Key.DirectionUp -> shownValue + 0.05f
                    Key.MoveHome -> 0f
                    Key.MoveEnd -> 1f
                    else -> return@onKeyEvent false
                }
                commit(target)
                true
            }
        }
        .focusable(enabled)
        .pointerInput(enabled, reversed, radiusPx) {
            if (enabled) detectTapGestures { position ->
                commit(SeekGeometry(size.width.toFloat(), size.height.toFloat(), radiusPx).fractionAt(position.x, reversed))
            }
        }
        .pointerInput(enabled, reversed, radiusPx) {
            if (!enabled) return@pointerInput
            var pending: Float? = null
            fun update(x: Float) {
                val fraction = SeekGeometry(size.width.toFloat(), size.height.toFloat(), radiusPx).fractionAt(x, reversed)
                pending = fraction
                drag = fraction
                latestSeeking(fraction)
            }
            try {
                detectHorizontalDragGestures(
                    onDragStart = { update(it.x) },
                    onHorizontalDrag = { change, _ -> update(change.position.x) },
                    onDragEnd = { pending?.let(::commit); pending = null },
                    onDragCancel = { pending = null; drag = null; latestSeeking(null) },
                )
            } finally {
                // Lock, metadata/session replacement and disposal cancel a gesture without seeking.
                drag = null
                latestSeeking(null)
            }
        }) {
        Canvas(Modifier.fillMaxSize().testTag("media_progress_track")) {
            val geometry = SeekGeometry(size.width, size.height, radiusPx)
            val start = Offset(geometry.startX, geometry.centreY)
            val end = Offset(geometry.endX, geometry.centreY)
            val thumb = Offset(geometry.thumbX(shownValue, reversed), geometry.centreY)
            val trackWidth = 4.dp.toPx()
            drawLine(Color.White.copy(alpha = 0.20f), start, end, trackWidth, StrokeCap.Round)
            if (shownValue > 0f) drawLine(Color.White.copy(alpha = if (enabled) 0.95f else 0.38f),
                if (reversed) end else start, thumb, trackWidth, StrokeCap.Round)
            drawCircle(Color.White.copy(alpha = if (enabled) 1f else 0.38f), geometry.thumbRadius, thumb)
        }
    }
}
