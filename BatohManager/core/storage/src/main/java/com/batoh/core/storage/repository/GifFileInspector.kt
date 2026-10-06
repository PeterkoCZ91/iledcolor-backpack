package com.batoh.core.storage.repository

import com.batoh.core.conversion.SafeGifDecoder
import com.batoh.core.domain.model.GifFileProblem
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException

/**
 * Explains why a library tile has no preview. Uses the same bounded parser as edit/upload, so a
 * null result means editing and sending will accept the file even if the system preview decoder
 * (Coil/ImageDecoder) rejected it.
 */
internal object GifFileInspector {
    fun inspect(open: () -> InputStream?, ensureActive: () -> Unit = {}): GifFileProblem? {
        val bytes = try {
            val input = open() ?: return GifFileProblem.MISSING
            input.use { readBounded(it, ensureActive) } ?: return GifFileProblem.TOO_LARGE
        } catch (e: CancellationException) {
            throw e
        } catch (e: FileNotFoundException) {
            return GifFileProblem.MISSING
        } catch (e: SecurityException) {
            return GifFileProblem.NO_ACCESS
        } catch (e: IOException) {
            return GifFileProblem.UNREADABLE
        } catch (e: IllegalArgumentException) {
            // ContentResolver throws this for URIs no provider can resolve any more.
            return GifFileProblem.MISSING
        }
        return classify(bytes, ensureActive)
    }

    fun classify(bytes: ByteArray, ensureActive: () -> Unit = {}): GifFileProblem? {
        if (bytes.isEmpty()) return GifFileProblem.EMPTY
        if (bytes.size < 6) return GifFileProblem.NOT_GIF
        val signature = String(bytes, 0, 6, Charsets.US_ASCII)
        if (signature != "GIF87a" && signature != "GIF89a") return GifFileProblem.NOT_GIF
        return try {
            SafeGifDecoder.validate(bytes, ensureActive)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GifFileProblem.CORRUPT
        }
    }

    /** Null when the stream exceeds [SafeGifDecoder.MAX_BYTES]. */
    private fun readBounded(input: InputStream, ensureActive: () -> Unit): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            ensureActive()
            val count = input.read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size().toLong() + count > SafeGifDecoder.MAX_BYTES) return null
            output.write(buffer, 0, count)
        }
    }
}
