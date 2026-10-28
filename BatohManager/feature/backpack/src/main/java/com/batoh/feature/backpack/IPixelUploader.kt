package com.batoh.feature.backpack

import com.batoh.core.data.bluetooth.UploadFailure
import com.batoh.core.data.bluetooth.ipixel.IPixelProtocol
import com.batoh.core.data.bluetooth.ipixel.IPixelProtocol.UploadAck
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sends one file to slot 0 of an iPixel panel: one [IPixelProtocol.uploadMessages] window at a time,
 * each answered by a `05 00 kk 00 cc` notification on [acks]. Failures are [UploadFailureException]s
 * whose index/total count windows.
 */
internal class IPixelUploader(
    private val write: suspend (ByteArray) -> Boolean,
    private val acks: Flow<ByteArray>,
    private val ackTimeoutMs: Long = 10_000
) {
    suspend fun upload(file: ByteArray, isGif: Boolean, onProgress: (Float) -> Unit) = coroutineScope {
        val windows = IPixelProtocol.uploadMessages(file, isGif)
        val total = windows.size
        // Listening starts before the first write, so an ACK that beats the write's return is kept.
        val received = Channel<UploadAck>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            acks.collect { n -> IPixelProtocol.parseUploadAck(n)?.let { received.send(it) } }
        }
        try {
            var done = false
            for ((index, window) in windows.withIndex()) {
                if (!write(window)) throw UploadFailureException(UploadFailure.ChunkWriteFailed(index, total))
                when (val ack = withTimeoutOrNull(ackTimeoutMs) { received.receive() }) {
                    null -> throw UploadFailureException(UploadFailure.ChunkTimeout(index, total))
                    is UploadAck.Rejected -> throw UploadFailureException(UploadFailure.ChunkRejected(index, total, ack.code))
                    UploadAck.Done -> done = true
                    UploadAck.WindowAccepted -> onProgress((index + 1).toFloat() / total)
                }
                if (done) break
            }
            // The last window was only accepted: the panel still has to confirm the stored file.
            while (!done) {
                when (val ack = withTimeoutOrNull(ackTimeoutMs) { received.receive() }) {
                    null -> throw UploadFailureException(UploadFailure.EndTimeout)
                    is UploadAck.Rejected -> throw UploadFailureException(UploadFailure.ChunkRejected(total - 1, total, ack.code))
                    UploadAck.Done -> done = true
                    UploadAck.WindowAccepted -> Unit
                }
            }
            onProgress(1f)
        } finally {
            collector.cancel()
        }
    }
}
