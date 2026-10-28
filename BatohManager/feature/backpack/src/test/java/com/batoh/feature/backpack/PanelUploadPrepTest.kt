package com.batoh.feature.backpack

import com.batoh.core.conversion.GifEditorProcessor
import com.batoh.core.conversion.SafeGifDecoder
import com.batoh.core.data.bluetooth.BackpackPayload
import com.batoh.core.data.bluetooth.PanelFamily
import com.batoh.core.data.bluetooth.PanelSpec
import com.batoh.core.data.bluetooth.UploadFailure
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PanelUploadPrepTest {
    private val noConversion: suspend (ByteArray) -> ByteArray = { error("a 64x64 GIF needs no conversion") }

    /** 64x64 two-frame GIF (copy of the conversion module's golden output); TestPatternGif needs android.graphics. */
    private val fixture: ByteArray = requireNotNull(javaClass.getResourceAsStream("/fixture_64x64.gif")).use { it.readBytes() }

    @Test
    fun prepare_backpackBytesUnchanged() = runBlocking {
        val gif = fixture
        val prepared = PanelUploadPrep.prepare(gif, PanelSpec.BACKPACK_DEFAULT, noConversion)
        assertArrayEquals(BackpackPayload.fromGif(gif), prepared)
    }

    @Test
    fun prepare_ipixelConverts64x64To32x32() = runBlocking {
        val gif = fixture
        val prepared = PanelUploadPrep.prepare(gif, PanelSpec(PanelFamily.IPixel, 32, 32, true), noConversion)
        assertEquals(32 to 32, BackpackPayload.gifSize(prepared))
        assertEquals(SafeGifDecoder.validate(gif).frameCount, SafeGifDecoder.validate(prepared).frameCount)
    }

    @Test
    fun prepare_ipixelKeepsAGifOfPanelSize() = runBlocking {
        val gif = GifEditorProcessor.transform(fixture, width = 64, height = 16).gifBytes
        val prepared = PanelUploadPrep.prepare(gif, PanelSpec(PanelFamily.IPixel, 64, 16, true), noConversion)
        assertArrayEquals(gif, prepared)
    }

    @Test
    fun prepare_ipixelRejectsANonGif() {
        val e = assertThrows(UploadFailureException::class.java) {
            runBlocking { PanelUploadPrep.prepare(ByteArray(40), PanelSpec(PanelFamily.IPixel, 32, 32, true), noConversion) }
        }
        assertEquals(UploadFailure.NotAGif, e.failure)
    }

    @Test
    fun eagerProgramme_64x64OnBackpackBuiltRightAway() {
        val playback = EditorPlaybackOptions(speed = 50, light = 80)
        assertArrayEquals(BackpackPayload.fromGif(fixture), PanelUploadPrep.eagerProgramme(fixture, ipixelLink = false, playback = null))
        assertArrayEquals(playback.buildPayload(fixture), PanelUploadPrep.eagerProgramme(fixture, ipixelLink = false, playback = playback))
    }

    @Test
    fun eagerProgramme_deferredForOtherSizesAndIPixel() {
        // Rendered for an iPixel strip while disconnected: the target is known only once Ready.
        val strip = GifEditorProcessor.transform(fixture, width = 64, height = 16).gifBytes
        assertNull(PanelUploadPrep.eagerProgramme(strip, ipixelLink = false, playback = null))
        assertNull(PanelUploadPrep.eagerProgramme(fixture, ipixelLink = true, playback = null))
    }

    @Test
    fun readyGifProgramme_64x64BytesUnchanged() = runBlocking {
        val playback = EditorPlaybackOptions(speed = 50, light = 80)
        assertArrayEquals(BackpackPayload.fromGif(fixture), PanelUploadPrep.readyGifProgramme(fixture, null, noConversion))
        assertArrayEquals(playback.buildPayload(fixture), PanelUploadPrep.readyGifProgramme(fixture, playback, noConversion))
    }

    @Test
    fun readyGifProgramme_otherSizesScaledTo64x64First() = runBlocking {
        val strip = GifEditorProcessor.transform(fixture, width = 64, height = 16).gifBytes
        var converted: ByteArray? = null
        val payload = PanelUploadPrep.readyGifProgramme(strip, null) { converted = it; fixture }
        assertArrayEquals(strip, converted)
        assertArrayEquals(BackpackPayload.fromGif(fixture), payload)
    }
}
