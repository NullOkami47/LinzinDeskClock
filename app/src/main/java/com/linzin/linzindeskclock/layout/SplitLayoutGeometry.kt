package com.linzin.linzindeskclock.layout

import kotlin.math.roundToInt

/** Physical pixel placement. The hit target is an overlay and reserves no panel space. */
internal data class SplitLayoutGeometry(
    val splitPx: Int,
    val clockLeftPx: Int,
    val clockWidthPx: Int,
    val widgetsLeftPx: Int,
    val widgetsWidthPx: Int,
    val hitLeftPx: Int,
    val hitWidthPx: Int
)

internal fun SplitLayoutState.geometry(totalWidthPx: Int, requestedHitWidthPx: Int): SplitLayoutGeometry {
    require(totalWidthPx >= 0 && requestedHitWidthPx >= 0)
    val leftWidth = (totalWidthPx * ratio).roundToInt().coerceIn(0, totalWidthPx)
    val rightWidth = totalWidthPx - leftWidth
    val clockOnLeft = clockSide == ClockSide.LEFT
    val hitWidth = requestedHitWidthPx.coerceAtMost(totalWidthPx)
    return SplitLayoutGeometry(
        splitPx = leftWidth,
        clockLeftPx = if (clockOnLeft) 0 else leftWidth,
        clockWidthPx = if (clockOnLeft) leftWidth else rightWidth,
        widgetsLeftPx = if (clockOnLeft) leftWidth else 0,
        widgetsWidthPx = if (clockOnLeft) rightWidth else leftWidth,
        // Keep the hit target inside very narrow windows, without shifting the physical split.
        hitLeftPx = (leftWidth - hitWidth / 2).coerceIn(0, totalWidthPx - hitWidth),
        hitWidthPx = hitWidth
    )
}
