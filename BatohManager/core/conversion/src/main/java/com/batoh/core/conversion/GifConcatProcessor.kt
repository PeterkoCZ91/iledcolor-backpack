package com.batoh.core.conversion

import java.io.ByteArrayOutputStream

/** Chyby řetězení GIFů; zrušení (výjimka z cancel lambdy) se propaguje beze změny. */
sealed class GifConcatException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class EmptyInput : GifConcatException("Není vybrán žádný GIF")
    class InvalidSource(val index: Int, cause: Throwable) :
        GifConcatException("GIF č. ${index + 1} nelze přečíst: ${cause.message}", cause)
    class TooManyFrames(val limit: Int) : GifConcatException("Řetěz má více než $limit snímků")
    class TooLarge(val limitBytes: Int) : GifConcatException("Výsledný GIF je větší než 20 MB")
}

data class GifConcatOptions(
    val scaleMode: GifScaleMode = GifScaleMode.CenterCrop,
    /** Pauza mezi GIFy v ms (přidá se k delay posledního snímku každého GIFu kromě posledního). */
    val pauseBetweenMs: Int = 0,
    /** Je-li zadáno, každý snímek dostane tento delay v ms (pauzy se přičítají navíc). */
    val frameDelayOverrideMs: Int? = null
)

object GifConcatProcessor {
    class Source(val bytes: ByteArray)

    private class Frame(val pixels: IntArray, var delayCs: Int)

    fun concat(
        items: List<Source>,
        options: GifConcatOptions = GifConcatOptions(),
        checkCancellation: () -> Unit = {},
        /** Jen pro testy; produkčně vždy [SafeGifDecoder.MAX_BYTES]. */
        maxBytes: Int = SafeGifDecoder.MAX_BYTES
    ): GifEditResult {
        if (items.isEmpty()) throw GifConcatException.EmptyInput()
        require(options.pauseBetweenMs >= 0) { "Pauza nesmí být záporná" }
        options.frameDelayOverrideMs?.let { require(it >= 0) { "Rychlost nesmí být záporná" } }
        val editOptions = GifEditOptions(scaleMode = options.scaleMode)
        val frames = ArrayList<Frame>()
        for ((index, item) in items.withIndex()) {
            checkCancellation()
            val first = frames.size
            var cancelled: Throwable? = null
            val guarded = { try { checkCancellation() } catch (t: Throwable) { cancelled = t; throw t } }
            try {
                SafeGifDecoder.decode(item.bytes, guarded) { pixels, width, height, delay ->
                    guarded()
                    if (frames.size >= SafeGifDecoder.MAX_FRAMES) throw GifConcatException.TooManyFrames(SafeGifDecoder.MAX_FRAMES)
                    val delayCs = options.frameDelayOverrideMs?.let { msToCs(it) } ?: delay
                    frames += Frame(GifEditorProcessor.transformPixels(pixels, width, height, editOptions), delayCs)
                }
            } catch (e: GifConcatException) {
                throw e
            } catch (e: IllegalArgumentException) {
                cancelled?.let { throw it }
                throw GifConcatException.InvalidSource(index, e)
            }
            if (index < items.lastIndex && options.pauseBetweenMs > 0 && frames.size > first) {
                val last = frames.last()
                last.delayCs = (last.delayCs + msToCs(options.pauseBetweenMs)).coerceAtMost(65535)
            }
        }
        return encode(frames, checkCancellation, maxBytes)
    }

    private fun msToCs(ms: Int) = ((ms + 5) / 10).coerceIn(0, 65535)

    private fun encode(frames: List<Frame>, checkCancellation: () -> Unit, maxBytes: Int): GifEditResult {
        val output = ByteArrayOutputStream()
        output.write("GIF89a".toByteArray(Charsets.US_ASCII))
        output.short(64)
        output.short(64)
        output.write(0xe6) // Globální paleta 128 barev jako u GifEditorProcessor.
        output.write(0)
        output.write(0)
        var duration = 0L
        for ((i, frame) in frames.withIndex()) {
            checkCancellation()
            val (palette, indices) = GifEditorProcessor.indexed(frame.pixels)
            if (i == 0) {
                output.write(palette)
                output.write(byteArrayOf(0x21, 0xff.toByte(), 11))
                output.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
                output.write(3)
                output.write(1)
                output.short(0) // loop navždy
                output.write(0)
            }
            output.write(byteArrayOf(0x21, 0xf9.toByte(), 4, 4)) // disposal 1
            output.short(frame.delayCs)
            output.write(0)
            output.write(0)
            output.write(0x2c)
            output.short(0); output.short(0); output.short(64); output.short(64)
            output.write(if (i == 0) 0 else 0x86)
            if (i != 0) output.write(palette)
            LzwEncoder(64, 64, indices, 7).encode(output)
            duration += frame.delayCs * 10L
            if (output.size() >= maxBytes) throw GifConcatException.TooLarge(maxBytes)
        }
        output.write(0x3b)
        if (output.size() > maxBytes) throw GifConcatException.TooLarge(maxBytes)
        return GifEditResult(output.toByteArray(), GifInfo(64, 64, frames.size, duration, 0))
    }

    private fun ByteArrayOutputStream.short(value: Int) {
        write(value and 255)
        write((value ushr 8) and 255)
    }
}
