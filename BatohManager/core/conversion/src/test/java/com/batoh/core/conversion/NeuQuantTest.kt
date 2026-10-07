package com.batoh.core.conversion

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeuQuantTest {
    private fun randomRgb(pixels: Int, seed: Long): ByteArray =
        ByteArray(pixels * 3).also { Random(seed).nextBytes(it) }

    private fun rgbOf(palette: ByteArray, index: Int) = IntArray(3) { palette[index * 3 + it].toInt() and 0xff }

    @Test fun paletteHasRequestedNumberOfEntries() {
        for (size in listOf(64, 128, 256)) {
            val data = randomRgb(4096, 1)
            val palette = NeuQuant(data, data.size, 10, size).process()
            assertEquals(size * 3, palette.size)
        }
    }

    @Test fun mapAlwaysReturnsIndexInsidePalette() {
        val data = randomRgb(4096, 2)
        val nq = NeuQuant(data, data.size, 10, 128)
        nq.process()
        val rnd = Random(3)
        repeat(2000) {
            val index = nq.map(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
            assertTrue("index $index", index in 0 until 128)
        }
        // extremes
        for (c in listOf(0, 255)) assertTrue(nq.map(c, c, c) in 0 until 128)
    }

    @Test fun twoColourImageIsReproducedClosely() {
        val data = ByteArray(2048 * 3)
        for (i in 0 until 2048) {
            val red = i % 2 == 0
            data[i * 3] = if (red) 255.toByte() else 0
            data[i * 3 + 2] = if (red) 0 else 255.toByte()
        }
        val nq = NeuQuant(data, data.size, 1, 128)
        val palette = nq.process()
        val red = rgbOf(palette, nq.map(255, 0, 0))
        val blue = rgbOf(palette, nq.map(0, 0, 255))
        assertTrue("red=${red.toList()}", red[0] > 200 && red[1] < 50 && red[2] < 50)
        assertTrue("blue=${blue.toList()}", blue[2] > 200 && blue[0] < 50 && blue[1] < 50)
    }

    @Test fun processingIsDeterministic() {
        val data = randomRgb(3000, 4)
        val a = NeuQuant(data.copyOf(), data.size, 5, 128).process()
        val b = NeuQuant(data.copyOf(), data.size, 5, 128).process()
        assertTrue(a.contentEquals(b))
    }

    @Test fun tinyInputBelowMinimumStillProducesPalette() {
        val data = randomRgb(10, 5)
        val palette = NeuQuant(data, data.size, 10, 128).process()
        assertEquals(128 * 3, palette.size)
    }
}
