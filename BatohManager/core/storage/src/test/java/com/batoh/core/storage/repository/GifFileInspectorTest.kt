package com.batoh.core.storage.repository

import com.batoh.core.conversion.SafeGifDecoder
import com.batoh.core.domain.model.GifFileProblem
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.Base64
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class GifFileInspectorTest {
    private val validGif = Base64.getDecoder().decode(
        "R0lGODlhQABAAIEAAP8AAAAAAAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQAFAAAACwAAAAAQABAAAAIaQABCBxIsKDBgwgTKlzIsKHDhxAjSpxIsaLFixgzatzIsaPHjyBDihxJsqTJkyhTqlzJsqXLlzBjypxJs6bNmzhz6tzJs6fPn0CDCh1KtKjRo0iTKl3KtKnTp1CjSp1KtarVq1izagUQEAAh+QQBFAABACwAAAAAQABAAIEAAP8AAAAAAAAAAAAIaQABCBxIsKDBgwgTKlzIsKHDhxAjSpxIsaLFixgzatzIsaPHjyBDihxJsqTJkyhTqlzJsqXLlzBjypxJs6bNmzhz6tzJs6fPn0CDCh1KtKjRo0iTKl3KtKnTp1CjSp1KtarVq1izagUQEAA7"
    )

    private fun inspect(bytes: ByteArray) = GifFileInspector.inspect({ ByteArrayInputStream(bytes) })

    @Test
    fun validGifHasNoProblem() = assertNull(inspect(validGif))

    @Test
    fun missingFileAndNullStreamAreReportedAsMissing() {
        assertEquals(GifFileProblem.MISSING, GifFileInspector.inspect({ throw FileNotFoundException("gone") }))
        assertEquals(GifFileProblem.MISSING, GifFileInspector.inspect({ null }))
    }

    @Test
    fun revokedAccessIsReported() =
        assertEquals(GifFileProblem.NO_ACCESS, GifFileInspector.inspect({ throw SecurityException("revoked") }))

    @Test
    fun ioErrorWhileReadingIsReported() {
        val failing = object : InputStream() { override fun read(): Int = throw IOException("eio") }
        assertEquals(GifFileProblem.UNREADABLE, GifFileInspector.inspect({ failing }))
    }

    @Test
    fun emptyFileIsReported() = assertEquals(GifFileProblem.EMPTY, inspect(ByteArray(0)))

    @Test
    fun videoSavedAsGifIsNotGif() {
        val mp4Header = byteArrayOf(0, 0, 0, 0x18) + "ftypmp42".toByteArray(Charsets.US_ASCII) + ByteArray(32)
        assertEquals(GifFileProblem.NOT_GIF, inspect(mp4Header))
        assertEquals(GifFileProblem.NOT_GIF, inspect(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun truncatedGifIsCorrupt() =
        assertEquals(GifFileProblem.CORRUPT, inspect(validGif.copyOf(validGif.size / 2)))

    @Test
    fun oversizedFileIsTooLarge() =
        assertEquals(GifFileProblem.TOO_LARGE, inspect(ByteArray(SafeGifDecoder.MAX_BYTES + 1)))

    @Test
    fun cancellationIsNotSwallowed() {
        assertThrows(CancellationException::class.java) {
            GifFileInspector.inspect({ ByteArrayInputStream(validGif) }) { throw CancellationException("stop") }
        }
    }
}
