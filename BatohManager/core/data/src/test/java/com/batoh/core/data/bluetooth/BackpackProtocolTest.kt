package com.batoh.core.data.bluetooth

import java.time.LocalDateTime
import java.util.zip.CRC32C
import org.junit.Assert.*
import org.junit.Test

class BackpackProtocolTest {
    // A minimal GIF fixture with a 64×64 logical screen and a single image.
    private fun gif(width: Int = 64, height: Int = 64): ByteArray =
        "GIF89a".toByteArray(Charsets.US_ASCII) + byteArrayOf(
            width.toByte(), (width shr 8).toByte(), height.toByte(), (height shr 8).toByte(),
            0x80.toByte(), 0, 0, 0, 0, 0, -1, -1, -1,
            0x2C, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 0x44, 1, 0, 0x3B
        )

    @Test fun crcMatchesCastagnoliCheckVector() {
        assertEquals(0xE3069283.toInt(), BackpackPayload.crc32c("123456789".toByteArray()))
    }

    @Test fun payloadKeepsGifAndHashesOnlyBytesAfterOffset24() {
        val source = gif()
        val payload = BackpackPayload.fromGif(source)
        assertEquals(source.size + 46, payload.size)
        assertArrayEquals(source, payload.copyOfRange(46, payload.size))
        assertArrayEquals(byteArrayOf(1, 0, 0, 0), payload.copyOfRange(4, 8))
        assertArrayEquals(ByteArray(16), payload.copyOfRange(8, 24))
        assertArrayEquals(byteArrayOf(0, 0, 0, 0, 0, 64, 0, 64, 0, 0, 0, 6, 0, 1, 0, 100, 4, 0, 100, 0, 0, 0),
            payload.copyOfRange(24, 46))
        val reference = CRC32C().apply { update(payload, 24, payload.size - 24) }
        assertArrayEquals(BackpackFrame.int32(reference.value.toInt()), payload.copyOfRange(0, 4))
        val start = BackpackPayload.startFrame(payload)
        assertTrue(BackpackFrame.isValid(start))
        assertEquals(6, start[1].toInt())
        assertArrayEquals(payload.copyOfRange(0, 4), start.copyOfRange(4, 8))
        assertArrayEquals(BackpackFrame.int32(payload.size), start.copyOfRange(8, 12))
    }

    @Test fun invalidGifAndWrongDimensionsAreRejected() {
        for (bytes in listOf(ByteArray(13), gif(32, 64), gif(64, 128), "GIFbad".toByteArray())) {
            try {
                BackpackPayload.fromGif(bytes)
                fail("Invalid GIF accepted")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun chunksKeepEntirePayloadAndCorrectChecksumAtDifferentMtus() {
        val data = ByteArray(1200) { it.toByte() }
        for (mtu in listOf(100, 247, 512)) {
            val packets = BackpackFrame.dataChunks(data, mtu)
            val restored = packets.flatMapIndexed { index, packet ->
                assertTrue(BackpackFrame.isValid(packet))
                assertArrayEquals(BackpackFrame.int32(index), packet.copyOfRange(4, 8))
                val size = ((packet[8].toInt() and 255) shl 8) or (packet[9].toInt() and 255)
                assertEquals(packet.size - 12, size)
                assertTrue(size <= mtu - 25)
                packet.copyOfRange(10, packet.size - 2).toList()
            }.toByteArray()
            assertArrayEquals(data, restored)
        }
        assertArrayEquals(byteArrayOf(0x54, 1, 0, 3, 1, 0, 0x59), BackpackFrame.end())
    }

    @Test fun settingsEncodeManufacturerValues() {
        assertEquals(10, BackpackCommands.brightness(1)[4].toInt())
        assertEquals(1, BackpackCommands.brightness(10)[4].toInt())
        assertEquals(0, BackpackCommands.screen(false)[4].toInt())
        assertEquals(1, BackpackCommands.screen(true)[4].toInt())
        assertEquals(0x14, BackpackCommands.rotate(3, true)[4].toInt())
        assertArrayEquals(byteArrayOf(26, 10, 6, 15, 4, 3, 0),
            BackpackCommands.setTime(LocalDateTime.of(2026, 10, 6, 15, 4, 3)).copyOfRange(4, 11))
        assertEquals(2, BackpackCommands.clearPrograms()[1].toInt())
        assertEquals(16, BackpackCommands.queryState()[1].toInt())
    }

    @Test fun capturedAcknowledgementsAcceptFirmwareLengthQuirk() {
        val chunkAck = byteArrayOf(0x54, 0, 0, 5, 0, 0, 0, 13, 1, 0, 0x67)
        assertFalse(BackpackFrame.isValid(chunkAck))
        assertTrue(BackpackFrame.isValidNotification(chunkAck))
        assertFalse(BackpackFrame.isValidNotification(chunkAck.copyOf().apply { this[8] = 0 }))
        val setupAck = byteArrayOf(0x54, 7, 0, 3, 0, 0, 0x5E)
        assertTrue(BackpackFrame.isValidNotification(setupAck))
        val startAck = byteArrayOf(0x54, 6, 0, 3, 1, 0, 0x5E)
        assertTrue(BackpackFrame.isValidNotification(startAck))
    }

    @Test fun stateParsesAndRejectsTruncatedOrCorruptResponses() {
        val frame = BackpackFrame.build(0x10, byteArrayOf(0, 0, 1, 4, 0x12))
        assertEquals(BackpackCommands.State(true, 7, 2, true), BackpackCommands.parseState(frame))
        for (size in 0 until frame.size) assertNull(BackpackCommands.parseState(frame.copyOf(size)))
        assertNull(BackpackCommands.parseState(frame.copyOf().apply { this[7] = 5 }))
        assertNull(BackpackCommands.parseState(BackpackFrame.build(0x10, byteArrayOf(0, 0, 1, 0, 0))))
        assertNull(BackpackCommands.parseState(BackpackFrame.build(0x09, byteArrayOf(0, 0, 1, 4, 0))))
    }
}
