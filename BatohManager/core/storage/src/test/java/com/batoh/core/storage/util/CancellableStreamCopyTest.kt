package com.batoh.core.storage.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CancellableStreamCopyTest {
    @Test
    fun copiesBytesAndReportsTheCopiedLength() {
        val bytes = ByteArray(25_000) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()

        val copied = ByteArrayInputStream(bytes).copyToCancellable(output, {}, bufferSize = 1024)

        assertEquals(bytes.size.toLong(), copied)
        assertArrayEquals(bytes, output.toByteArray())
    }

    @Test
    fun propagatesCancellationBetweenReadChunks() {
        val cancellation = CancellationException("cancel copy")
        val output = ByteArrayOutputStream()
        var checks = 0

        val thrown = assertThrows(CancellationException::class.java) {
            ByteArrayInputStream(ByteArray(4096) { it.toByte() }).copyToCancellable(
                output,
                ensureActive = { if (++checks == 2) throw cancellation },
                bufferSize = 512
            )
        }

        assertEquals(cancellation, thrown)
        assertEquals(512, output.size())
    }
}
