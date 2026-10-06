package com.batoh.feature.backpack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    private fun response(status: Int) = ByteArray(7).also { it[4] = status.toByte() }
}
