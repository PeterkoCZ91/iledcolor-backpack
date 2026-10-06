package com.batoh.core.conversion

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * Own 64×64 test animation (scrolling colour bars) for the "test image" upload, so the app
 * no longer ships a payload captured from the manufacturer's app.
 */
object TestPatternGif {
    private val COLORS = intArrayOf(
        0xFFFF0000.toInt(), 0xFFFFFF00.toInt(), 0xFF00FF00.toInt(), 0xFF00FFFF.toInt(),
        0xFF0000FF.toInt(), 0xFFFF00FF.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(),
    )

    fun render(size: Int = 64, frames: Int = 8, delayMs: Int = 250): ByteArray {
        val output = ByteArrayOutputStream()
        val encoder = GifEncoder().apply {
            setSize(size, size)
            setDelay(delayMs)
            setRepeat(0)
            start(output)
        }
        val bar = size / COLORS.size
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        for (frame in 0 until frames) {
            for (y in 0 until size) for (x in 0 until size) {
                pixels[y * size + x] = COLORS[((x / bar) + frame) % COLORS.size]
            }
            bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
            encoder.addFrame(bitmap)
        }
        encoder.finish()
        bitmap.recycle()
        return output.toByteArray()
    }
}
