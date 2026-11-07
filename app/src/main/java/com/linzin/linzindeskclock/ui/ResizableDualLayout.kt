package com.linzin.linzindeskclock.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.linzin.linzindeskclock.R
import com.linzin.linzindeskclock.layout.ClockSide
import com.linzin.linzindeskclock.layout.SplitLayoutState
import com.linzin.linzindeskclock.layout.geometry
import kotlin.math.roundToInt

private val DividerTouchWidth = 48.dp
private val DividerLineWidth = 1.dp
private val DividerGripWidth = 4.dp
private val DividerGripHeight = 40.dp
private const val AccessibleRatioStep = 0.05f

/**
 * A horizontal split with a physical (not RTL-relative) divider. Supply bounded width and height.
 * [state] belongs to the parent; [onStateChange] must synchronously update that hoisted state.
 *
 * Every drag delta is reported without snapping. Only a successful pointer release calls
 * SplitLayoutState.release(), reports its result, then calls [onDragFinished] for persistence.
 * Cancellation (including locking during a drag) keeps the last reported ratio without snapping
 * or calling onDragFinished. Locking also disables keyboard and accessibility adjustments.
 *
 * Accessibility set-progress, named move actions and left/right arrow keys commit an exact
 * clamped value and call onDragFinished; they are not pointer releases and do not snap. This also
 * lets small accessibility increments move away from 50/50 instead of being trapped by snapping.
 *
 * Clock and widgets have permanent, separate composition slots. Only their measured widths and
 * physical positions change, so remember/rememberSaveable/effects survive crossing the centre.
 * Keep both PagerStates in the parent and pass the appropriate one through each content lambda.
 * This composable never creates a pager or reads/writes preferences.
 */
@Composable
fun ResizableDualLayout(
    state: SplitLayoutState,
    onStateChange: (SplitLayoutState) -> Unit,
    onDragFinished: () -> Unit,
    isUiLocked: Boolean,
    clock: @Composable () -> Unit,
    widgets: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentState by rememberUpdatedState(state)
    val currentOnStateChange by rememberUpdatedState(onStateChange)
    val currentOnDragFinished by rememberUpdatedState(onDragFinished)
    val currentIsUiLocked by rememberUpdatedState(isUiLocked)
    var containerWidthPx by remember { mutableIntStateOf(0) }
    // Accumulate deltas locally: multiple input events can arrive before the parent recomposes.
    var draggingState by remember { mutableStateOf<SplitLayoutState?>(null) }
    val displayedState = if (isUiLocked) state else draggingState ?: state

    val dividerLabel = stringResource(R.string.split_divider_label)
    val moveLeftLabel = stringResource(R.string.split_move_left)
    val moveRightLabel = stringResource(R.string.split_move_right)
    val clockSideLabel = stringResource(
        if (displayedState.clockSide == ClockSide.LEFT) R.string.split_clock_left else R.string.split_clock_right
    )
    val leftPercent = (displayedState.ratio * 100).roundToInt()
    val splitDescription = stringResource(R.string.split_divider_state, leftPercent, 100 - leftPercent, clockSideLabel)
    val description = if (isUiLocked) {
        stringResource(R.string.split_divider_locked, splitDescription)
    } else {
        splitDescription
    }
    val dividerColour = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isUiLocked) 0.25f else 0.65f)

    val adjustRatio: (Float) -> Boolean = { ratio ->
        if (currentIsUiLocked || draggingState != null || !ratio.isFinite()) {
            false
        } else {
            val adjusted = currentState.withRatio(ratio)
            if (adjusted == currentState) {
                false
            } else {
                currentOnStateChange(adjusted)
                currentOnDragFinished()
                true
            }
        }
    }

    Layout(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { containerWidthPx = it.width }
            .drawWithContent {
                drawContent()
                val splitX = displayedState.geometry(size.width.roundToInt(), 0).splitPx.toFloat()
                drawLine(
                    color = dividerColour,
                    start = Offset(splitX, 0f),
                    end = Offset(splitX, size.height),
                    strokeWidth = DividerLineWidth.toPx()
                )
                val gripWidth = DividerGripWidth.toPx()
                val gripHeight = DividerGripHeight.toPx().coerceAtMost(size.height)
                drawRoundRect(
                    color = dividerColour,
                    topLeft = Offset(splitX - gripWidth / 2f, (size.height - gripHeight) / 2f),
                    size = Size(gripWidth, gripHeight),
                    cornerRadius = CornerRadius(gripWidth / 2f)
                )
            },
        content = {
            // Never branch or reorder these two slots when clockSide changes.
            Box(Modifier.clipToBounds(), propagateMinConstraints = true) { clock() }
            Box(Modifier.clipToBounds(), propagateMinConstraints = true) { widgets() }
            Box(
                Modifier
                    .pointerInput(isUiLocked) {
                        if (!isUiLocked) {
                            try {
                                detectHorizontalDragGestures(
                                    onDragStart = { draggingState = currentState },
                                    onHorizontalDrag = { change, amount ->
                                        if (!currentIsUiLocked) {
                                            val previous = draggingState ?: currentState
                                            val next = previous.dragBy(amount, containerWidthPx.toFloat())
                                            draggingState = next
                                            if (next != previous) currentOnStateChange(next)
                                            change.consume()
                                        }
                                    },
                                    onDragEnd = {
                                        val released = draggingState?.release()
                                        draggingState = null
                                        if (!currentIsUiLocked && released != null) {
                                            currentOnStateChange(released)
                                            currentOnDragFinished()
                                        }
                                    },
                                    onDragCancel = { draggingState = null }
                                )
                            } finally {
                                // pointerInput is cancelled when locked or removed from composition.
                                draggingState = null
                            }
                        }
                    }
                    .semantics {
                        contentDescription = dividerLabel
                        stateDescription = description
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            displayedState.ratio, SplitLayoutState.MIN_RATIO..SplitLayoutState.MAX_RATIO
                        )
                        if (isUiLocked) {
                            disabled()
                        } else {
                            setProgress { adjustRatio(it) }
                            customActions = listOf(
                                CustomAccessibilityAction(moveLeftLabel) {
                                    adjustRatio(currentState.ratio - AccessibleRatioStep)
                                },
                                CustomAccessibilityAction(moveRightLabel) {
                                    adjustRatio(currentState.ratio + AccessibleRatioStep)
                                }
                            )
                        }
                    }
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || currentIsUiLocked) {
                            false
                        } else {
                            when (event.key) {
                                Key.DirectionLeft -> adjustRatio(currentState.ratio - AccessibleRatioStep)
                                Key.DirectionRight -> adjustRatio(currentState.ratio + AccessibleRatioStep)
                                else -> false
                            }
                        }
                    }
                    .focusable(enabled = !isUiLocked)
            )
        }
    ) { measurables, constraints ->
        require(constraints.hasBoundedWidth && constraints.hasBoundedHeight) {
            "ResizableDualLayout requires bounded width and height"
        }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val geometry = displayedState.geometry(width, DividerTouchWidth.roundToPx())
        val clockPanel = measurables[0].measure(Constraints.fixed(geometry.clockWidthPx, height))
        val widgetsPanel = measurables[1].measure(Constraints.fixed(geometry.widgetsWidthPx, height))
        val divider = measurables[2].measure(Constraints.fixed(geometry.hitWidthPx, height))
        layout(width, height) {
            // place(), not placeRelative(): stored sides always mean physical left/right.
            clockPanel.place(geometry.clockLeftPx, 0)
            widgetsPanel.place(geometry.widgetsLeftPx, 0)
            divider.place(geometry.hitLeftPx, 0, zIndex = 1f)
        }
    }
}
