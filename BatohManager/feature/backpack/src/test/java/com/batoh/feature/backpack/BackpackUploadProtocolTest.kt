package com.batoh.feature.backpack

import com.batoh.core.data.bluetooth.UploadFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackpackUploadProtocolTest {
    @Test
    fun statusOneMeansChunksShouldBeSent() {
        assertEquals(UploadStartDecision.SendChunks, classifyUploadStart(response(status = 1)))
    }

    @Test
    fun statusThreeMeansTheGifIsAlreadyPresent() {
        assertEquals(UploadStartDecision.AlreadyPresent, classifyUploadStart(response(status = 3)))
    }

    @Test
    fun statusTwoMeansThereIsNotEnoughSpace() {
        assertEquals(UploadStartDecision.InsufficientSpace, classifyUploadStart(response(status = 2)))
    }

    @Test
    fun unknownStatusIsRejected() {
        assertEquals(UploadStartDecision.Rejected, classifyUploadStart(response(status = 0x7F)))
    }

    @Test
    fun shortResponseIsRejectedBeforeStatusRead() {
        assertThrows(IllegalArgumentException::class.java) {
            classifyUploadStart(byteArrayOf(0xA9.toByte(), 0x53, 0, 0, 3))
        }
    }

    @Test
    fun headerStatusMapsToTypedFailure() {
        assertNull(uploadStartFailure(response(status = 1)))
        assertNull(uploadStartFailure(response(status = 3)))
        assertEquals(UploadFailure.InsufficientSpace, uploadStartFailure(response(status = 2)))
        assertEquals(UploadFailure.HeaderRejected(0), uploadStartFailure(response(status = 0)))
        assertEquals(UploadFailure.HeaderRejected(4), uploadStartFailure(response(status = 4)))
        assertEquals(UploadFailure.HeaderRejected(0xFF), uploadStartFailure(response(status = 0xFF)))
        assertEquals(UploadFailure.HeaderTimeout, uploadStartFailure(null))
        assertEquals(UploadFailure.HeaderRejected(-1), uploadStartFailure(byteArrayOf(0x54, 0x06, 0, 3, 1)))
    }

    @Test
    fun chunkResultMapsToTypedFailure() {
        assertNull(chunkFailure(4, 10, written = true, acked = true, status = 1))
        assertEquals(UploadFailure.ChunkRejected(4, 10, 0), chunkFailure(4, 10, written = true, acked = true, status = 0))
        assertEquals(UploadFailure.ChunkRejected(4, 10, 2), chunkFailure(4, 10, written = true, acked = true, status = 2))
        assertEquals(UploadFailure.ChunkRejected(4, 10, 3), chunkFailure(4, 10, written = true, acked = true, status = 3))
        assertEquals(UploadFailure.ChunkTimeout(4, 10), chunkFailure(4, 10, written = true, acked = false, status = 1))
        assertEquals(UploadFailure.ChunkWriteFailed(4, 10), chunkFailure(4, 10, written = false, acked = false, status = 1))
    }

    @Test
    fun endEchoMapsToTypedFailure() {
        assertNull(endFailure(response(status = 1)))
        assertNull(endFailure(response(status = 3)))
        assertEquals(UploadFailure.EndRejected(0), endFailure(response(status = 0)))
        assertEquals(UploadFailure.EndRejected(2), endFailure(response(status = 2)))
        assertEquals(UploadFailure.EndRejected(-1), endFailure(byteArrayOf(0x54, 0x01, 0)))
        assertEquals(UploadFailure.EndTimeout, endFailure(null))
    }

    @Test
    fun onlyMissingAnswersCanBeReclassifiedAsConnectionLost() {
        assertTrue(UploadFailure.HeaderTimeout.isMissingAnswer())
        assertTrue(UploadFailure.ChunkTimeout(1, 2).isMissingAnswer())
        assertTrue(UploadFailure.ChunkWriteFailed(1, 2).isMissingAnswer())
        assertTrue(UploadFailure.EndTimeout.isMissingAnswer())
        assertTrue(UploadFailure.AuthFailed.isMissingAnswer())
        assertFalse(UploadFailure.InsufficientSpace.isMissingAnswer())
        assertFalse(UploadFailure.HeaderRejected(0).isMissingAnswer())
        assertFalse(UploadFailure.ChunkRejected(1, 2, 0).isMissingAnswer())
        assertFalse(UploadFailure.EndRejected(0).isMissingAnswer())
    }

    @Test
    fun everyFailureHasTitleHintAndShowsOneBasedChunkNumbers() {
        val all = listOf(
            UploadFailure.NotConnected, UploadFailure.ConnectionLost, UploadFailure.AuthFailed,
            UploadFailure.HeaderTimeout, UploadFailure.HeaderRejected(0), UploadFailure.InsufficientSpace,
            UploadFailure.ChunkWriteFailed(0, 5), UploadFailure.ChunkRejected(0, 5, 0), UploadFailure.ChunkTimeout(0, 5),
            UploadFailure.EndTimeout, UploadFailure.EndRejected(0), UploadFailure.MtuTooSmall(23),
            UploadFailure.GifTooLarge, UploadFailure.GifUnreadable, UploadFailure.NotAGif, UploadFailure.GifInvalid,
            UploadFailure.PayloadInvalid, UploadFailure.Cancelled, UploadFailure.Unknown,
        )
        val texts = all.map { it.text() }
        texts.forEach { assertTrue(it.title != 0 && it.hint != 0) }
        assertEquals("each reason has its own title", all.size, texts.map { it.title }.toSet().size)
        assertEquals(listOf<Any>(3, 9, 2), UploadFailure.ChunkRejected(2, 9, 2).text().args)
        assertEquals(listOf<Any>(1, 5), UploadFailure.ChunkTimeout(0, 5).text().args)
        assertEquals(listOf<Any>(4), UploadFailure.HeaderRejected(4).text().args)
    }

    private fun response(status: Int) = ByteArray(7).also { it[4] = status.toByte() }
}
