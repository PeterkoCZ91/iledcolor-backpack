package com.batoh.core.conversion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

/** Renders four independently scrolling messages into one endlessly repeating, panel-sized GIF. */
object TextLoopRenderer {
    fun render(
        texts: List<String>,
        width: Int,
        height: Int,
        textColor: Int = 0xFFFFFFFF.toInt(),
        backgroundColor: Int = 0xFF000000.toInt(),
        bold: Boolean = true,
        checkCancellation: () -> Unit = {}
    ): ByteArray {
        require(texts.size == TextLoopLayout.ITEM_COUNT) { "Exactly four messages are required" }
        val messages = texts.map { it.trim() }
        require(messages.all { it.isNotEmpty() && it.length <= TextBannerLayout.MAX_TEXT_LENGTH }) {
            "Each message must contain 1–${TextBannerLayout.MAX_TEXT_LENGTH} characters"
        }
        // Keep the decoded animation below SafeGifDecoder's 64 M pixel budget.
        require(width in 1..4096 && height in 1..4096 &&
            width.toLong() * height * TextLoopLayout.FRAME_COUNT <= 64L * 1024 * 1024) {
            "Panel size exceeds GIF limits"
        }
        val paint = Paint().apply {
            isAntiAlias = false
            isSubpixelText = false
            color = textColor
            textSize = height * 0.75f
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val widths = messages.map { ceil(paint.measureText(it).toDouble()).toInt().coerceAtLeast(0) }
        val metrics = paint.fontMetrics
        val baseline = TextBannerLayout.baseline(metrics.ascent, metrics.descent, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val output = BoundedGifOutput()
        try {
            val encoder = GifEncoder().apply {
                setSize(width, height)
                setRepeat(0)
                setDelay(TextLoopLayout.FRAME_DELAY_MS)
            }
            check(encoder.start(output)) { "GIF encoder failed to start" }
            messages.forEachIndexed { item, message ->
                for (frame in 0 until TextLoopLayout.FRAMES_PER_ITEM) {
                    checkCancellation()
                    canvas.drawColor(backgroundColor or 0xFF000000.toInt())
                    canvas.drawText(message, TextLoopLayout.offsetX(width, widths[item], frame).toFloat(), baseline, paint)
                    check(encoder.addFrame(bitmap)) { "GIF encoder failed at frame $frame" }
                }
            }
            check(encoder.finish()) { "GIF encoder failed to finish" }
            return output.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    private class BoundedGifOutput : ByteArrayOutputStream() {
        override fun write(value: Int) {
            require(size() < SafeGifDecoder.MAX_BYTES) { "GIF is larger than 20 MB" }
            super.write(value)
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            require(length <= SafeGifDecoder.MAX_BYTES - size()) { "GIF is larger than 20 MB" }
            super.write(bytes, offset, length)
        }
    }
}
