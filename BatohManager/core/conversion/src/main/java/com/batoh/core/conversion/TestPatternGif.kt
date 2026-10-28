package com.batoh.core.conversion

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * Own test animation (64×64 by default) (scrolling colour bars) for the "test image" upload, so the app
 * no longer ships a payload captured from the manufacturer's app.
 */
object TestPatternGif {
    private val COLORS = intArrayOf(
        0xFFFF0000.toInt(), 0xFFFFFF00.toInt(), 0xFF00FF00.toInt(), 0xFF00FFFF.toInt(),
        0xFF0000FF.toInt(), 0xFFFF00FF.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(),
    )

    fun render(width: Int = 64, height: Int = 64, frames: Int = 8, delayMs: Int = 250): ByteArray {
        val output = ByteArrayOutputStream()
        val encoder = GifEncoder().apply {
            setSize(width, height)
            setDelay(delayMs)
            setRepeat(0)
            start(output)
        }
        val bar = (width / COLORS.size).coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        for (frame in 0 until frames) {
            for (y in 0 until height) for (x in 0 until width) {
                pixels[y * width + x] = COLORS[((x / bar) + frame) % COLORS.size]
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            encoder.addFrame(bitmap)
        }
        encoder.finish()
        bitmap.recycle()
        return output.toByteArray()
    }
}
