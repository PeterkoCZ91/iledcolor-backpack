package com.batoh.feature.library

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation
import com.batoh.core.conversion.PanelPreviewSampler

/**
 * Coil transformation producing the exact panel-sized bitmap with [PanelPreviewSampler]; draw it with
 * FilterQuality.None. [crop] null uses the send's scale mode for the source and panel size.
 */
class BackpackPreviewTransformation(
    private val crop: Boolean? = null,
    private val panelWidth: Int = PanelPreviewSampler.PANEL_SIZE,
    private val panelHeight: Int = PanelPreviewSampler.PANEL_SIZE
) : Transformation {
    override val cacheKey: String = "backpack-panel-${panelWidth}x$panelHeight-${crop?.let { if (it) "crop" else "fit" } ?: "auto"}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val source = if (input.config == Bitmap.Config.HARDWARE) input.copy(Bitmap.Config.ARGB_8888, false) else input
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        val crop = crop ?: PanelPreviewSampler.previewCrop(source.width, source.height, panelWidth, panelHeight)
        val panel = PanelPreviewSampler.scaleToPanel(pixels, source.width, source.height, crop, panelWidth, panelHeight)
        return Bitmap.createBitmap(panel, panelWidth, panelHeight, Bitmap.Config.ARGB_8888)
    }
}
