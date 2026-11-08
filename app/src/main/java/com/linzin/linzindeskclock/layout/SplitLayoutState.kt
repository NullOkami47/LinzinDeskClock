package com.linzin.linzindeskclock.layout

/** Physical screen sides, independent of the locale's layout direction. */
enum class ClockSide { LEFT, RIGHT }

/**
 * Immutable, Android/Compose-independent split state.
 *
 * [ratio] is the physical LEFT panel's fraction of the entire available width, not the clock's
 * fraction. [clockSide] is the user's chosen side, independent of either panel's size.
 * Construction clamps finite ratios to 0.2..0.8 and recovers non-finite ratios to 70/30
 * for the supplied side. Construction and dragging never snap: only [release] does.
 *
 * Resizing and release preserve [clockSide]; only [withClockSide] explicitly changes it.
 * All methods leave this instance unchanged, so callers can hoist it in mutableStateOf.
 */
class SplitLayoutState(
    ratio: Float = DEFAULT_RATIO,
    clockSide: ClockSide = ClockSide.LEFT
) {
    val ratio: Float = if (ratio.isFinite()) {
        ratio.coerceIn(MIN_RATIO, MAX_RATIO)
    } else {
        if (clockSide == ClockSide.LEFT) DEFAULT_RATIO else LEGACY_RIGHT_RATIO
    }

    val clockSide: ClockSide = clockSide

    /** Exact continuous update; invalid input is ignored rather than making a live drag jump. */
    fun withRatio(ratio: Float): SplitLayoutState =
        if (ratio.isFinite()) SplitLayoutState(ratio, clockSide) else this

    /** Explicitly changes the clock's physical side without resizing either panel. */
    fun withClockSide(side: ClockSide): SplitLayoutState = SplitLayoutState(ratio, side)

    /** Positive [deltaPx] moves the physical divider right, including in RTL layouts. */
    fun dragBy(deltaPx: Float, totalWidthPx: Float): SplitLayoutState {
        if (!deltaPx.isFinite() || !totalWidthPx.isFinite() || totalWidthPx <= 0f) return this
        // Double arithmetic prevents finite Float inputs overflowing before they can be clamped.
        val next = (ratio.toDouble() + deltaPx.toDouble() / totalWidthPx.toDouble())
            .coerceIn(MIN_RATIO.toDouble(), MAX_RATIO.toDouble())
        return withRatio(next.toFloat())
    }

    /** Snap only on release: 25..35% -> 30%, 45..55% -> 50%, 65..75% -> 70%, inclusive. */
    fun release(): SplitLayoutState =
        SNAP_BANDS.firstOrNull { (band, _) -> ratio in band }
            ?.let { (_, anchor) -> withRatio(anchor) } ?: this

    override fun equals(other: Any?): Boolean =
        other is SplitLayoutState && ratio == other.ratio && clockSide == other.clockSide

    override fun hashCode(): Int = 31 * ratio.hashCode() + clockSide.hashCode()

    override fun toString(): String = "SplitLayoutState(ratio=" + ratio + ", clockSide=" + clockSide + ")"

    companion object {
        const val MIN_RATIO = 0.2f
        const val MAX_RATIO = 0.8f
        const val CENTRE_RATIO = 0.5f
        const val DEFAULT_RATIO = 0.7f
        const val LEGACY_RIGHT_RATIO = 0.3f
        const val SNAP_START = 0.45f
        const val SNAP_END = 0.55f

        // Share exact bands with the UI's pixel-based release classification.
        internal val SNAP_BANDS = listOf(
            (0.25f..0.35f) to LEGACY_RIGHT_RATIO,
            (SNAP_START..SNAP_END) to CENTRE_RATIO,
            (0.65f..0.75f) to DEFAULT_RATIO
        )

        /** Accepts ClockLayoutMode.name without depending on the Activity's enum. */
        fun fromLegacyMode(mode: String?): SplitLayoutState =
            if (mode == "DUAL_RIGHT_LARGE") {
                SplitLayoutState(LEGACY_RIGHT_RATIO, ClockSide.RIGHT)
            } else {
                SplitLayoutState(DEFAULT_RATIO, ClockSide.LEFT)
            }
    }
}
