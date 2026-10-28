package com.batoh.feature.backpack

import com.batoh.core.conversion.TextBannerLayout
import com.batoh.core.data.bluetooth.MyPanel

/** A size the user can pick while composing a text banner. */
data class TextBannerSizeChoice(val name: String?, val width: Int, val height: Int)

/** Pure size validation and choice list for the banner's target-size picker; JVM-tested. */
object TextBannerPanelSize {
    fun isValid(width: Int, height: Int): Boolean =
        TextBannerLayout.isValidPanelSide(width) && TextBannerLayout.isValidPanelSide(height)

    /** Parses two typed numbers; null unless both are whole numbers inside the allowed range. */
    fun parse(widthText: String, heightText: String): Pair<Int, Int>? {
        val width = widthText.trim().toIntOrNull() ?: return null
        val height = heightText.trim().toIntOrNull() ?: return null
        return if (isValid(width, height)) width to height else null
    }

    /** Restores a saved size; 0 / missing / out of range means automatic. */
    fun restore(width: Int?, height: Int?): Pair<Int, Int>? =
        if (width != null && height != null && isValid(width, height)) width to height else null

    /** My panels with a known size, one entry per name and size, in the order they are stored. */
    fun choices(myPanels: List<MyPanel>): List<TextBannerSizeChoice> =
        myPanels.mapNotNull { panel ->
            panel.spec?.takeIf { isValid(it.width, it.height) }
                ?.let { TextBannerSizeChoice(panel.name, it.width, it.height) }
        }.distinct()
}
