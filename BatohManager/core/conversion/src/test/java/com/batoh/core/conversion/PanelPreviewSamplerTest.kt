package com.batoh.core.conversion

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelPreviewSamplerTest {
    private val red = 0xffff0000.toInt()
    private val blue = 0xff0000ff.toInt()
    private val black = 0xff000000.toInt()

    @Test
    fun panelSizedSourceIsCopiedPixelForPixel() {
        val source = IntArray(64 * 64) { 0xff000000.toInt() or it }
        assertArrayEquals(source, PanelPreviewSampler.scaleToPanel(source, 64, 64))
    }

    @Test
    fun upscalingUsesNearestNeighbourWithoutBlending() {
        // 2×2 checkerboard → four solid 32×32 quadrants, no intermediate colours.
        val source = intArrayOf(red, blue, blue, red)
        val panel = PanelPreviewSampler.scaleToPanel(source, 2, 2)
        assertEquals(64 * 64, panel.size)
        assertEquals(setOf(red, blue), panel.toSet())
        assertEquals(red, panel[0])
        assertEquals(red, panel[31 * 64 + 31])
        assertEquals(blue, panel[32])
        assertEquals(blue, panel[32 * 64])
        assertEquals(red, panel[63 * 64 + 63])
    }

    @Test
    fun wideSourceIsCentreCroppedByDefault() {
        // 3×1: left red, middle blue, right red → crop keeps only the middle column.
        val panel = PanelPreviewSampler.scaleToPanel(intArrayOf(red, blue, red), 3, 1)
        assertEquals(setOf(blue), panel.toSet())
    }

    @Test
    fun fitLetterboxesWithBlackBars() {
        val panel = PanelPreviewSampler.scaleToPanel(intArrayOf(red, blue), 2, 1, crop = false)
        assertEquals(black, panel[0])            // top bar
        assertEquals(black, panel[63 * 64 + 63]) // bottom bar
        assertEquals(red, panel[32 * 64])        // middle row, left half
        assertEquals(blue, panel[32 * 64 + 63])  // middle row, right half
    }

    @Test
    fun transparentPixelsBecomeBlackLikeTheUploadConverter() {
        val panel = PanelPreviewSampler.scaleToPanel(intArrayOf(0x00ffffff), 1, 1)
        assertEquals(setOf(black), panel.toSet())
    }

    @Test
    fun scaleToPanel_64x16Fit() {
        // Square source on a 64×16 strip: fit keeps the whole square in the centre 16 columns.
        val panel = PanelPreviewSampler.scaleToPanel(IntArray(4 * 4) { red }, 4, 4, crop = false, panelWidth = 64, panelHeight = 16)
        assertEquals(64 * 16, panel.size)
        for (row in 0 until 16) for (column in 0 until 64) {
            assertEquals("($column, $row)", if (column in 24..39) red else black, panel[row * 64 + column])
        }
    }

    @Test
    fun previewCrop_followsTheUploadScaleMode() {
        assertTrue(PanelPreviewSampler.previewCrop(160, 90, 64, 32))   // 16:9 on 2:1 → crop, like the upload
        assertFalse(PanelPreviewSampler.previewCrop(200, 100, 32, 32)) // 2:1 on square → fit
        assertFalse(PanelPreviewSampler.previewCrop(100, 100, 64, 16)) // square on 4:1 strip → fit
        assertTrue(PanelPreviewSampler.previewCrop(300, 50, 64, 64))   // the backpack always crops
    }
}
