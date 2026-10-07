package com.batoh.core.conversion

import java.io.ByteArrayOutputStream

/** GIF chaining errors; cancellation (exception from the cancel lambda) propagates unchanged. */
sealed class GifConcatException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class EmptyInput : GifConcatException("No GIF selected")
    class InvalidSource(val index: Int, cause: Throwable) :
        GifConcatException("GIF #${index + 1} cannot be read: ${cause.message}", cause)
    class TooManyFrames(val limit: Int) : GifConcatException("Chain has more than $limit frames")
    class TooLarge(val limitBytes: Int) : GifConcatException("The resulting GIF is larger than 20 MB")
}

data class GifConcatOptions(
    val scaleMode: GifScaleMode = GifScaleMode.CenterCrop,
    /** Pause between GIFs in ms (added to the delay of the last frame of each GIF except the last). */
    val pauseBetweenMs: Int = 0,
    /** If set, every frame gets this delay in ms (pauses are added on top). */
    val frameDelayOverrideMs: Int? = null
)

object GifConcatProcessor {
    class Source(val bytes: ByteArray)

    private class Frame(val pixels: IntArray, var delayCs: Int)

    fun concat(
        items: List<Source>,
        options: GifConcatOptions = GifConcatOptions(),
        checkCancellation: () -> Unit = {},
        /** Tests only; in production always [SafeGifDecoder.MAX_BYTES]. */
        maxBytes: Int = SafeGifDecoder.MAX_BYTES
    ): GifEditResult {
        if (items.isEmpty()) throw GifConcatException.EmptyInput()
        require(options.pauseBetweenMs >= 0) { "Pause must not be negative" }
        options.frameDelayOverrideMs?.let { require(it >= 0) { "Speed must not be negative" } }
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
        output.write(0xe6) // Global palette of 128 colors, same as GifEditorProcessor.
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
                output.short(0) // loop forever
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
