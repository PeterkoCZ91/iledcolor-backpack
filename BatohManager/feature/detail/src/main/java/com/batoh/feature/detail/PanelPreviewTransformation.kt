package com.batoh.feature.detail

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation
import com.batoh.core.conversion.PanelPreviewSampler

/**
 * Exact panel sample as the send converts it ([PanelPreviewSampler], scale mode of the send for the
 * source and panel size); draw with FilterQuality.None.
 */
internal class PanelPreviewTransformation(
    private val panelWidth: Int = PanelPreviewSampler.PANEL_SIZE,
    private val panelHeight: Int = PanelPreviewSampler.PANEL_SIZE
) : Transformation {
    override val cacheKey: String = "backpack-panel-${panelWidth}x$panelHeight-auto"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val source = if (input.config == Bitmap.Config.HARDWARE) input.copy(Bitmap.Config.ARGB_8888, false) else input
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        val crop = PanelPreviewSampler.previewCrop(source.width, source.height, panelWidth, panelHeight)
        val panel = PanelPreviewSampler.scaleToPanel(pixels, source.width, source.height, crop, panelWidth, panelHeight)
        return Bitmap.createBitmap(panel, panelWidth, panelHeight, Bitmap.Config.ARGB_8888)
    }
}
