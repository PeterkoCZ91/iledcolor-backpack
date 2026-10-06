package com.batoh.core.conversion

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Random
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Node

class GifEditorProcessorTest {
    private val black = 0xff000000.toInt()
    private val red = 0xffff0000.toInt()
    private val green = 0xff00ff00.toInt()
    private val blue = 0xff0000ff.toInt()

    @Test
    fun safeLzwDecodeCrossesWidthsAndClearsForTwoSevenAndEightBitPalettes() {
        for (depth in listOf(2, 7, 8)) {
            val random = Random(depth.toLong())
            val pixels = ByteArray(512 * 128) { random.nextInt(1 shl depth).toByte() }
            val gif = ByteArrayOutputStream()
            gif.write("GIF89a".toByteArray())
            gif.short(512); gif.short(128)
            gif.write(0x80 or (depth - 1)); gif.write(0); gif.write(0)
            repeat(1 shl depth) { color -> repeat(3) { gif.write(color) } }
            gif.write(0x2c); gif.short(0); gif.short(0); gif.short(512); gif.short(128); gif.write(0)
            LzwEncoder(512, 128, pixels, depth).encode(gif)
            gif.write(0x3b)
            var frames = 0
            SafeGifDecoder.decode(gif.toByteArray(), {}) { canvas, _, _, _ ->
                ++frames
                assertArrayEquals("depth=$depth", pixels, ByteArray(canvas.size) { (canvas[it] and 255).toByte() })
            }
            assertEquals(1, frames)
        }
    }

    @Test
    fun rotationsAndMirrorsProduceActualExpectedPixelsInIndependentDecoder() {
        val source = fixture(64, 64, listOf(Frame(ByteArray(4096) { i ->
            when { i / 64 < 32 && i % 64 < 32 -> 1; i / 64 < 32 -> 2; i % 64 < 32 -> 3; else -> 0 }.toByte()
        })))
        fun corners(options: GifEditOptions): List<Int> {
            val output = GifEditorProcessor.transform(source, options)
            val pixels = readFrames(output.gifBytes).single().pixels
            return listOf(pixels[16 * 64 + 16], pixels[16 * 64 + 48], pixels[48 * 64 + 16], pixels[48 * 64 + 48])
        }
        assertEquals(listOf(red, green, blue, black), corners(GifEditOptions()))
        assertEquals(listOf(blue, red, black, green), corners(GifEditOptions(rotationDegrees = 90)))
        assertEquals(listOf(black, blue, green, red), corners(GifEditOptions(rotationDegrees = 180)))
        assertEquals(listOf(green, black, red, blue), corners(GifEditOptions(rotationDegrees = 270)))
        assertEquals(listOf(green, red, black, blue), corners(GifEditOptions(mirrorHorizontal = true)))
        assertEquals(listOf(blue, black, red, green), corners(GifEditOptions(mirrorVertical = true)))
        assertEquals(listOf(red, blue, green, black), corners(GifEditOptions(rotationDegrees = 90, mirrorHorizontal = true)))
    }

    @Test
    fun cropAndFitDifferAndFitUsesBlackBorders() {
        val source = fixture(128, 64, listOf(Frame(ByteArray(128 * 64) { i ->
            when (i % 128) { in 0..31 -> 1; in 32..95 -> 2; else -> 3 }.toByte()
        })))
        val cropped = readFrames(GifEditorProcessor.transform(source).gifBytes).single().pixels
        assertTrue(cropped.all { it == green })
        val fitted = readFrames(GifEditorProcessor.transform(source, GifEditOptions(scaleMode = GifScaleMode.Fit)).gifBytes).single().pixels
        for (y in 0 until 64) for (x in 0 until 64) {
            val expected = if (y < 16 || y >= 48) black else when (x) { in 0..15 -> red; in 16..47 -> green; else -> blue }
            assertEquals("x=$x y=$y", expected, fitted[y * 64 + x])
        }
    }

    @Test
    fun animationDelaysAndLoopArePreserved() {
        val source = fixture(64, 64, listOf(Frame(ByteArray(4096) { 1 }, delay = 7), Frame(ByteArray(4096) { 2 }, delay = 23)), loops = 3)
        val output = GifEditorProcessor.transform(source, GifEditOptions(rotationDegrees = 180))
        val frames = readFrames(output.gifBytes)
        assertEquals(listOf(7, 23), frames.map { it.delay })
        assertEquals(3, frames.first().loops)
        assertTrue(frames[0].pixels.all { it == red })
        assertTrue(frames[1].pixels.all { it == green })
        val info = SafeGifDecoder.validate(output.gifBytes)
        assertEquals(3, info.loopCount)
        assertEquals(300L, info.durationMs)
        assertEquals(2, info.frameCount)
        assertEquals(null, SafeGifDecoder.validate(GifEditorProcessor.transform(fixture(64, 64, listOf(Frame(ByteArray(4096))))).gifBytes).loopCount)
    }

    @Test
    fun transparencyPartialFramesAndDisposalAreComposedBeforeTransform() {
        val source = fixture(64, 64, listOf(
            Frame(ByteArray(4096) { 1 }),
            Frame(ByteArray(32 * 32) { 2 }, width = 32, height = 32, disposal = 3),
            Frame(ByteArray(32 * 32) { 3 }, left = 32, top = 32, width = 32, height = 32, disposal = 2),
            Frame(byteArrayOf(0), width = 1, height = 1, transparent = 0)
        ))
        val frames = readFrames(GifEditorProcessor.transform(source).gifBytes)
        assertEquals(green, frames[1].pixels[0])
        assertEquals(red, frames[1].pixels[63])
        assertEquals(red, frames[2].pixels[0]) // restore previous after frame 1
        assertEquals(blue, frames[2].pixels[63 * 64 + 63])
        assertEquals(red, frames[3].pixels[0]) // transparent pixel preserves old canvas
        assertEquals(black, frames[3].pixels[63 * 64 + 63]) // disposal 2 restores background
    }

    @Test
    fun malformedAndOversizedInputsAreRejectedWithoutNativeDecoding() {
        val source = fixture(64, 64, listOf(Frame(ByteArray(4096) { 1 })))
        assertThrows(IllegalArgumentException::class.java) { SafeGifDecoder.validate(source.copyOf(12)) }
        assertThrows(IllegalArgumentException::class.java) { SafeGifDecoder.validate(source.copyOf(source.size - 1)) }
        assertThrows(IllegalArgumentException::class.java) { SafeGifDecoder.validate(source.copyOf().also { it[6] = 0; it[7] = 0 }) }
        assertThrows(IllegalArgumentException::class.java) { SafeGifDecoder.validate(ByteArray(SafeGifDecoder.MAX_BYTES + 1)) }
        assertThrows(IllegalArgumentException::class.java) {
            SafeGifDecoder.validate(source.copyOf().also { it[6] = 0; it[7] = 16; it[8] = 0; it[9] = 8 })
        }
        assertThrows(IllegalArgumentException::class.java) {
            SafeGifDecoder.validate(fixture(64, 64, List(601) { Frame(ByteArray(4096)) }))
        }
        // Replace compressed image data with impossible dictionary codes.
        val broken = source.copyOf()
        val descriptor = 13 + 12 + 8 // header + palette + GCE
        broken[descriptor + 12] = 0xff.toByte()
        assertThrows(IllegalArgumentException::class.java) { SafeGifDecoder.validate(broken) }
    }

    @Test
    fun cancellationStopsDecodeAndTransform() {
        val source = fixture(64, 64, listOf(Frame(ByteArray(4096) { 1 })))
        assertThrows(InterruptedException::class.java) {
            GifEditorProcessor.transform(source) { throw InterruptedException("cancel") }
        }
    }

    private data class Frame(val pixels: ByteArray, val delay: Int = 10, val left: Int = 0, val top: Int = 0,
        val width: Int = 64, val height: Int = 64, val disposal: Int = 1, val transparent: Int = -1)

    private fun fixture(width: Int, height: Int, frames: List<Frame>, loops: Int? = null): ByteArray {
        val gif = ByteArrayOutputStream()
        gif.write("GIF89a".toByteArray())
        gif.short(width); gif.short(height)
        gif.write(0x81); gif.write(0); gif.write(0)
        gif.write(byteArrayOf(0, 0, 0, 255.toByte(), 0, 0, 0, 255.toByte(), 0, 0, 0, 255.toByte()))
        loops?.let {
            gif.write(byteArrayOf(0x21, 0xff.toByte(), 11)); gif.write("NETSCAPE2.0".toByteArray())
            gif.write(3); gif.write(1); gif.short(it); gif.write(0)
        }
        frames.forEach { frame ->
            gif.write(byteArrayOf(0x21, 0xf9.toByte(), 4))
            gif.write((frame.disposal shl 2) or if (frame.transparent >= 0) 1 else 0)
            gif.short(frame.delay); gif.write(frame.transparent.coerceAtLeast(0)); gif.write(0)
            gif.write(0x2c); gif.short(frame.left); gif.short(frame.top)
            val frameWidth = if (frames.size == 1 && frame.pixels.size == width * height) width else frame.width
            val frameHeight = if (frames.size == 1 && frame.pixels.size == width * height) height else frame.height
            gif.short(frameWidth); gif.short(frameHeight); gif.write(0)
            LzwEncoder(frameWidth, frameHeight, frame.pixels, 2).encode(gif)
        }
        gif.write(0x3b)
        return gif.toByteArray()
    }

    private data class DecodedFrame(val pixels: IntArray, val delay: Int, val loops: Int?)

    /** JDK reader accessed reflectively because Android's compiler hides java.desktop. */
    private fun readFrames(bytes: ByteArray): List<DecodedFrame> {
        val io = Class.forName("javax.imageio.ImageIO")
        val input = io.getMethod("createImageInputStream", Any::class.java).invoke(null, ByteArrayInputStream(bytes))
        val readers = io.getMethod("getImageReadersByFormatName", String::class.java).invoke(null, "gif") as Iterator<*>
        val reader = readers.next()!!
        val readerClass = Class.forName("javax.imageio.ImageReader")
        readerClass.getMethod("setInput", Any::class.java).invoke(reader, input)
        try {
            val count = readerClass.getMethod("getNumImages", Boolean::class.javaPrimitiveType).invoke(reader, true) as Int
            return (0 until count).map { index ->
                val image = readerClass.getMethod("read", Int::class.javaPrimitiveType).invoke(reader, index)
                val imageClass = image.javaClass
                val intType = Int::class.javaPrimitiveType!!
                assertEquals(64, imageClass.getMethod("getWidth").invoke(image))
                assertEquals(64, imageClass.getMethod("getHeight").invoke(image))
                val pixels = imageClass.getMethod("getRGB", intType, intType, intType, intType, IntArray::class.java, intType, intType)
                    .invoke(image, 0, 0, 64, 64, null, 0, 64) as IntArray
                val metadata = readerClass.getMethod("getImageMetadata", intType).invoke(reader, index)
                val tree = Class.forName("javax.imageio.metadata.IIOMetadata").getMethod("getAsTree", String::class.java)
                    .invoke(metadata, "javax_imageio_gif_image_1.0") as Node
                var child = tree.firstChild
                var delay = -1
                var loops: Int? = null
                while (child != null) {
                    if (child.nodeName == "GraphicControlExtension") delay = child.attributes.getNamedItem("delayTime").nodeValue.toInt()
                    if (child.nodeName == "ApplicationExtensions") {
                        var app = child.firstChild
                        while (app != null) {
                            if (app.attributes?.getNamedItem("applicationID")?.nodeValue == "NETSCAPE") {
                                val data = Class.forName("javax.imageio.metadata.IIOMetadataNode").getMethod("getUserObject").invoke(app) as ByteArray
                                loops = (data[1].toInt() and 255) or ((data[2].toInt() and 255) shl 8)
                            }
                            app = app.nextSibling
                        }
                    }
                    child = child.nextSibling
                }
                DecodedFrame(pixels, delay, loops)
            }
        } finally {
            readerClass.getMethod("dispose").invoke(reader)
            Class.forName("javax.imageio.stream.ImageInputStream").getMethod("close").invoke(input)
        }
    }

    private fun ByteArrayOutputStream.short(value: Int) { write(value and 255); write((value ushr 8) and 255) }
}
