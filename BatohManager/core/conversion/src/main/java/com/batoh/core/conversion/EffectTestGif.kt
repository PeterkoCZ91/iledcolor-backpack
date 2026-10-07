package com.batoh.core.conversion

import java.io.ByteArrayOutputStream

/**
 * Small asymmetric 64x64 GIF for judging programme effects on the panel (pure JVM, no Bitmap).
 *
 * The picture has a red left half, a blue right half, a large white right-pointing arrow and the
 * effect code as a large white digit (or two) in the upper-left corner, so scroll direction and mirroring are visible at a glance.
 * A green marker below the arrow advances to the right on every frame, which shows the frame order.
 * Format matches what the panel accepts: 128-entry global palette, disposal 1, 200 ms frames.
 */
object EffectTestGif {
    const val SIZE = 64
    private const val FRAMES = 10
    private const val DELAY_CS = 20

    private const val BLACK = 0
    private const val LEFT = 1
    private const val RIGHT = 2
    private const val WHITE = 3
    private const val GREEN = 4

    private val PALETTE_RGB = intArrayOf(
        0x000000, 0x802020, 0x203090, 0xFFFFFF, 0x20E020,
    )

    // 3x5 digit glyphs, one row per entry, bit 2 = leftmost column.
    private val DIGIT_GLYPHS = listOf(
        intArrayOf(7, 5, 5, 5, 7), // 0
        intArrayOf(2, 6, 2, 2, 7), // 1
        intArrayOf(7, 1, 7, 4, 7), // 2
        intArrayOf(7, 1, 7, 1, 7), // 3
        intArrayOf(5, 5, 7, 1, 1), // 4
        intArrayOf(7, 4, 7, 1, 7), // 5
        intArrayOf(7, 4, 7, 5, 7), // 6
        intArrayOf(7, 1, 1, 1, 1), // 7
        intArrayOf(7, 5, 7, 5, 7), // 8
        intArrayOf(7, 5, 7, 1, 7), // 9
    )
    private const val DIGIT_SCALE = 5 // 3x5 glyph -> 15x25 px
    private const val DIGIT_GAP = 3

    fun render(effectCode: Int): ByteArray {
        require(effectCode in 0..99) { "Effect code must be 0..99, got $effectCode" }
        val out = ByteArrayOutputStream()
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        out.short(SIZE); out.short(SIZE)
        out.write(0xE6) // global table, 128 entries (size field 6)
        out.write(0); out.write(0)
        val palette = ByteArray(128 * 3)
        PALETTE_RGB.forEachIndexed { i, rgb ->
            palette[i * 3] = (rgb shr 16).toByte()
            palette[i * 3 + 1] = (rgb shr 8).toByte()
            palette[i * 3 + 2] = rgb.toByte()
        }
        out.write(palette)
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 11))
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(3, 1)); out.short(0); out.write(0)
        for (frame in 0 until FRAMES) {
            out.write(byteArrayOf(0x21, 0xF9.toByte(), 4, 4)) // disposal 1, no transparency
            out.short(DELAY_CS); out.write(0); out.write(0)
            out.write(0x2C)
            out.short(0); out.short(0); out.short(SIZE); out.short(SIZE)
            out.write(0)
            LzwEncoder(SIZE, SIZE, frameIndices(effectCode, frame), 7).encode(out)
        }
        out.write(0x3B)
        return out.toByteArray()
    }

    /** Palette indices of one frame, row-major; visible for tests. */
    internal fun frameIndices(effectCode: Int, frame: Int): ByteArray {
        val px = ByteArray(SIZE * SIZE)
        fun set(x: Int, y: Int, c: Int) { if (x in 0 until SIZE && y in 0 until SIZE) px[y * SIZE + x] = c.toByte() }
        for (y in 0 until SIZE) for (x in 0 until SIZE) set(x, y, if (x < SIZE / 2) LEFT else RIGHT)
        // Arrow: shaft x 8..40, head x 40..58 centred on y = 34.
        for (y in 30..37) for (x in 8 until 40) set(x, y, WHITE)
        for (x in 40..58) {
            val half = 14 - (x - 40) * 14 / 18
            for (y in 34 - half..33 + half) set(x, y, WHITE)
        }
        // Large white effect code on a dark box in the upper-left corner.
        val digits = effectCode.toString().map { it - '0' }
        val boxW = 4 + digits.size * 3 * DIGIT_SCALE + (digits.size - 1) * DIGIT_GAP
        for (y in 0 until 5 * DIGIT_SCALE + 4) for (x in 0 until boxW) set(x, y, BLACK)
        digits.forEachIndexed { g, d ->
            val x0 = 2 + g * (3 * DIGIT_SCALE + DIGIT_GAP)
            DIGIT_GLYPHS[d].forEachIndexed { r, bits ->
                for (c in 0 until 3) if (bits shr (2 - c) and 1 == 1) {
                    for (dy in 0 until DIGIT_SCALE) for (dx in 0 until DIGIT_SCALE) {
                        set(x0 + c * DIGIT_SCALE + dx, 2 + r * DIGIT_SCALE + dy, WHITE)
                    }
                }
            }
        }
        // Frame marker moving right along the bottom.
        val markerX = 2 + frame * 6
        for (y in 54..59) for (x in markerX until markerX + 4) set(x, y, GREEN)
        return px
    }

    private fun ByteArrayOutputStream.short(v: Int) { write(v and 0xFF); write((v shr 8) and 0xFF) }
}
