package com.batoh.core.storage.repository

import com.batoh.core.conversion.SafeGifDecoder
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.concurrent.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GifImportReaderTest {
    @Test
    fun readsAndValidatesSmallAnimatedGif() {
        val gif = Base64.getDecoder().decode(
            "R0lGODlhQABAAIEAAP8AAAAAAAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQAFAAAACwAAAAAQABAAAAIaQABCBxIsKDBgwgTKlzIsKHDhxAjSpxIsaLFixgzatzIsaPHjyBDihxJsqTJkyhTqlzJsqXLlzBjypxJs6bNmzhz6tzJs6fPn0CDCh1KtKjRo0iTKl3KtKnTp1CjSp1KtarVq1izagUQEAAh+QQBFAABACwAAAAAQABAAIEAAP8AAAAAAAAAAAAIaQABCBxIsKDBgwgTKlzIsKHDhxAjSpxIsaLFixgzatzIsaPHjyBDihxJsqTJkyhTqlzJsqXLlzBjypxJs6bNmzhz6tzJs6fPn0CDCh1KtKjRo0iTKl3KtKnTp1CjSp1KtarVq1izagUQEAA7"
        )

        val bytes = GifImportReader.read(ByteArrayInputStream(gif)) {}
        val info = GifImportReader.validate(bytes) {}

        assertArrayEquals(gif, bytes)
        assertEquals(64, info.width)
        assertEquals(64, info.height)
        assertEquals(2, info.frameCount)
    }

    @Test
    fun rejectsMissingInputStream() {
        assertThrows(IllegalArgumentException::class.java) {
            GifImportReader.read(null) {}
        }
    }

    @Test
    fun rejectsInputLargerThanTheImportLimit() {
        val oversized = ByteArray(SafeGifDecoder.MAX_BYTES + 1)

        assertThrows(IllegalArgumentException::class.java) {
            GifImportReader.read(ByteArrayInputStream(oversized)) {}
        }
    }

    @Test
    fun propagatesCancellationWhileReading() {
        val cancellation = CancellationException("test cancellation")

        val thrown = assertThrows(CancellationException::class.java) {
            GifImportReader.read(ByteArrayInputStream(ByteArray(16_384))) { throw cancellation }
        }

        assertEquals(cancellation, thrown)
    }

    @Test
    fun rejectsMalformedGifBeforeItCanBeCopied() {
        assertThrows(IllegalArgumentException::class.java) {
            GifImportReader.validate("not a gif".toByteArray()) {}
        }
    }
}
