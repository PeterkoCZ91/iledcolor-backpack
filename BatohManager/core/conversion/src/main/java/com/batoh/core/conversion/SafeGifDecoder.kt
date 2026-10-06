package com.batoh.core.conversion

import java.io.ByteArrayOutputStream

data class GifInfo(
    val width: Int,
    val height: Int,
    val frameCount: Int,
    val durationMs: Long,
    /** null = play once; 0 = repeat forever; positive = repeat count. */
    val loopCount: Int?
)

/** Bounded GIF89a parser and LZW decoder. Malformed data never enters a native decoder.
 * Format reference: https://www.w3.org/Graphics/GIF/spec-gif89a.txt
 */
object SafeGifDecoder {
    const val MAX_BYTES = 20 * 1024 * 1024
    const val MAX_INPUT_BYTES = MAX_BYTES
    const val MAX_FRAMES = 600
    private const val MAX_CANVAS_PIXELS = 4 * 1024 * 1024
    private const val MAX_DECODED_PIXELS = 64 * 1024 * 1024L

    fun validate(bytes: ByteArray, checkCancellation: () -> Unit = {}): GifInfo =
        decode(bytes, checkCancellation) { _, _, _, _ -> }

    internal fun decode(
        bytes: ByteArray,
        checkCancellation: () -> Unit,
        onFrame: (pixels: IntArray, width: Int, height: Int, delayCs: Int) -> Unit
    ): GifInfo {
        require(bytes.size in 13..MAX_INPUT_BYTES) { "GIF je prázdný nebo větší než 20 MB" }
        val input = Reader(bytes)
        val signature = String(input.take(6), Charsets.US_ASCII)
        require(signature == "GIF87a" || signature == "GIF89a") { "Soubor není GIF" }
        val width = input.short()
        val height = input.short()
        require(width in 1..4096 && height in 1..4096 && width.toLong() * height <= MAX_CANVAS_PIXELS) {
            "GIF má příliš velké rozměry (nejvýše 4 miliony pixelů)"
        }
        val packed = input.byte()
        val backgroundIndex = input.byte()
        input.byte() // Pixel aspect ratio.
        val globalPalette = if (packed and 0x80 != 0) input.palette(1 shl ((packed and 7) + 1)) else null
        val background = globalPalette?.getOrNull(backgroundIndex) ?: 0xff000000.toInt()
        val canvas = IntArray(width * height) { background }
        var delay = 0
        var disposal = 0
        var transparent = -1
        var loops: Int? = null
        var frameCount = 0
        var duration = 0L
        var decodedPixels = 0L
        while (true) {
            checkCancellation()
            when (input.byte()) {
                0x3b -> {
                    require(frameCount > 0) { "GIF neobsahuje žádný snímek" }
                    return GifInfo(width, height, frameCount, duration, loops)
                }
                0x21 -> when (input.byte()) {
                    0xf9 -> {
                        require(input.byte() == 4) { "Neplatný řídicí blok GIFu" }
                        val flags = input.byte()
                        disposal = (flags ushr 2) and 7
                        require(disposal <= 3) { "Nepodporovaný způsob skládání snímků GIFu" }
                        delay = input.short()
                        val index = input.byte()
                        transparent = if (flags and 1 != 0) index else -1
                        require(input.byte() == 0) { "Neukončený řídicí blok GIFu" }
                    }
                    0xff -> {
                        val application = String(input.take(input.byte()), Charsets.US_ASCII)
                        val extension = input.blocks()
                        if (application == "NETSCAPE2.0" || application == "ANIMEXTS1.0") {
                            require(extension.size >= 3 && extension[0].toInt() == 1) { "Neplatná smyčka GIFu" }
                            loops = (extension[1].toInt() and 255) or ((extension[2].toInt() and 255) shl 8)
                        }
                    }
                    0x01 -> throw IllegalArgumentException("GIF s textovými snímky není podporovaný")
                    else -> input.blocks()
                }
                0x2c -> {
                    require(++frameCount <= MAX_FRAMES) { "GIF má více než 600 snímků" }
                    val left = input.short()
                    val top = input.short()
                    val frameWidth = input.short()
                    val frameHeight = input.short()
                    require(frameWidth > 0 && frameHeight > 0 && left + frameWidth <= width && top + frameHeight <= height) {
                        "Snímek GIFu přesahuje plátno"
                    }
                    decodedPixels += frameWidth.toLong() * frameHeight
                    require(decodedPixels <= MAX_DECODED_PIXELS) { "GIF obsahuje příliš mnoho obrazových dat" }
                    val frameFlags = input.byte()
                    val palette = if (frameFlags and 0x80 != 0) input.palette(1 shl ((frameFlags and 7) + 1))
                        else requireNotNull(globalPalette) { "GIF nemá paletu barev" }
                    val minCodeSize = input.byte()
                    val indices = decodeLzw(input.blocks(), minCodeSize, frameWidth * frameHeight, checkCancellation)
                    if (frameCount == 1 && transparent >= 0) canvas.fill(0)
                    val previous = if (disposal == 3) canvas.copyOf() else null
                    var sourceOffset = 0
                    fun row(y: Int) {
                        for (x in 0 until frameWidth) {
                            val index = indices[sourceOffset++].toInt() and 255
                            if (index != transparent) {
                                require(index < palette.size) { "GIF odkazuje na neexistující barvu" }
                                canvas[(top + y) * width + left + x] = palette[index]
                            }
                        }
                    }
                    if (frameFlags and 0x40 != 0) {
                        for ((start, step) in listOf(0 to 8, 4 to 8, 2 to 4, 1 to 2)) {
                            for (y in start until frameHeight step step) row(y)
                        }
                    } else for (y in 0 until frameHeight) row(y)
                    onFrame(canvas, width, height, delay)
                    duration += delay * 10L
                    when (disposal) {
                        2 -> {
                            val clearColor = if (transparent >= 0) 0 else background
                            for (y in top until top + frameHeight) {
                                canvas.fill(clearColor, y * width + left, y * width + left + frameWidth)
                            }
                        }
                        3 -> requireNotNull(previous).copyInto(canvas)
                    }
                    delay = 0
                    disposal = 0
                    transparent = -1
                }
                else -> throw IllegalArgumentException("Poškozený nebo neukončený GIF")
            }
        }
    }

    private fun decodeLzw(data: ByteArray, minimum: Int, count: Int, checkCancellation: () -> Unit): ByteArray {
        require(minimum in 2..8) { "Neplatná velikost LZW kódu" }
        val clear = 1 shl minimum
        val end = clear + 1
        val prefix = IntArray(4096)
        val suffix = ByteArray(4096)
        repeat(clear) { suffix[it] = it.toByte() }
        val stack = ByteArray(4097)
        val output = ByteArray(count)
        var outputOffset = 0
        var bitOffset = 0
        var size = minimum + 1
        var next = clear + 2
        var old = -1
        var first = 0
        while (true) {
            if (outputOffset and 4095 == 0) checkCancellation()
            require(bitOffset.toLong() + size <= data.size.toLong() * 8) { "Neukončená LZW data GIFu" }
            var code = 0
            repeat(size) { bit ->
                code = code or (((data[(bitOffset + bit) / 8].toInt() ushr ((bitOffset + bit) % 8)) and 1) shl bit)
            }
            bitOffset += size
            if (code == clear) {
                size = minimum + 1
                next = clear + 2
                old = -1
                continue
            }
            if (code == end) {
                require(outputOffset == count) { "GIF má neúplný snímek" }
                return output
            }
            require(code < next || (code == next && old >= 0 && next < 4096)) { "Poškozený LZW slovník GIFu" }
            val incoming = code
            var stackSize = 0
            if (code == next) {
                stack[stackSize++] = first.toByte()
                code = old
            }
            while (code >= clear) {
                require(code < next && code != clear && code != end && stackSize < 4096) { "Poškozený LZW řetězec GIFu" }
                stack[stackSize++] = suffix[code]
                code = prefix[code]
            }
            first = code
            stack[stackSize++] = first.toByte()
            require(outputOffset + stackSize <= count) { "GIF má příliš dlouhý snímek" }
            while (stackSize > 0) output[outputOffset++] = stack[--stackSize]
            if (old >= 0 && next < 4096) {
                prefix[next] = old
                suffix[next] = first.toByte()
                ++next
                if (next == (1 shl size) && size < 12) ++size
            }
            old = incoming
        }
    }

    private class Reader(private val bytes: ByteArray) {
        private var position = 0
        fun byte(): Int {
            require(position < bytes.size) { "GIF je neúplný" }
            return bytes[position++].toInt() and 255
        }
        fun short(): Int = byte() or (byte() shl 8)
        fun take(count: Int): ByteArray {
            require(count >= 0 && position.toLong() + count <= bytes.size) { "GIF je neúplný" }
            return bytes.copyOfRange(position, position + count).also { position += count }
        }
        fun palette(count: Int): IntArray = IntArray(count) { 0xff000000.toInt() or (byte() shl 16) or (byte() shl 8) or byte() }
        fun blocks(): ByteArray {
            val output = ByteArrayOutputStream()
            while (true) {
                val length = byte()
                if (length == 0) return output.toByteArray()
                output.write(take(length))
            }
        }
    }
}
