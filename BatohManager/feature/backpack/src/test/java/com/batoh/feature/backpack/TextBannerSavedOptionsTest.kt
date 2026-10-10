package com.batoh.feature.backpack

import com.batoh.core.conversion.TextBannerOptions
import com.batoh.core.conversion.TextBannerSize
import com.batoh.core.conversion.TextBannerSpeed
import org.junit.Assert.assertEquals
import org.junit.Test

class TextBannerSavedOptionsTest {
    @Test
    fun roundTripsAllOptions() {
        val options = TextBannerOptions("Hello", 0xFF112233.toInt(), 0xFF445566.toInt(),
            TextBannerSpeed.entries.last(), TextBannerSize.entries.first(), bold = false)
        val store = mutableMapOf<String, Any>()
        TextBannerSavedOptions.write(options) { k, v -> store[k] = v }
        assertEquals(options, TextBannerSavedOptions.read { store[it] })
    }

    @Test
    fun emptyOrStaleStateFallsBackToDefaults() {
        assertEquals(TextBannerOptions(text = ""), TextBannerSavedOptions.read { null })
        val stale = mapOf<String, Any>("text_banner_speed" to "Removed", "text_banner_size" to "Gone")
        assertEquals(TextBannerOptions(text = ""), TextBannerSavedOptions.read { stale[it] })
    }
}
