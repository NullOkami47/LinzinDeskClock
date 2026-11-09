package com.linzin.linzindeskclock.media

import kotlin.math.min
import kotlin.math.roundToInt

data class ArtworkRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class ArtworkLayout(val artwork: ArtworkRect, val extensions: List<ArtworkRect>, val scale: Float)
data class ArtworkSize(val width: Int, val height: Int)

/** Whole-image fit, with a disjoint partition of only the surrounding letterbox area. */
fun fitArtwork(sourceWidth: Int, sourceHeight: Int, width: Int, height: Int): ArtworkLayout? {
    if (sourceWidth <= 0 || sourceHeight <= 0 || width <= 0 || height <= 0) return null
    val scale = min(width.toDouble() / sourceWidth, height.toDouble() / sourceHeight)
    val fittedWidth = (sourceWidth * scale).toFloat().coerceAtMost(width.toFloat())
    val fittedHeight = (sourceHeight * scale).toFloat().coerceAtMost(height.toFloat())
    val left = (width - fittedWidth) / 2f
    val top = (height - fittedHeight) / 2f
    val fit = ArtworkRect(left, top, left + fittedWidth, top + fittedHeight)
    val extensions = buildList {
        if (top > 0f) {
            add(ArtworkRect(0f, 0f, width.toFloat(), fit.top))
            add(ArtworkRect(0f, fit.bottom, width.toFloat(), height.toFloat()))
        }
        if (left > 0f) {
            add(ArtworkRect(0f, fit.top, fit.left, fit.bottom))
            add(ArtworkRect(fit.right, fit.top, width.toFloat(), fit.bottom))
        }
    }
    return ArtworkLayout(fit, extensions, scale.toFloat())
}

/** Cap the longest edge without upscaling; minimum one pixel for very narrow covers. */
fun downsampleSize(width: Int, height: Int, maxEdge: Int): ArtworkSize? {
    if (width <= 0 || height <= 0 || maxEdge <= 0) return null
    val scale = min(1.0, maxEdge.toDouble() / maxOf(width, height))
    return ArtworkSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
}
