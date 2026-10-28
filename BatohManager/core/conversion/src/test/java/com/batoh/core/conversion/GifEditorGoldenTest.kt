package com.batoh.core.conversion

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Pins the default 64x64 output of [GifEditorProcessor.transform] byte for byte (the backpack depends on it). */
class GifEditorGoldenTest {
    private val gradient = gradientGif(96, 72)

    @Test
    fun transform_defaultIsByteIdentical() {
        check("default_crop.gif", GifEditorProcessor.transform(gradient).gifBytes)
    }

    @Test
    fun transform_defaultOptionsFitRotatedMirroredIsByteIdentical() {
        val options = GifEditOptions(rotationDegrees = 90, mirrorHorizontal = true, scaleMode = GifScaleMode.Fit)
        check("fit_rot90_mirror.gif", GifEditorProcessor.transform(gradient, options).gifBytes)
    }

    private fun check(name: String, actual: ByteArray) {
        val stream = javaClass.getResourceAsStream("/golden/$name")
        assertNotNull("missing golden $name", stream)
        assertArrayEquals(name, stream!!.readBytes(), actual)
    }

    /** Two frames, 256-colour palette, so both the exact palette and NeuQuant paths are exercised. */
    private fun gradientGif(width: Int, height: Int): ByteArray {
        val gif = ByteArrayOutputStream()
        gif.write("GIF89a".toByteArray())
        gif.short(width); gif.short(height)
        gif.write(0xf7); gif.write(0); gif.write(0)
        for (c in 0 until 256) { gif.write(c); gif.write((c * 7) and 255); gif.write(255 - c) }
        gif.write(byteArrayOf(0x21, 0xff.toByte(), 11)); gif.write("NETSCAPE2.0".toByteArray())
        gif.write(3); gif.write(1); gif.short(2); gif.write(0)
        for (frame in 0 until 2) {
            gif.write(byteArrayOf(0x21, 0xf9.toByte(), 4, 4)); gif.short(9 + frame); gif.write(0); gif.write(0)
            gif.write(0x2c); gif.short(0); gif.short(0); gif.short(width); gif.short(height); gif.write(0)
            val pixels = ByteArray(width * height) { ((it % width) * 2 + (it / width) * 3 + frame * 40).toByte() }
            LzwEncoder(width, height, pixels, 8).encode(gif)
        }
        gif.write(0x3b)
        return gif.toByteArray()
    }

    private fun ByteArrayOutputStream.short(value: Int) { write(value and 255); write((value ushr 8) and 255) }
}
