package com.batoh.feature.backpack

import com.batoh.core.data.bluetooth.PanelFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrightnessPresetsTest {
    @Test fun backpackLevelsStayInVerifiedRangeAndAreOrdered() {
        val levels = BrightnessPreset.values().map { BrightnessPresets.level(it, PanelFamily.Backpack) }
        assertEquals(listOf(2, 5, 10), levels)
        assertTrue(levels.all { it in 1..10 })
        assertEquals(levels.sorted(), levels)
    }

    @Test fun ipixelLevelsStayInVerifiedRangeAndAreOrdered() {
        val levels = BrightnessPreset.values().map { BrightnessPresets.level(it, PanelFamily.IPixel) }
        assertEquals(listOf(10, 50, 100), levels)
        assertTrue(levels.all { it in 0..100 })
        assertEquals(levels.sorted(), levels)
    }

    @Test fun presetForFindsExactMatchesOnly() {
        assertEquals(BrightnessPreset.Dim, BrightnessPresets.presetFor(2, PanelFamily.Backpack))
        assertEquals(BrightnessPreset.Bright, BrightnessPresets.presetFor(100, PanelFamily.IPixel))
        assertNull(BrightnessPresets.presetFor(3, PanelFamily.Backpack))
        assertNull(BrightnessPresets.presetFor(2, PanelFamily.IPixel))
    }

    @Test fun parseRejectsUnknownNames() {
        assertEquals(BrightnessPreset.Normal, BrightnessPresets.parse("Normal"))
        assertNull(BrightnessPresets.parse("normal"))
        assertNull(BrightnessPresets.parse(null))
    }
}
