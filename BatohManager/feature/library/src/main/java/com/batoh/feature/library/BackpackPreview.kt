package com.batoh.feature.library

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Pure 64×64 panel sampling that mirrors GifEditorProcessor (nearest neighbour, centre crop by
 * default, transparent → black). Library "send" converts non-64×64 GIFs with these defaults.
 */
object BackpackPreviewScaler {
    const val PANEL_SIZE = 64
    private const val BLACK = 0xff000000.toInt()

    fun scaleToPanel(pixels: IntArray, width: Int, height: Int, crop: Boolean = true): IntArray {
        require(width > 0 && height > 0 && pixels.size >= width * height) { "Invalid source size" }
        val span = if (crop) min(width, height) else max(width, height)
        return IntArray(PANEL_SIZE * PANEL_SIZE) { index ->
            val x = floor((index % PANEL_SIZE + 0.5) * span / PANEL_SIZE + (width - span) / 2.0).toInt()
            val y = floor((index / PANEL_SIZE + 0.5) * span / PANEL_SIZE + (height - span) / 2.0).toInt()
            if (x !in 0 until width || y !in 0 until height) BLACK
            else {
                val color = pixels[y * width + x]
                if (color ushr 24 == 0) BLACK else color or BLACK
            }
        }
    }
}

/** Coil transformation producing the exact 64×64 bitmap; draw it with FilterQuality.None. */
class BackpackPreviewTransformation(private val crop: Boolean = true) : Transformation {
    override val cacheKey: String = "backpack-panel-64-${if (crop) "crop" else "fit"}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val source = if (input.config == Bitmap.Config.HARDWARE) input.copy(Bitmap.Config.ARGB_8888, false) else input
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        val panel = BackpackPreviewScaler.scaleToPanel(pixels, source.width, source.height, crop)
        return Bitmap.createBitmap(panel, BackpackPreviewScaler.PANEL_SIZE, BackpackPreviewScaler.PANEL_SIZE,
            Bitmap.Config.ARGB_8888)
    }
}
