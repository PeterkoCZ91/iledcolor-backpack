package com.batoh.core.storage.util

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
