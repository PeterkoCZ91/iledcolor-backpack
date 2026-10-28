package com.batoh.core.conversion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextLoopLayoutTest {
    @Test
    fun fourMessagesFillFortySecondsWithinGifFrameLimit() {
        assertEquals(4, TextLoopLayout.ITEM_COUNT)
        assertEquals(100, TextLoopLayout.FRAMES_PER_ITEM)
        assertEquals(400, TextLoopLayout.FRAME_COUNT)
        assertEquals(40_000, TextLoopLayout.DURATION_MS)
        assertTrue(TextLoopLayout.FRAME_COUNT <= SafeGifDecoder.MAX_FRAMES)
    }

    @Test
    fun everyMessageTraversesA64By16PanelInItsTenSeconds() {
        val width = 64
        val textWidth = 120
        assertEquals(width, TextLoopLayout.offsetX(width, textWidth, 0))
        assertEquals(-textWidth, TextLoopLayout.offsetX(width, textWidth, 99))
        for (frame in 1 until TextLoopLayout.FRAMES_PER_ITEM) {
            assertTrue(TextLoopLayout.offsetX(width, textWidth, frame) <=
                TextLoopLayout.offsetX(width, textWidth, frame - 1))
        }
    }

    @Test
    fun sameTimingWorksForOtherPanelWidths() {
        for (width in listOf(32, 64, 448)) {
            assertEquals(width, TextLoopLayout.offsetX(width, 20, 0))
            assertEquals(-20, TextLoopLayout.offsetX(width, 20, 99))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun frameOutsideMessageIsRejected() {
        TextLoopLayout.offsetX(64, 20, TextLoopLayout.FRAMES_PER_ITEM)
    }
}
