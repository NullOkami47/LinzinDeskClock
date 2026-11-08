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
        for (side in ClockSide.entries) {
            for (ratio in listOf(0.37f, 0.5f, 0.73f)) {
                val legacy = if (side == ClockSide.LEFT) "DUAL_RIGHT_LARGE" else "DUAL_LEFT_LARGE"
                val prefs = InMemorySharedPreferences(mapOf(
                    "pref_layout_mode" to legacy,
                    "pref_split_ratio" to ratio,
                    "pref_clock_side" to side.name
                ))
                val restored = SplitLayoutPreferences.load(prefs)
                assertEquals(ratio, restored.ratio, 0f)
                assertEquals(side, restored.clockSide)
                assertEquals(0, prefs.writeCount)
            }
        }
    }

    @Test
    fun `save and reload preserve either chosen side at every panel size`() {
        for (side in ClockSide.entries) {
            for (ratio in listOf(0.2f, 0.3f, 0.47f, 0.5f, 0.7f, 0.8f)) {
                val prefs = InMemorySharedPreferences()
                SplitLayoutPreferences.save(prefs, SplitLayoutState(ratio, side))
                val restored = SplitLayoutPreferences.load(prefs)
                assertEquals(ratio, restored.ratio, 0f)
                assertEquals(side, restored.clockSide)
                assertEquals(side.name, prefs.getString("pref_clock_side", null))
                assertEquals(1, prefs.writeCount)
            }
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
        for (side in ClockSide.entries) {
            val fallbackRatio = if (side == ClockSide.LEFT) 0.7f else 0.3f
            val legacy = if (side == ClockSide.LEFT) "DUAL_RIGHT_LARGE" else "DUAL_LEFT_LARGE"
            for (invalid in invalidValues) {
                val prefs = InMemorySharedPreferences(mapOf(
                    "pref_layout_mode" to legacy,
                    "pref_split_ratio" to invalid,
                    "pref_clock_side" to side.name
                ))
                assertEquals(SplitLayoutState(fallbackRatio, side), SplitLayoutPreferences.load(prefs))
                assertEquals(fallbackRatio, prefs.getFloat("pref_split_ratio", 0f), 0f)
                assertEquals(side.name, prefs.getString("pref_clock_side", null))
                SplitLayoutPreferences.load(prefs)
                assertEquals(1, prefs.writeCount)
            }
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
    fun `out-of-range stored ratios clamp without changing a valid saved side`() {
        val high = InMemorySharedPreferences(mapOf("pref_split_ratio" to 9f, "pref_clock_side" to "RIGHT"))
        assertEquals(SplitLayoutState(0.8f, ClockSide.RIGHT), SplitLayoutPreferences.load(high))
        assertEquals(0.8f, high.getFloat("pref_split_ratio", 0f), 0f)
        assertEquals("RIGHT", high.getString("pref_clock_side", null))
        val low = InMemorySharedPreferences(mapOf("pref_split_ratio" to -9f, "pref_clock_side" to "LEFT"))
        assertEquals(SplitLayoutState(0.2f, ClockSide.LEFT), SplitLayoutPreferences.load(low))
        assertEquals(0.2f, low.getFloat("pref_split_ratio", 0f), 0f)
        assertEquals("LEFT", low.getString("pref_clock_side", null))
    }

    @Test
    fun `absent or invalid saved side inherits legacy or left independently of ratio`() {
        for ((legacy, expectedSide) in listOf(
            null to ClockSide.LEFT, "DUAL_LEFT_LARGE" to ClockSide.LEFT,
            "DUAL_RIGHT_LARGE" to ClockSide.RIGHT, "SINGLE" to ClockSide.LEFT, "unknown" to ClockSide.LEFT
        )) {
            for (invalid in listOf(null, "", "unknown", 42, false)) {
                for (ratio in listOf(0.2f, 0.4f, 0.5f, 0.6f, 0.8f)) {
                    val values = mutableMapOf<String, Any>("pref_split_ratio" to ratio)
                    legacy?.let { values["pref_layout_mode"] = it }
                    invalid?.let { values["pref_clock_side"] = it }
                    val prefs = InMemorySharedPreferences(values)
                    val restored = SplitLayoutPreferences.load(prefs)
                    assertEquals(ratio, restored.ratio, 0f)
                    assertEquals(expectedSide, restored.clockSide)
                    assertEquals(expectedSide.name, prefs.getString("pref_clock_side", null))
                    SplitLayoutPreferences.load(prefs)
                    assertEquals(1, prefs.writeCount)
                }
            }
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
        assertEquals(SplitLayoutState(0.7f, ClockSide.RIGHT), SplitLayoutPreferences.load(sideOnly))
    }

    @Test
    fun `missing ratio migrates the legacy proportion independently of a valid saved side`() {
        for ((legacy, expectedRatio) in listOf(
            "DUAL_LEFT_LARGE" to 0.7f, "DUAL_RIGHT_LARGE" to 0.3f, "SINGLE" to 0.7f, "unknown" to 0.7f
        )) {
            for (side in ClockSide.entries) {
                val prefs = InMemorySharedPreferences(mapOf(
                    "pref_layout_mode" to legacy, "pref_clock_side" to side.name
                ))
                val restored = SplitLayoutPreferences.load(prefs)
                assertEquals(expectedRatio, restored.ratio, 0f)
                assertEquals(side, restored.clockSide)
                assertEquals(expectedRatio, prefs.getFloat("pref_split_ratio", 0f), 0f)
                assertEquals(side.name, prefs.getString("pref_clock_side", null))
                SplitLayoutPreferences.load(prefs)
                assertEquals(1, prefs.writeCount)
            }
        }
    }

    @Test
    fun `explicitly choosing the smaller clock panel persists across reload and resizing`() {
        val prefs = InMemorySharedPreferences()
        var state = SplitLayoutState()
        for ((side, ratio) in listOf(ClockSide.RIGHT to 0.7f, ClockSide.LEFT to 0.3f)) {
            state = state.withRatio(ratio).withClockSide(side)
            SplitLayoutPreferences.save(prefs, state)
            val restored = SplitLayoutPreferences.load(prefs)
            assertEquals(ratio, restored.ratio, 0f)
            assertEquals(side, restored.clockSide)
            state = restored.dragBy(if (side == ClockSide.RIGHT) -400f else 400f, 1000f).release()
            SplitLayoutPreferences.save(prefs, state)
            assertEquals(side, SplitLayoutPreferences.load(prefs).clockSide)
            assertEquals(side.name, prefs.getString("pref_clock_side", null))
        }
        assertEquals(4, prefs.writeCount)
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
            "pref_split_ratio" to 0.39f, "pref_clock_side" to "LEFT"
        ), prefs.all)
        assertEquals(1, prefs.writeCount)
    }
}
