package com.batoh.core.conversion

/**
 * Pure panel sampling for the library and detail "as on the panel" previews: the same nearest
 * neighbour sampling as [GifEditorProcessor] (centre crop or fit, transparent → black).
 */
object PanelPreviewSampler {
    const val PANEL_SIZE = 64

    /**
     * The scale mode a send uses: the backpack converts with centre crop, an iPixel panel with
     * [GifEditorProcessor.defaultScaleMode].
     */
    fun previewCrop(srcWidth: Int, srcHeight: Int, panelWidth: Int, panelHeight: Int): Boolean =
        (panelWidth == PANEL_SIZE && panelHeight == PANEL_SIZE) ||
            GifEditorProcessor.defaultScaleMode(srcWidth, srcHeight, panelWidth, panelHeight) == GifScaleMode.CenterCrop

    fun scaleToPanel(
        pixels: IntArray,
        width: Int,
        height: Int,
        crop: Boolean = true,
        panelWidth: Int = PANEL_SIZE,
        panelHeight: Int = PANEL_SIZE
    ): IntArray {
        require(width > 0 && height > 0 && pixels.size >= width * height) { "Invalid source size" }
        val options = GifEditOptions(scaleMode = if (crop) GifScaleMode.CenterCrop else GifScaleMode.Fit)
        return GifEditorProcessor.transformPixels(pixels, width, height, options, panelWidth, panelHeight)
    }
}
