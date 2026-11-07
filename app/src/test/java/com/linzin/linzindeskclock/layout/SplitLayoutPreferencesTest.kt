package com.linzin.linzindeskclock.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitLayoutPreferencesTest {
    @Test
    fun `missing new settings migrate the old right layout and persist the pair`() {
        val prefs = InMemorySharedPreferences(mapOf("pref_layout_mode" to "DUAL_RIGHT_LARGE"))
        assertEquals(SplitLayoutState(0.3f, ClockSide.RIGHT), SplitLayoutPreferences.load(prefs))
        assertEquals(0.3f, prefs.getFloat("pref_split_ratio", 0f), 0f)
        assertEquals("RIGHT", prefs.getString("pref_clock_side", null))
        assertEquals("DUAL_RIGHT_LARGE", prefs.getString("pref_layout_mode", null))
        assertEquals(1, prefs.writeCount)
    }

    @Test
    fun `left legacy mode and empty settings restore the left seventy thirty layout`() {
        for (legacy in listOf("DUAL_LEFT_LARGE", "SINGLE", "unknown")) {
            val prefs = InMemorySharedPreferences(mapOf("pref_layout_mode" to legacy))
            assertEquals(SplitLayoutState(0.7f), SplitLayoutPreferences.load(prefs))
        }
        assertEquals(SplitLayoutState(0.7f), SplitLayoutPreferences.load(InMemorySharedPreferences()))
    }

    @Test
    fun `new ratio and side take precedence over the old layout without rewriting`() {
        val prefs = InMemorySharedPreferences(mapOf(
            "pref_layout_mode" to "DUAL_LEFT_LARGE",
            "pref_split_ratio" to 0.37f,
            "pref_clock_side" to "RIGHT"
        ))
        assertEquals(SplitLayoutState(0.37f, ClockSide.RIGHT), SplitLayoutPreferences.load(prefs))
        assertEquals(0, prefs.writeCount)
    }

    @Test
    fun `save and reload preserve both possible centre sides`() {
        for (side in ClockSide.values()) {
            val prefs = InMemorySharedPreferences()
            SplitLayoutPreferences.save(prefs, SplitLayoutState(0.5f, side))
            assertEquals(SplitLayoutState(0.5f, side), SplitLayoutPreferences.load(prefs))
            assertEquals(1, prefs.writeCount)
        }
    }

    @Test
    fun `saving and restoring an in-progress ratio never apply the release snap`() {
        val prefs = InMemorySharedPreferences()
        SplitLayoutPreferences.save(prefs, SplitLayoutState(0.47f))
        assertEquals(SplitLayoutState(0.47f), SplitLayoutPreferences.load(prefs))
    }

    @Test
    fun `invalid ratio types and non-finite values fall back to a valid saved side`() {
        val invalidValues = listOf("0.6", 42, 0.6, true, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
        for (invalid in invalidValues) {
            val prefs = InMemorySharedPreferences(mapOf(
                "pref_layout_mode" to "DUAL_LEFT_LARGE",
                "pref_split_ratio" to invalid,
                "pref_clock_side" to "RIGHT"
            ))
            assertEquals(SplitLayoutState(0.3f, ClockSide.RIGHT), SplitLayoutPreferences.load(prefs))
            assertEquals(0.3f, prefs.getFloat("pref_split_ratio", 0f), 0f)
        }
    }

    @Test
    fun `invalid ratio and side together recover using the old mode`() {
        val prefs = InMemorySharedPreferences(mapOf(
            "pref_layout_mode" to "DUAL_RIGHT_LARGE",
            "pref_split_ratio" to "broken",
            "pref_clock_side" to 12
        ))
        assertEquals(SplitLayoutState(0.3f, ClockSide.RIGHT), SplitLayoutPreferences.load(prefs))
        assertEquals("RIGHT", prefs.getString("pref_clock_side", null))
    }

    @Test
    fun `out-of-range stored ratios clamp and repair an inconsistent side`() {
        val high = InMemorySharedPreferences(mapOf("pref_split_ratio" to 9f, "pref_clock_side" to "RIGHT"))
        assertEquals(SplitLayoutState(0.8f), SplitLayoutPreferences.load(high))
        assertEquals(0.8f, high.getFloat("pref_split_ratio", 0f), 0f)
        assertEquals("LEFT", high.getString("pref_clock_side", null))
        val low = InMemorySharedPreferences(mapOf("pref_split_ratio" to -9f, "pref_clock_side" to "LEFT"))
        assertEquals(SplitLayoutState(0.2f), SplitLayoutPreferences.load(low))
        assertEquals("RIGHT", low.getString("pref_clock_side", null))
    }

    @Test
    fun `valid ratio determines the side when a stored side is corrupt or inconsistent`() {
        for (invalid in listOf("unknown", 42, false, "LEFT")) {
            val prefs = InMemorySharedPreferences(mapOf("pref_split_ratio" to 0.4f, "pref_clock_side" to invalid))
            assertEquals(SplitLayoutState(0.4f), SplitLayoutPreferences.load(prefs))
            assertEquals("RIGHT", prefs.getString("pref_clock_side", null))
        }
    }

    @Test
    fun `centre with no usable saved side inherits the legacy side`() {
        val prefs = InMemorySharedPreferences(mapOf(
            "pref_layout_mode" to "DUAL_RIGHT_LARGE", "pref_split_ratio" to 0.5f, "pref_clock_side" to "unknown"
        ))
        assertEquals(SplitLayoutState(0.5f, ClockSide.RIGHT), SplitLayoutPreferences.load(prefs))
    }

    @Test
    fun `partially written settings recover the field that is still valid`() {
        val ratioOnly = InMemorySharedPreferences(mapOf("pref_split_ratio" to 0.62f))
        assertEquals(SplitLayoutState(0.62f), SplitLayoutPreferences.load(ratioOnly))
        assertEquals("LEFT", ratioOnly.getString("pref_clock_side", null))
        val sideOnly = InMemorySharedPreferences(mapOf("pref_clock_side" to "RIGHT"))
        assertEquals(SplitLayoutState(0.3f, ClockSide.RIGHT), SplitLayoutPreferences.load(sideOnly))
    }

    @Test
    fun `invalid legacy preference types also restore safely`() {
        val prefs = InMemorySharedPreferences(mapOf("pref_layout_mode" to false))
        assertEquals(SplitLayoutState(), SplitLayoutPreferences.load(prefs))
    }

    @Test
    fun `migration is idempotent even if the legacy mode changes afterwards`() {
        val prefs = InMemorySharedPreferences(mapOf("pref_layout_mode" to "DUAL_RIGHT_LARGE"))
        val first = SplitLayoutPreferences.load(prefs)
        prefs.edit().putString("pref_layout_mode", "DUAL_LEFT_LARGE").apply()
        val writes = prefs.writeCount
        assertEquals(first, SplitLayoutPreferences.load(prefs))
        assertEquals(writes, prefs.writeCount)
    }

    @Test
    fun `save updates only the two split keys and keeps both pager preferences`() {
        val prefs = InMemorySharedPreferences(mapOf(
            "pref_layout_mode" to "SINGLE", "pref_main_page" to 2, "pref_side_page" to 1, "pref_24_hour" to true
        ))
        SplitLayoutPreferences.save(prefs, SplitLayoutState(0.39f))
        assertEquals(mapOf(
            "pref_layout_mode" to "SINGLE", "pref_main_page" to 2, "pref_side_page" to 1, "pref_24_hour" to true,
            "pref_split_ratio" to 0.39f, "pref_clock_side" to "RIGHT"
        ), prefs.all)
        assertEquals(1, prefs.writeCount)
    }
}
