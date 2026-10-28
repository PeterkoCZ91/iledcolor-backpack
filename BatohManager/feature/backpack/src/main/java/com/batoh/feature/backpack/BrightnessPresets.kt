package com.batoh.feature.backpack

import com.batoh.core.data.bluetooth.PanelFamily

/** The three one-tap brightness choices. Night mode is simply [Dim]; nothing here runs on a timer. */
enum class BrightnessPreset { Dim, Normal, Bright }

/**
 * Maps a preset to a panel family's own brightness scale. The ranges are the verified ones
 * (docs/ble-protocol.md 10.4 and 13.4): backpack 1 (dimmest) to 10 (brightest), iPixel 0 to 100 percent.
 * The levels themselves are a design choice, not measured values.
 */
object BrightnessPresets {
    const val BACKPACK_DIM = 2
    const val BACKPACK_NORMAL = 5
    const val BACKPACK_BRIGHT = 10
    const val IPIXEL_DIM = 10
    const val IPIXEL_NORMAL = 50
    const val IPIXEL_BRIGHT = 100

    fun level(preset: BrightnessPreset, family: PanelFamily): Int = when (family) {
        PanelFamily.Backpack -> when (preset) {
            BrightnessPreset.Dim -> BACKPACK_DIM
            BrightnessPreset.Normal -> BACKPACK_NORMAL
            BrightnessPreset.Bright -> BACKPACK_BRIGHT
        }
        PanelFamily.IPixel -> when (preset) {
            BrightnessPreset.Dim -> IPIXEL_DIM
            BrightnessPreset.Normal -> IPIXEL_NORMAL
            BrightnessPreset.Bright -> IPIXEL_BRIGHT
        }
    }

    /** The preset whose level equals [level] exactly, or null when the slider sits somewhere in between. */
    fun presetFor(level: Int, family: PanelFamily): BrightnessPreset? =
        BrightnessPreset.values().firstOrNull { level(it, family) == level }

    /** Parses a stored name; anything unknown (including null) gives null, so bad data never sends a command. */
    fun parse(name: String?): BrightnessPreset? = BrightnessPreset.values().firstOrNull { it.name == name }
}
