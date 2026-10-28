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

/** Scroll direction: the way the text moves across the panel. */
enum class TextBannerDirection { RightToLeft, LeftToRight }

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
    val bold: Boolean = true,
    val direction: TextBannerDirection = TextBannerDirection.RightToLeft
)

/** Frame plan for one loop: text enters from the right edge and fully leaves on the left. */
data class TextBannerPlan(
    val stepPx: Int,
    val frameCount: Int,
    val delayMs: Int,
    val panelWidth: Int = TextBannerLayout.SIZE,
    val direction: TextBannerDirection = TextBannerDirection.RightToLeft,
    val textWidthPx: Int = 0
) {
    val durationMs: Long get() = frameCount.toLong() * delayMs

    /**
     * X position of the text's left edge in [frame]. Right-to-left: frame 0 starts just off the right
     * edge. Left-to-right: frame 0 starts with the text just off the left edge.
     */
    fun offsetX(frame: Int): Int = when (direction) {
        TextBannerDirection.RightToLeft -> panelWidth - frame * stepPx
        TextBannerDirection.LeftToRight -> -textWidthPx + frame * stepPx
    }
}

/** Pure layout math (no Android dependencies, unit-testable). */
object TextBannerLayout {
    const val SIZE = 64
    const val MAX_FRAMES = 300
    const val MAX_TEXT_LENGTH = 60
    const val MIN_DELAY_MS = 20

    /** Sane panel side range in pixels (known panels run from 16 to 64; room is left for larger ones). */
    const val MIN_PANEL_SIDE = 8
    const val MAX_PANEL_SIDE = 128

    fun isValidPanelSide(side: Int): Boolean = side in MIN_PANEL_SIDE..MAX_PANEL_SIDE

    /**
     * Scroll distance is panel width + text width. The step grows when needed so one loop never
     * exceeds [maxFrames]; the last frame stops one step before the blank "text gone" state, which
     * makes the loop seamless (frame 0 is that blank state).
     */
    fun plan(
        textWidthPx: Int,
        requestedStepPx: Int,
        delayMs: Int,
        panelWidth: Int = SIZE,
        maxFrames: Int = MAX_FRAMES,
        direction: TextBannerDirection = TextBannerDirection.RightToLeft
    ): TextBannerPlan {
        require(maxFrames >= 1) { "maxFrames must be positive" }
        val distance = panelWidth + textWidthPx.coerceAtLeast(0)
        val minStep = ceil(distance.toDouble() / maxFrames).toInt()
        val step = maxOf(1, requestedStepPx, minStep)
        val frames = ceil(distance.toDouble() / step).toInt().coerceIn(1, maxFrames)
        return TextBannerPlan(step, frames, delayMs.coerceAtLeast(MIN_DELAY_MS), panelWidth,
            direction, textWidthPx.coerceAtLeast(0))
    }

    /** Baseline that vertically centres a line with the given font ascent (negative) and descent. */
    fun baseline(ascent: Float, descent: Float, panelHeight: Int = SIZE): Float =
        (panelHeight - (descent - ascent)) / 2f - ascent
}

data class TextBannerResult(val gifBytes: ByteArray, val plan: TextBannerPlan)

/** Renders a looping, horizontally scrolling GIF (64×64 by default) with Canvas and the shared [GifEncoder]. */
object TextBannerRenderer {
    fun render(
        options: TextBannerOptions,
        width: Int = TextBannerLayout.SIZE,
        height: Int = TextBannerLayout.SIZE,
        checkCancellation: () -> Unit = {}
    ): TextBannerResult {
        val text = options.text.trim()
        require(text.isNotEmpty()) { "Text is empty" }
        require(TextBannerLayout.isValidPanelSide(width) && TextBannerLayout.isValidPanelSide(height)) {
            "Unsupported panel size ${width}x$height"
        }
        val paint = Paint().apply {
            // Crisp pixels on the LED panel and a 2-colour image that compresses well.
            isAntiAlias = false
            isSubpixelText = false
            color = options.textColor
            textSize = options.size.textSizePx * height / TextBannerLayout.SIZE
            typeface = if (options.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val textWidth = ceil(paint.measureText(text).toDouble()).toInt()
        val plan = TextBannerLayout.plan(textWidth, options.speed.stepPx, options.speed.delayMs, width,
            direction = options.direction)
        val metrics = paint.fontMetrics
        val baseline = TextBannerLayout.baseline(metrics.ascent, metrics.descent, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val output = ByteArrayOutputStream()
        try {
            val encoder = GifEncoder().apply {
                setSize(width, height)
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
