package com.batoh.core.storage.repository

import com.batoh.core.conversion.GifInfo
import com.batoh.core.conversion.SafeGifDecoder
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Bounded, cancellable byte reader shared by the Gallery and ACTION_SEND import paths. */
internal object GifImportReader {
    fun read(input: InputStream?, ensureActive: () -> Unit): ByteArray {
        val source = requireNotNull(input) { "Vybraný GIF nelze načíst" }
        return source.use {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                ensureActive()
                val count = it.read(buffer)
                if (count < 0) break
                require(output.size().toLong() + count <= SafeGifDecoder.MAX_BYTES) {
                    "GIF je příliš velký (maximum 20 MB)"
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    fun validate(bytes: ByteArray, ensureActive: () -> Unit): GifInfo = try {
        SafeGifDecoder.validate(bytes, ensureActive)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("GIF je poškozený nebo překračuje podporované limity", e)
    }
}
