package com.batoh.core.conversion

import java.io.ByteArrayOutputStream
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

enum class GifScaleMode { CenterCrop, Fit }

data class GifEditOptions(
    val rotationDegrees: Int = 0,
    val mirrorHorizontal: Boolean = false,
    val mirrorVertical: Boolean = false,
    val scaleMode: GifScaleMode = GifScaleMode.CenterCrop
)

data class GifEditResult(val gifBytes: ByteArray, val info: GifInfo)

/** Pixel edits are applied to composed animation frames, preserving each delay and loop count. */
object GifEditorProcessor {
    fun transform(bytes: ByteArray, options: GifEditOptions = GifEditOptions(), checkCancellation: () -> Unit = {}): GifEditResult {
        val frames = mutableListOf<Pair<IntArray, Int>>()
        val source = SafeGifDecoder.decode(bytes, checkCancellation) { pixels, width, height, delay ->
            checkCancellation()
            frames += transformPixels(pixels, width, height, options) to delay
        }
        val output = ByteArrayOutputStream()
        output.write("GIF89a".toByteArray(Charsets.US_ASCII))
        output.short(64)
        output.short(64)
        output.write(0xe6) // Global 128-color palette, matching the panel's GIF format.
        output.write(0)
        output.write(0)
        for ((frameIndex, frame) in frames.withIndex()) {
            checkCancellation()
            val (palette, indices) = indexed(frame.first)
            if (frameIndex == 0) {
                output.write(palette)
                source.loopCount?.let { loops ->
                    output.write(byteArrayOf(0x21, 0xff.toByte(), 11))
                    output.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
                    output.write(3)
                    output.write(1)
                    output.short(loops)
                    output.write(0)
                }
            }
            output.write(byteArrayOf(0x21, 0xf9.toByte(), 4, 4)) // Dispose 1, fully opaque composed frame.
            output.short(frame.second)
            output.write(0)
            output.write(0)
            output.write(0x2c)
            output.short(0)
            output.short(0)
            output.short(64)
            output.short(64)
            output.write(if (frameIndex == 0) 0 else 0x86)
            if (frameIndex != 0) output.write(palette)
            LzwEncoder(64, 64, indices, 7).encode(output)
        }
        output.write(0x3b)
        return GifEditResult(output.toByteArray(), source.copy(width = 64, height = 64))
    }

    internal fun transformPixels(pixels: IntArray, width: Int, height: Int, options: GifEditOptions): IntArray {
        require(options.rotationDegrees in listOf(0, 90, 180, 270)) { "Rotation must be 0°, 90°, 180° or 270°" }
        val quarterTurn = options.rotationDegrees == 90 || options.rotationDegrees == 270
        val rotatedWidth = if (quarterTurn) height else width
        val rotatedHeight = if (quarterTurn) width else height
        val span = if (options.scaleMode == GifScaleMode.CenterCrop) min(rotatedWidth, rotatedHeight) else max(rotatedWidth, rotatedHeight)
        return IntArray(64 * 64) { index ->
            val x = floor((index % 64 + 0.5) * span / 64.0 + (rotatedWidth - span) / 2.0).toInt()
            val y = floor((index / 64 + 0.5) * span / 64.0 + (rotatedHeight - span) / 2.0).toInt()
            if (x !in 0 until rotatedWidth || y !in 0 until rotatedHeight) {
                0xff000000.toInt()
            } else {
                val mirroredX = if (options.mirrorHorizontal) rotatedWidth - 1 - x else x
                val mirroredY = if (options.mirrorVertical) rotatedHeight - 1 - y else y
                val (originalX, originalY) = when (options.rotationDegrees) {
                    90 -> mirroredY to (height - 1 - mirroredX)
                    180 -> (width - 1 - mirroredX) to (height - 1 - mirroredY)
                    270 -> (width - 1 - mirroredY) to mirroredX
                    else -> mirroredX to mirroredY
                }
                val color = pixels[originalY * width + originalX]
                if (color ushr 24 == 0) 0xff000000.toInt() else color or 0xff000000.toInt()
            }
        }
    }

    internal fun indexed(pixels: IntArray): Pair<ByteArray, ByteArray> {
        val unique = linkedMapOf<Int, Int>()
        for (color in pixels) {
            if (!unique.containsKey(color)) unique[color] = unique.size
            if (unique.size > 128) break
        }
        val palette = ByteArray(128 * 3)
        if (unique.size <= 128) {
            for ((color, index) in unique) {
                palette[index * 3] = (color ushr 16).toByte()
                palette[index * 3 + 1] = (color ushr 8).toByte()
                palette[index * 3 + 2] = color.toByte()
            }
            return palette to ByteArray(pixels.size) { requireNotNull(unique[pixels[it]]).toByte() }
        }
        val rgb = ByteArray(pixels.size * 3)
        pixels.forEachIndexed { index, color ->
            rgb[index * 3] = (color ushr 16).toByte()
            rgb[index * 3 + 1] = (color ushr 8).toByte()
            rgb[index * 3 + 2] = color.toByte()
        }
        val quantizer = NeuQuant(rgb, rgb.size, 10, 128)
        val colors = quantizer.process()
        return colors to ByteArray(pixels.size) { index ->
            val color = pixels[index]
            quantizer.map((color ushr 16) and 255, (color ushr 8) and 255, color and 255).toByte()
        }
    }

    private fun ByteArrayOutputStream.short(value: Int) {
        write(value and 255)
        write((value ushr 8) and 255)
    }
}
