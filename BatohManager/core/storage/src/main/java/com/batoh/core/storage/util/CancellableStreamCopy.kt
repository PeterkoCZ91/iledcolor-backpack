package com.batoh.core.storage.util

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal fun InputStream.copyToCancellable(
    output: OutputStream,
    ensureActive: () -> Unit,
    bufferSize: Int = DEFAULT_BUFFER_SIZE
): Long {
    require(bufferSize > 0) { "Buffer size must be positive" }
    val buffer = ByteArray(bufferSize)
    var copied = 0L
    while (true) {
        ensureActive()
        val count = read(buffer)
        if (count < 0) return copied
        if (count > 0) {
            output.write(buffer, 0, count)
            copied += count
        }
    }
}

/**
 * Minimal view of a MediaStore-like destination (insert pending row, open it, publish, delete),
 * so the "never leave a partial item behind" contract can be tested on the JVM.
 */
internal interface PendingItemSink<H : Any> {
    fun insert(): H?
    fun openOutput(item: H): OutputStream?
    fun publish(item: H)
    fun delete(item: H)
}

/**
 * Copies this stream into a freshly inserted pending item and publishes it. On any failure,
 * including cancellation and a failing source mid-copy, the inserted item is deleted and the
 * original error is rethrown (cleanup errors are attached as suppressed).
 */
internal fun <H : Any> InputStream.writeToPendingItem(
    sink: PendingItemSink<H>,
    ensureActive: () -> Unit,
    bufferSize: Int = DEFAULT_BUFFER_SIZE
): H {
    ensureActive()
    val item = sink.insert() ?: throw IOException("Destination item could not be created")
    try {
        val output = sink.openOutput(item) ?: throw IOException("Destination output stream could not be opened")
        output.use { out -> use { input -> input.copyToCancellable(out, ensureActive, bufferSize) } }
        ensureActive()
        sink.publish(item)
        return item
    } catch (error: Throwable) {
        try {
            sink.delete(item)
        } catch (cleanupError: Throwable) {
            error.addSuppressed(cleanupError)
        }
        throw error
    }
}
