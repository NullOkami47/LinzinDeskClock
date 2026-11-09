package com.linzin.linzindeskclock.media

/** Pixel coordinates shared by seek painting and pointer input; endpoints include the thumb. */
internal class SeekGeometry(width: Float, height: Float, thumbRadius: Float) {
    private val canvasWidth = width.coerceAtLeast(0f)
    val centreY = height.coerceAtLeast(0f) / 2f
    val thumbRadius = minOf(thumbRadius.coerceAtLeast(0f), canvasWidth / 2f, centreY)
    val startX = this.thumbRadius
    val endX = canvasWidth - this.thumbRadius

    fun thumbX(fraction: Float, reversed: Boolean = false): Float {
        val value = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
        return startX + (endX - startX) * if (reversed) 1f - value else value
    }

    fun fractionAt(x: Float, reversed: Boolean = false): Float {
        if (endX <= startX || !x.isFinite()) return 0f
        val value = ((x - startX) / (endX - startX)).coerceIn(0f, 1f)
        return if (reversed) 1f - value else value
    }
}
