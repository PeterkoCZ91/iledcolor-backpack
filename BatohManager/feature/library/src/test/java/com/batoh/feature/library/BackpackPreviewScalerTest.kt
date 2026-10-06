package com.batoh.feature.library

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BackpackPreviewScalerTest {
    private val red = 0xffff0000.toInt()
    private val blue = 0xff0000ff.toInt()
    private val black = 0xff000000.toInt()

    @Test
    fun panelSizedSourceIsCopiedPixelForPixel() {
        val source = IntArray(64 * 64) { 0xff000000.toInt() or it }
        assertArrayEquals(source, BackpackPreviewScaler.scaleToPanel(source, 64, 64))
    }

    @Test
    fun upscalingUsesNearestNeighbourWithoutBlending() {
        // 2×2 checkerboard → four solid 32×32 quadrants, no intermediate colours.
        val source = intArrayOf(red, blue, blue, red)
        val panel = BackpackPreviewScaler.scaleToPanel(source, 2, 2)
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
        val panel = BackpackPreviewScaler.scaleToPanel(intArrayOf(red, blue, red), 3, 1)
        assertEquals(setOf(blue), panel.toSet())
    }

    @Test
    fun fitLetterboxesWithBlackBars() {
        val panel = BackpackPreviewScaler.scaleToPanel(intArrayOf(red, blue), 2, 1, crop = false)
        assertEquals(black, panel[0])            // top bar
        assertEquals(black, panel[63 * 64 + 63]) // bottom bar
        assertEquals(red, panel[32 * 64])        // middle row, left half
        assertEquals(blue, panel[32 * 64 + 63])  // middle row, right half
    }

    @Test
    fun transparentPixelsBecomeBlackLikeTheUploadConverter() {
        val panel = BackpackPreviewScaler.scaleToPanel(intArrayOf(0x00ffffff), 1, 1)
        assertEquals(setOf(black), panel.toSet())
    }
}
