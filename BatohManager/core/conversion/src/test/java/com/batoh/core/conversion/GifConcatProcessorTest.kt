package com.batoh.core.conversion

import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class GifConcatProcessorTest {
    /** Jednobarevné snímky dané velikosti; delays v setinách sekundy. */
    private fun gif(width: Int, height: Int, delays: List<Int>, shade: Int = 1): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("GIF89a".toByteArray())
        out.short(width); out.short(height)
        out.write(0x80 or 1); out.write(0); out.write(0) // 4 barvy
        for (c in listOf(intArrayOf(0, 0, 0), intArrayOf(255, 0, 0), intArrayOf(0, 255, 0), intArrayOf(0, 0, 255))) c.forEach { out.write(it) }
        for (d in delays) {
            out.write(byteArrayOf(0x21, 0xf9.toByte(), 4, 4)); out.short(d); out.write(0); out.write(0)
            out.write(0x2c); out.short(0); out.short(0); out.short(width); out.short(height); out.write(0)
            LzwEncoder(width, height, ByteArray(width * height) { shade.toByte() }, 2).encode(out)
        }
        out.write(0x3b)
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.short(v: Int) { write(v and 255); write((v ushr 8) and 255) }
    private fun src(vararg g: ByteArray) = g.map { GifConcatProcessor.Source(it) }

    @Test
    fun framesDelaysAndSizeAreSummedAndResultValidates() {
        val r = GifConcatProcessor.concat(src(gif(64, 64, listOf(5, 7)), gif(32, 48, listOf(10), 2)))
        assertEquals(3, r.info.frameCount)
        assertEquals(220L, r.info.durationMs)
        assertEquals(64, r.info.width); assertEquals(64, r.info.height)
        assertEquals(0, r.info.loopCount)
        val check = SafeGifDecoder.validate(r.gifBytes)
        assertEquals(3, check.frameCount)
        assertEquals(220L, check.durationMs)
        assertEquals(64, check.width); assertEquals(64, check.height)
        assertEquals(0, check.loopCount)
        assertEquals(0x3b, r.gifBytes.last().toInt())
    }

    @Test
    fun pauseIsAddedToLastFrameOfEveryGifExceptLast() {
        val r = GifConcatProcessor.concat(
            src(gif(64, 64, listOf(5)), gif(64, 64, listOf(5)), gif(64, 64, listOf(5))),
            GifConcatOptions(pauseBetweenMs = 300)
        )
        assertEquals(50L + 50 + 50 + 600, r.info.durationMs)
        assertEquals(r.info.durationMs, SafeGifDecoder.validate(r.gifBytes).durationMs)
    }

    @Test
    fun speedOverrideReplacesDelays() {
        val r = GifConcatProcessor.concat(src(gif(64, 64, listOf(5, 7, 9))), GifConcatOptions(frameDelayOverrideMs = 100))
        assertEquals(300L, SafeGifDecoder.validate(r.gifBytes).durationMs)
    }

    @Test
    fun fitAndCropProduceDifferentCornersAndFitHasBlackBorders() {
        val wide = gif(128, 32, listOf(5), 1)
        fun pixels(mode: GifScaleMode): IntArray {
            var p: IntArray? = null
            SafeGifDecoder.decode(GifConcatProcessor.concat(src(wide), GifConcatOptions(scaleMode = mode)).gifBytes, {}) { px, _, _, _ -> p = px.copyOf() }
            return p!!
        }
        val fit = pixels(GifScaleMode.Fit)
        val crop = pixels(GifScaleMode.CenterCrop)
        assertEquals(0xff000000.toInt(), fit[2 * 64 + 32])
        assertEquals(0xffff0000.toInt(), fit[32 * 64 + 32])
        assertEquals(0xffff0000.toInt(), crop[2 * 64 + 32])
    }

    @Test
    fun rejectsEmptyAndInvalidInput() {
        assertThrows(GifConcatException.EmptyInput::class.java) { GifConcatProcessor.concat(emptyList()) }
        val e = assertThrows(GifConcatException.InvalidSource::class.java) {
            GifConcatProcessor.concat(src(gif(64, 64, listOf(5)), ByteArray(40) { 7 }))
        }
        assertEquals(1, e.index)
    }

    @Test
    fun rejectsMoreThan600Frames() {
        val big = gif(64, 64, List(300) { 1 })
        GifConcatProcessor.concat(src(big, big)) // přesně 600 projde
        assertThrows(GifConcatException.TooManyFrames::class.java) { GifConcatProcessor.concat(src(big, big, gif(64, 64, listOf(1)))) }
    }

    @Test
    fun rejectsOutputOverByteLimit() {
        val g = gif(64, 64, List(10) { 1 })
        val ok = GifConcatProcessor.concat(src(g))
        assertThrows(GifConcatException.TooLarge::class.java) {
            GifConcatProcessor.concat(src(g), maxBytes = ok.gifBytes.size / 2)
        }
        assertEquals(ok.gifBytes.size, GifConcatProcessor.concat(src(g), maxBytes = ok.gifBytes.size).gifBytes.size)
    }

    @Test
    fun cancellationPropagatesUnchanged() {
        class Stop : RuntimeException()
        var calls = 0
        assertThrows(Stop::class.java) {
            GifConcatProcessor.concat(src(gif(64, 64, listOf(5, 5, 5))), checkCancellation = { if (++calls > 3) throw Stop() })
        }
    }
}
