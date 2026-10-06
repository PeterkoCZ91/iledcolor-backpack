package com.batoh.core.conversion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

/** Scroll speed: pixels moved per frame and frame delay. */
enum class TextBannerSpeed(val stepPx: Int, val delayMs: Int) {
    Slow(1, 100),
    Normal(2, 80),
    Fast(3, 60)
}

/** Font size in panel pixels (the panel is 64 px tall). */
enum class TextBannerSize(val textSizePx: Float) {
    Small(22f),
    Medium(34f),
    Large(48f)
}

data class TextBannerOptions(
    val text: String,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val backgroundColor: Int = 0xFF000000.toInt(),
    val speed: TextBannerSpeed = TextBannerSpeed.Normal,
    val size: TextBannerSize = TextBannerSize.Medium,
    val bold: Boolean = true
)

/** Frame plan for one loop: text enters from the right edge and fully leaves on the left. */
data class TextBannerPlan(val stepPx: Int, val frameCount: Int, val delayMs: Int) {
    val durationMs: Long get() = frameCount.toLong() * delayMs

    /** X position of the text's left edge in [frame]; frame 0 starts just off the right edge. */
    fun offsetX(frame: Int): Int = TextBannerLayout.SIZE - frame * stepPx
}

/** Pure layout math (no Android dependencies, unit-testable). */
object TextBannerLayout {
    const val SIZE = 64
    const val MAX_FRAMES = 300
    const val MAX_TEXT_LENGTH = 60
    const val MIN_DELAY_MS = 20

    /**
     * Scroll distance is panel width + text width. The step grows when needed so one loop never
     * exceeds [maxFrames]; the last frame stops one step before the blank "text gone" state, which
     * makes the loop seamless (frame 0 is that blank state).
     */
    fun plan(textWidthPx: Int, requestedStepPx: Int, delayMs: Int, maxFrames: Int = MAX_FRAMES): TextBannerPlan {
        require(maxFrames >= 1) { "maxFrames must be positive" }
        val distance = SIZE + textWidthPx.coerceAtLeast(0)
        val minStep = ceil(distance.toDouble() / maxFrames).toInt()
        val step = maxOf(1, requestedStepPx, minStep)
        val frames = ceil(distance.toDouble() / step).toInt().coerceIn(1, maxFrames)
        return TextBannerPlan(step, frames, delayMs.coerceAtLeast(MIN_DELAY_MS))
    }

    /** Baseline that vertically centres a line with the given font ascent (negative) and descent. */
    fun baseline(ascent: Float, descent: Float): Float = (SIZE - (descent - ascent)) / 2f - ascent
}

data class TextBannerResult(val gifBytes: ByteArray, val plan: TextBannerPlan)

/** Renders a looping, horizontally scrolling 64×64 GIF with Canvas and the shared [GifEncoder]. */
object TextBannerRenderer {
    fun render(options: TextBannerOptions, checkCancellation: () -> Unit = {}): TextBannerResult {
        val text = options.text.trim()
        require(text.isNotEmpty()) { "Text is empty" }
        val paint = Paint().apply {
            // Crisp pixels on the LED panel and a 2-colour image that compresses well.
            isAntiAlias = false
            isSubpixelText = false
            color = options.textColor
            textSize = options.size.textSizePx
            typeface = if (options.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val width = ceil(paint.measureText(text).toDouble()).toInt()
        val plan = TextBannerLayout.plan(width, options.speed.stepPx, options.speed.delayMs)
        val metrics = paint.fontMetrics
        val baseline = TextBannerLayout.baseline(metrics.ascent, metrics.descent)
        val size = TextBannerLayout.SIZE
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val output = ByteArrayOutputStream()
        try {
            val encoder = GifEncoder().apply {
                setSize(size, size)
                setRepeat(0)
                setDelay(plan.delayMs)
            }
            check(encoder.start(output)) { "GIF encoder failed to start" }
            for (frame in 0 until plan.frameCount) {
                checkCancellation()
                canvas.drawColor(options.backgroundColor or 0xFF000000.toInt())
                canvas.drawText(text, plan.offsetX(frame).toFloat(), baseline, paint)
                check(encoder.addFrame(bitmap)) { "GIF frame $frame failed" }
            }
            check(encoder.finish()) { "GIF encoder failed to finish" }
        } finally {
            bitmap.recycle()
        }
        return TextBannerResult(output.toByteArray(), plan)
    }
}
