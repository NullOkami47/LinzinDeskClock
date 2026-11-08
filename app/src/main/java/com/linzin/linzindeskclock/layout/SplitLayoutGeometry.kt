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

/**
 * Use the same rounded divider pixel as measurement/drawing for a pointer release. For example,
 * a drag can produce 0.24999999 even though the divider is visibly at pixel 250 of 1000.
 * Only the snap decision uses pixel precision; unsnapped ratios and the last clock side survive.
 */
internal fun SplitLayoutState.releaseAtWidth(totalWidthPx: Int): SplitLayoutState {
    if (totalWidthPx <= 0) return release()
    val dividerPx = geometry(totalWidthPx, 0).splitPx
    val anchor = SplitLayoutState.SNAP_BANDS.firstOrNull { (band, _) ->
        // Round the band edges too: e.g. 35% of 319px lies between physical pixels.
        dividerPx in (totalWidthPx * band.start).roundToInt()..
            (totalWidthPx * band.endInclusive).roundToInt()
    }?.second
    return anchor?.let { withRatio(it) } ?: this
}
