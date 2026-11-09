package com.linzin.linzindeskclock.layout

import android.content.SharedPreferences

/** Storage adapter for the parent's existing desk_clock_prefs preferences. */
object SplitLayoutPreferences {
    const val RATIO_KEY = "pref_split_ratio"
    const val CLOCK_SIDE_KEY = "pref_clock_side"
    private const val LEGACY_MODE_KEY = "pref_layout_mode"

    /**
     * Restore and, if needed, persist a migrated/repaired pair. A single snapshot avoids mixing
     * values from different edits and avoids typed-getter ClassCastExceptions on corrupt data.
     *
     * A valid saved side is honoured regardless of ratio; a missing/invalid side inherits the
     * legacy mode (LEFT by default). A missing ratio migrates the legacy proportion, while an
     * invalid ratio falls back to 70/30 for the resolved side. Finite out-of-range values are
     * clamped. Neither restore nor save applies the release snap or changes the resolved side.
     *
     * This does not change pref_layout_mode or either parent's pager preference.
     */
    fun load(preferences: SharedPreferences): SplitLayoutState {
        val values = preferences.all
        val legacy = SplitLayoutState.fromLegacyMode(values[LEGACY_MODE_KEY] as? String)
        val side = when (values[CLOCK_SIDE_KEY]) {
            ClockSide.LEFT.name -> ClockSide.LEFT
            ClockSide.RIGHT.name -> ClockSide.RIGHT
            else -> legacy.clockSide
        }
        val ratio = if (values.containsKey(RATIO_KEY)) {
            values[RATIO_KEY] as? Float ?: Float.NaN
        } else {
            legacy.ratio
        }
        val state = SplitLayoutState(ratio, side)
        if (values[RATIO_KEY] != state.ratio || values[CLOCK_SIDE_KEY] != state.clockSide.name) {
            save(preferences, state)
        }
        return state
    }

    /** Writes both fields in one edit. Normally called by the parent's onDragFinished callback. */
    fun save(preferences: SharedPreferences, state: SplitLayoutState) {
        preferences.edit()
            .putFloat(RATIO_KEY, state.ratio)
            .putString(CLOCK_SIDE_KEY, state.clockSide.name)
            .apply()
    }
}
