package com.batoh.core.conversion

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LzwEncoderTest {
    @Test
    fun randomPixelsRoundTripAcrossCodeWidthsAndDictionaryClears() {
        for (depth in listOf(2, 7, 8)) {
            // Incompressible input fills the 4096-entry dictionary repeatedly.
            val pixels = randomPixels(512 * 256, depth)
            assertRoundTrip(512, 256, pixels, depth)
        }
    }

    @Test
    fun everyShortPrefixRoundTripsIncludingEofAtWidthTransitions() {
        for (depth in listOf(2, 7, 8)) {
            val pixels = randomPixels(1200, depth)
            // End the stream on either side of every early dictionary transition.
            // EOF must use the decoder's width after reading the final data code.
            for (length in 1..pixels.size) {
                assertRoundTrip(length, 1, pixels.copyOf(length), depth)
            }
        }
    }

    @Test
    fun flatImagesRoundTripIncludingSinglePixelAndRepeatedStrings() {
        for (depth in listOf(2, 7, 8)) {
            for (index in listOf(0, (1 shl depth) - 1)) {
                assertRoundTrip(1, 1, byteArrayOf(index.toByte()), depth)
                assertRoundTrip(256, 256, ByteArray(256 * 256) { index.toByte() }, depth)
            }
        }
    }

    @Test
    fun repeatingPatternsRoundTrip() {
        for (depth in listOf(2, 7, 8)) {
            val colorMask = (1 shl depth) - 1
            assertRoundTrip(256, 256, ByteArray(256 * 256) { (it and colorMask).toByte() }, depth)
            assertRoundTrip(256, 256, ByteArray(256 * 256) { ((it / 256 + it % 256) and colorMask).toByte() }, depth)
        }
    }

    @Test
    fun encoderCanBeReusedWithoutKeepingPendingBits() {
        val pixels = randomPixels(777, 8)
        val encoder = LzwEncoder(pixels.size, 1, pixels, 8)
        val first = ByteArrayOutputStream().also(encoder::encode).toByteArray()
        val second = ByteArrayOutputStream().also(encoder::encode).toByteArray()
        assertArrayEquals(first, second)
        assertRoundTrip(pixels.size, 1, pixels, 8)
    }

    private fun randomPixels(count: Int, depth: Int): ByteArray {
        val random = Random(20261006L + depth)
        return ByteArray(count) { random.nextInt(1 shl depth).toByte() }
    }

    private fun assertRoundTrip(width: Int, height: Int, pixels: ByteArray, depth: Int) {
        // Build a minimal GIF around the compressed data, with a grayscale palette.
        // Decode using the JDK's independent GIF reader, without Android or our encoder.
        val gif = ByteArrayOutputStream()
        gif.write("GIF89a".toByteArray(Charsets.US_ASCII))
        gif.writeShort(width)
        gif.writeShort(height)
        gif.write(0x80 or ((depth - 1) shl 4) or (depth - 1))
        gif.write(0)
        gif.write(0)
        repeat(1 shl depth) { index -> repeat(3) { gif.write(index) } }
        gif.write(0x2c)
        gif.writeShort(0)
        gif.writeShort(0)
        gif.writeShort(width)
        gif.writeShort(height)
        gif.write(0)
        LzwEncoder(width, height, pixels, depth).encode(gif)
        gif.write(0x3b)
        // Android's test compile classpath excludes java.desktop. Reflection keeps
        // this host-JVM decoder out of the production/Android compile classpath.
        val imageIo = Class.forName("javax.imageio.ImageIO")
        val decoded = imageIo.getMethod("read", InputStream::class.java)
            .invoke(null, ByteArrayInputStream(gif.toByteArray()))
        val imageClass = decoded.javaClass
        assertEquals(width, imageClass.getMethod("getWidth").invoke(decoded))
        assertEquals(height, imageClass.getMethod("getHeight").invoke(decoded))
        val intType = Int::class.javaPrimitiveType!!
        val rgb = imageClass.getMethod(
            "getRGB", intType, intType, intType, intType, IntArray::class.java, intType, intType
        ).invoke(decoded, 0, 0, width, height, null, 0, width) as IntArray
        val actual = ByteArray(pixels.size) { index -> (rgb[index] and 0xff).toByte() }
        assertArrayEquals("depth=$depth dimensions=${width}x$height", pixels, actual)
    }

    private fun ByteArrayOutputStream.writeShort(value: Int) {
        write(value and 0xff)
        write((value ushr 8) and 0xff)
    }
}
