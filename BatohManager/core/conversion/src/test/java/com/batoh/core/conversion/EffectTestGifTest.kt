package com.batoh.core.conversion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectTestGifTest {
    @Test
    fun rendersValidSmall64x64Animation() {
        val gif = EffectTestGif.render(3)
        val info = SafeGifDecoder.validate(gif)
        assertEquals(64, info.width)
        assertEquals(64, info.height)
        assertEquals(10, info.frameCount)
        assertTrue("size ${gif.size}", gif.size < 30 * 1024)
    }

    @Test
    fun pictureIsAsymmetricAndFramesDiffer() {
        val a = EffectTestGif.frameIndices(3, 0)
        val mirrored = ByteArray(a.size) { a[(it / 64) * 64 + 63 - it % 64] }
        assertTrue(!a.contentEquals(mirrored))
        assertTrue(!a.contentEquals(EffectTestGif.frameIndices(3, 1)))
        assertNotEquals(a[10 * 64 + 5], a[10 * 64 + 58]) // left and right halves differ
    }

    @Test
    fun differentCodesGiveDifferentGifs() {
        assertTrue(!EffectTestGif.render(0).contentEquals(EffectTestGif.render(1)))
        assertTrue(!EffectTestGif.render(1).contentEquals(EffectTestGif.render(10)))
        assertEquals(64, SafeGifDecoder.validate(EffectTestGif.render(10)).width)
    }
}
