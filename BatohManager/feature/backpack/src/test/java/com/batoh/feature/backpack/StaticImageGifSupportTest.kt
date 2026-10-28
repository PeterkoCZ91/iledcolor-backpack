package com.batoh.feature.backpack

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Format gate of [StaticImageGif]; decoding and encoding need Android and are covered on a device. */
class StaticImageGifSupportTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    private val pngSignature = intArrayOf(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)

    @Test fun jpegMagicIsSupported() {
        assertTrue(StaticImageGif.isSupported(bytes(0xff, 0xd8, 0xff, 0xe0, 0, 0x10, 0x4a, 0x46)))
    }

    @Test fun pngSignatureIsSupported() {
        assertTrue(StaticImageGif.isSupported(bytes(*pngSignature)))
    }

    @Test fun inputShorterThanEightBytesIsRejectedEvenWithValidMagic() {
        assertFalse(StaticImageGif.isSupported(bytes(0xff, 0xd8, 0xff, 0xe0, 0, 0x10, 0x4a)))
        assertFalse(StaticImageGif.isSupported(bytes(*pngSignature.copyOf(7))))
        assertFalse(StaticImageGif.isSupported(ByteArray(0)))
    }

    @Test fun gifVideoAndTextAreRejected() {
        assertFalse(StaticImageGif.isSupported("GIF89a\u0001\u0000\u0001\u0000".toByteArray(Charsets.ISO_8859_1)))
        assertFalse(StaticImageGif.isSupported(bytes(0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70)))
        assertFalse(StaticImageGif.isSupported("not an image".toByteArray()))
    }

    @Test fun truncatedJpegMagicIsRejected() {
        // Third byte must be 0xff as well.
        assertFalse(StaticImageGif.isSupported(bytes(0xff, 0xd8, 0x00, 0xe0, 0, 0x10, 0x4a, 0x46)))
        assertFalse(StaticImageGif.isSupported(bytes(0xff, 0xd9, 0xff, 0xe0, 0, 0x10, 0x4a, 0x46)))
    }

    @Test fun pngWithAnyWrongSignatureByteIsRejected() {
        for (i in pngSignature.indices) {
            val broken = pngSignature.copyOf().also { it[i] = it[i] xor 0x01 }
            assertFalse("byte $i", StaticImageGif.isSupported(bytes(*broken)))
        }
    }
}
