package com.batoh.feature.backpack

import com.batoh.core.data.bluetooth.BackpackPayload
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorPlaybackOptionsTest {
    /** Minimal 64×64 GIF header + trailer; fromGif only inspects the logical screen size. */
    private val gif = "GIF89a".toByteArray(Charsets.US_ASCII) +
        byteArrayOf(64, 0, 64, 0, 0, 0, 0, 0x3b)

    private fun Byte.u() = toInt() and 0xFF

    @Test
    fun defaultsMatchManufacturerAndTodayPayload() {
        val options = EditorPlaybackOptions()
        assertTrue(options.isDefault)
        val payload = options.buildPayload(gif)
        assertArrayEquals(BackpackPayload.fromGif(gif), payload)
        assertEquals(100, payload[EditorPlaybackOptions.SPEED_OFFSET].u())
        assertEquals(100, payload[EditorPlaybackOptions.LIGHT_OFFSET].u())
        assertEquals(0, payload[EditorPlaybackOptions.EFFECT_OFFSET].u())
    }

    @Test
    fun customSpeedAndLightLandAtTheirOffsets() {
        val payload = EditorPlaybackOptions(speed = 200, light = 37).buildPayload(gif)
        assertEquals(200, payload[0x27].u())
        assertEquals(37, payload[0x2A].u())
        assertEquals(0, payload[0x26].u())
        assertEquals(4, payload[0x28].u())
        assertEquals(6, payload[0x23].u()) // item type GIF
        // GIF follows the 46-byte header+item unchanged and fileID covers the changed bytes.
        assertArrayEquals(gif, payload.copyOfRange(0x2E, payload.size))
        assertFalse(payload.copyOfRange(0, 4).contentEquals(BackpackPayload.fromGif(gif).copyOfRange(0, 4)))
    }

    @Test
    fun extremesAreSingleBytes() {
        val payload = EditorPlaybackOptions(speed = 255, light = 0).buildPayload(gif)
        assertEquals(255, payload[0x27].u())
        assertEquals(0, payload[0x2A].u())
        assertFalse(EditorPlaybackOptions(speed = 255, light = 0).isDefault)
    }

    @Test
    fun outOfRangeValuesAreRejectedAndSliderValuesCoerced() {
        assertThrows(IllegalArgumentException::class.java) { EditorPlaybackOptions(speed = 256) }
        assertThrows(IllegalArgumentException::class.java) { EditorPlaybackOptions(light = -1) }
        assertEquals(0, EditorPlaybackOptions.coerce(-3f))
        assertEquals(255, EditorPlaybackOptions.coerce(300f))
        assertEquals(101, EditorPlaybackOptions.coerce(100.6f))
    }
}
