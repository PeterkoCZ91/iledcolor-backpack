package com.batoh.core.conversion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import java.time.LocalTime

/** Renders the current time as a small two-frame GIF (the colon blinks) at the panel's own size. */
object ClockGifRenderer {
    private const val FRAME_DELAY_MS = 500
    private const val TEXT_COLOR = 0xFFFFC800.toInt()

    /** "21:05" for [time]; always 24-hour with two-digit hours. */
    fun label(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

    fun render(time: LocalTime, width: Int, height: Int): ByteArray {
        require(TextBannerLayout.isValidPanelSide(width) && TextBannerLayout.isValidPanelSide(height)) {
            "Unsupported panel size ${width}x$height"
        }
        val label = label(time)
        val paint = Paint().apply {
            isAntiAlias = false
            color = TEXT_COLOR
            typeface = Typeface.DEFAULT_BOLD
        }
        // Largest text that still fits with a one-pixel margin on every side.
        var size = height * 0.8f
        paint.textSize = size
        while (size > 4f && (paint.measureText(label) > width - 2 || paint.fontMetrics.run { descent - ascent } > height - 2)) {
            size -= 1f
            paint.textSize = size
        }
        val metrics = paint.fontMetrics
        val baseline = (height - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val output = ByteArrayOutputStream()
        try {
            val encoder = GifEncoder().apply {
                setSize(width, height)
                setRepeat(0)
                setDelay(FRAME_DELAY_MS)
            }
            check(encoder.start(output)) { "GIF encoder failed to start" }
            for (colonOn in booleanArrayOf(true, false)) {
                val text = if (colonOn) label else label.replace(':', ' ')
                canvas.drawColor(0xFF000000.toInt())
                canvas.drawText(text, (width - paint.measureText(label)) / 2f, baseline, paint)
                check(encoder.addFrame(bitmap)) { "GIF clock frame failed" }
            }
            check(encoder.finish()) { "GIF encoder failed to finish" }
        } finally {
            bitmap.recycle()
        }
        return output.toByteArray()
    }
}
