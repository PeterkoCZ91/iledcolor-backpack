package com.batoh.core.data.bluetooth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackpackPlaylistTest {
    private val id = byteArrayOf(0x11, 0x22, 0x33, 0x44)

    private fun expected(i: Int, cs: Int) = byteArrayOf(
        0x54, 0x03, 0x00, 0x10, 0x02, i.toByte(),
        0x11, 0x22, 0x33, 0x44, 0x00, 0x00, 0x01, 0x00,
        0x01, 0x00, 0x00, 0x00, (cs shr 8).toByte(), cs.toByte()
    )

    @Test fun itemFrameIndexZero() =
        assertArrayEquals(expected(0, 0x0115), BackpackPlaylist.itemFrame(0, 2, id, 0x100))

    @Test fun itemFrameIndexOne() =
        assertArrayEquals(expected(1, 0x0116), BackpackPlaylist.itemFrame(1, 2, id, 0x100))

    @Test fun itemFrameMatchesCmd06Header() {
        val payload = ByteArray(0x100) { it.toByte() }
        val start = BackpackPayload.startFrame(payload)
        val item = BackpackPlaylist.itemFrame(0, 2, payload)
        assertArrayEquals(start.copyOfRange(4, 12), item.copyOfRange(6, 14))
        assertEquals(true, BackpackFrame.isValid(item))
    }

    @Test fun endFrameExactBytes() =
        assertArrayEquals(byteArrayOf(0x54, 0x08, 0x00, 0x03, 0x01, 0x00, 0x60), BackpackPlaylist.endFrame())

    @Test fun rejectsBadArguments() {
        assertThrows(IllegalArgumentException::class.java) { BackpackPlaylist.itemFrame(2, 2, id, 1) }
        assertThrows(IllegalArgumentException::class.java) { BackpackPlaylist.itemFrame(0, 2, ByteArray(3), 1) }
    }
}
