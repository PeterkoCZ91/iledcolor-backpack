package com.batoh.feature.backpack

import com.batoh.core.data.bluetooth.UploadFailure
import com.batoh.core.data.bluetooth.ipixel.IPixelProtocol
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class IPixelUploaderTest {
    private fun ack(code: Int) = byteArrayOf(0x05, 0x00, 0x03, 0x00, code.toByte())

    /** Fake panel: every write is recorded and answered with the next queued notification (if any). */
    private class FakePanel(replies: List<ByteArray>, private val writeOk: Boolean = true) {
        val acks = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
        val writes = mutableListOf<ByteArray>()
        private val queue = ArrayDeque(replies)
        val write: suspend (ByteArray) -> Boolean = { data ->
            writes += data
            if (writeOk) queue.removeFirstOrNull()?.let { acks.tryEmit(it) }
            writeOk
        }
    }

    private fun failureOf(block: suspend () -> Unit): UploadFailure {
        try {
            runBlocking { block() }
        } catch (e: UploadFailureException) {
            return e.failure
        }
        fail("Upload did not fail")
        error("unreachable")
    }

    @Test
    fun upload_singleWindowDone() = runBlocking {
        val file = ByteArray(1000) { it.toByte() }
        val panel = FakePanel(listOf(ack(3)))
        val progress = mutableListOf<Float>()
        IPixelUploader(panel.write, panel.acks).upload(file, isGif = true) { progress += it }
        assertEquals(1f, progress.last())
        assertArrayEquals(IPixelProtocol.uploadMessages(file, true).reduce(ByteArray::plus), panel.writes.reduce(ByteArray::plus))
    }

    @Test
    fun upload_multiWindow() = runBlocking {
        val file = ByteArray(2 * IPixelProtocol.WINDOW_BYTES + 500) { (it * 7).toByte() }
        val panel = FakePanel(listOf(ack(1), ack(1), ack(3)))
        val progress = mutableListOf<Float>()
        IPixelUploader(panel.write, panel.acks).upload(file, isGif = true) { progress += it }
        val messages = IPixelProtocol.uploadMessages(file, true)
        assertEquals(3, messages.size)
        assertEquals(3, panel.writes.size)
        messages.forEachIndexed { i, m -> assertArrayEquals(m, panel.writes[i]) }
        assertEquals(1f, progress.last())
        assertEquals(progress.sorted(), progress)
    }

    @Test
    fun upload_timeoutIsChunkTimeout() {
        val panel = FakePanel(emptyList())
        val failure = failureOf { IPixelUploader(panel.write, panel.acks, ackTimeoutMs = 50).upload(ByteArray(100), true) {} }
        assertEquals(UploadFailure.ChunkTimeout(0, 1), failure)
    }

    @Test
    fun upload_rejectedCode() {
        val panel = FakePanel(listOf(ack(5)))
        val failure = failureOf { IPixelUploader(panel.write, panel.acks).upload(ByteArray(100), true) {} }
        assertEquals(UploadFailure.ChunkRejected(0, 1, 5), failure)
    }

    @Test
    fun upload_writeFailure() {
        val panel = FakePanel(listOf(ack(3)), writeOk = false)
        val failure = failureOf { IPixelUploader(panel.write, panel.acks).upload(ByteArray(100), true) {} }
        assertEquals(UploadFailure.ChunkWriteFailed(0, 1), failure)
    }

    @Test
    fun upload_lastWindowAcceptedWithoutDoneIsEndTimeout() {
        val panel = FakePanel(listOf(ack(1)))
        val failure = failureOf { IPixelUploader(panel.write, panel.acks, ackTimeoutMs = 50).upload(ByteArray(100), true) {} }
        assertEquals(UploadFailure.EndTimeout, failure)
    }

    @Test
    fun upload_ignoresUnrelatedNotifications() = runBlocking {
        val panel = FakePanel(listOf(byteArrayOf(0x0A, 0x00, 0x01, 0x80.toByte(), 0x81.toByte())))
        val uploader = IPixelUploader({ data -> panel.write(data).also { panel.acks.tryEmit(ack(3)) } }, panel.acks)
        uploader.upload(ByteArray(100), true) {}
        assertEquals(1, panel.writes.size)
    }
}
