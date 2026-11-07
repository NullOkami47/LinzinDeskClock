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
     * Valid new settings override the legacy mode. A missing/invalid ratio falls back to 70/30
     * for the valid saved side, or the legacy side if unavailable. Finite out-of-range values
     * are clamped. A valid ratio decides which side is larger; only a 50/50 tie uses the saved
     * (then legacy) side. Neither restore nor save applies the release snap.
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
        val state = SplitLayoutState(values[RATIO_KEY] as? Float ?: Float.NaN, side)
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
