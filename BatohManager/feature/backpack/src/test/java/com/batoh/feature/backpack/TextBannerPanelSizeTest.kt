package com.batoh.feature.backpack

import com.batoh.core.conversion.TextBannerLayout
import com.batoh.core.data.bluetooth.MyPanel
import com.batoh.core.data.bluetooth.PanelFamily
import com.batoh.core.data.bluetooth.PanelSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextBannerPanelSizeTest {
    @Test
    fun limitsAreInclusive() {
        val min = TextBannerLayout.MIN_PANEL_SIDE
        val max = TextBannerLayout.MAX_PANEL_SIDE
        assertTrue(TextBannerPanelSize.isValid(min, max))
        assertFalse(TextBannerPanelSize.isValid(min - 1, 64))
        assertFalse(TextBannerPanelSize.isValid(64, max + 1))
        assertFalse(TextBannerPanelSize.isValid(0, 0))
        assertFalse(TextBannerPanelSize.isValid(-64, 64))
    }

    @Test
    fun parseAcceptsWholeNumbersInRange() {
        assertEquals(64 to 16, TextBannerPanelSize.parse("64", "16"))
        assertEquals(32 to 32, TextBannerPanelSize.parse(" 32 ", "32"))
    }

    @Test
    fun parseRejectsBlankNonNumericAndOutOfRange() {
        assertNull(TextBannerPanelSize.parse("", "16"))
        assertNull(TextBannerPanelSize.parse("64", ""))
        assertNull(TextBannerPanelSize.parse("6x", "16"))
        assertNull(TextBannerPanelSize.parse("64.5", "16"))
        assertNull(TextBannerPanelSize.parse("7", "16"))
        assertNull(TextBannerPanelSize.parse("64", "129"))
        assertNull(TextBannerPanelSize.parse("99999999999", "16"))
    }

    @Test
    fun restoreTreatsMissingOrZeroAsAutomatic() {
        assertNull(TextBannerPanelSize.restore(null, null))
        assertNull(TextBannerPanelSize.restore(0, 0))
        assertNull(TextBannerPanelSize.restore(64, null))
        assertEquals(64 to 32, TextBannerPanelSize.restore(64, 32))
    }

    @Test
    fun choicesListKnownSizesOnly() {
        val panels = listOf(
            MyPanel("A", "Backpack", PanelSpec(PanelFamily.Backpack, 64, 64, true)),
            MyPanel("B", "Strip", PanelSpec(PanelFamily.IPixel, 64, 16, true)),
            MyPanel("C", "Unknown", null),
            MyPanel("D", "Broken", PanelSpec(PanelFamily.IPixel, 0, 16, false)),
            MyPanel("E", "Backpack", PanelSpec(PanelFamily.Backpack, 64, 64, true))
        )
        assertEquals(
            listOf(TextBannerSizeChoice("Backpack", 64, 64), TextBannerSizeChoice("Strip", 64, 16)),
            TextBannerPanelSize.choices(panels)
        )
    }
}
