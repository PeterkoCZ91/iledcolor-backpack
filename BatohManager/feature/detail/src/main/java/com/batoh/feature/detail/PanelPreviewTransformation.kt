package com.batoh.feature.detail

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation
import kotlin.math.floor
import kotlin.math.min

/**
 * Exact 64×64 panel sample (nearest neighbour, centre crop, transparent → black) as the upload
 * converter does it; draw with FilterQuality.None. Same algorithm as feature/library
 * BackpackPreviewScaler (kept local to avoid a feature→feature dependency).
 */
internal class PanelPreviewTransformation : Transformation {
    override val cacheKey: String = "backpack-panel-64-crop"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val source = if (input.config == Bitmap.Config.HARDWARE) input.copy(Bitmap.Config.ARGB_8888, false) else input
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val span = min(width, height)
        val black = 0xff000000.toInt()
        val panel = IntArray(64 * 64) { index ->
            val x = floor((index % 64 + 0.5) * span / 64 + (width - span) / 2.0).toInt().coerceIn(0, width - 1)
            val y = floor((index / 64 + 0.5) * span / 64 + (height - span) / 2.0).toInt().coerceIn(0, height - 1)
            val color = pixels[y * width + x]
            if (color ushr 24 == 0) black else color or black
        }
        return Bitmap.createBitmap(panel, 64, 64, Bitmap.Config.ARGB_8888)
    }
}
