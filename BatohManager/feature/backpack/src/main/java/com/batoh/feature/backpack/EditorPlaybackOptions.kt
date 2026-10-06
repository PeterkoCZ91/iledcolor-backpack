package com.batoh.feature.backpack

import com.batoh.core.data.bluetooth.BackpackPayload

/**
 * Programme playback bytes written into the upload payload item (offset 0x27 speed, 0x2A light).
 * Defaults match the manufacturer app and BackpackPayload.fromGif; their visible effect on the
 * panel is not hardware-verified yet, so the editor labels the controls as experimental.
 */
data class EditorPlaybackOptions(
    val speed: Int = DEFAULT_SPEED,
    val light: Int = DEFAULT_LIGHT
) {
    init {
        require(speed in 0..255 && light in 0..255) { "Speed and light are single bytes (0..255)" }
    }

    val isDefault: Boolean get() = speed == DEFAULT_SPEED && light == DEFAULT_LIGHT

    /** Wraps an already 64×64 GIF; effect stays 0 like the manufacturer default. */
    fun buildPayload(gif: ByteArray): ByteArray = BackpackPayload.fromGif(gif, speed = speed, light = light)

    companion object {
        const val DEFAULT_SPEED = 100
        const val DEFAULT_LIGHT = 100
        const val SPEED_OFFSET = 0x27
        const val LIGHT_OFFSET = 0x2A
        const val EFFECT_OFFSET = 0x26

        fun coerce(value: Float): Int = Math.round(value).coerceIn(0, 255)
    }
}
