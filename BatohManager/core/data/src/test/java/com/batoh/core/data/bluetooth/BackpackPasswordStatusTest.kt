package com.batoh.core.data.bluetooth

import org.junit.Assert.*
import org.junit.Test

class BackpackPasswordStatusTest {
    private fun reply(r: Int) = BackpackFrame.build(0x0F, byteArrayOf(r.toByte()))

    @Test fun queryIsSixZeroPayload() {
        val f = BackpackPasswordStatus.queryFrame()
        assertArrayEquals(
            byteArrayOf(0x54, 0x0F, 0x00, 0x08, 0, 0, 0, 0, 0, 0, 0x00, 0x6B), f)
    }

    @Test fun decodesOk() {
        val d = BackpackPasswordStatus.decode(reply(1))!!
        assertEquals(BackpackPasswordStatus.Verdict.OK, d.verdict)
    }

    @Test fun decodesNotSet() {
        val d = BackpackPasswordStatus.decode(reply(3))!!
        assertEquals(3, d.raw)
        assertEquals(BackpackPasswordStatus.Verdict.NOT_SET, d.verdict)
    }

    @Test fun decodesOther() {
        val d = BackpackPasswordStatus.decode(reply(2))!!
        assertEquals(BackpackPasswordStatus.Verdict.OTHER, d.verdict)
    }

    @Test fun rejectsTruncatedBadOrWrongFrames() {
        assertNull(BackpackPasswordStatus.decode(null))
        assertNull(BackpackPasswordStatus.decode(reply(3).copyOf(5)))
        val bad = reply(3); bad[bad.size - 1] = (bad.last() + 1).toByte()
        assertNull(BackpackPasswordStatus.decode(bad))
        assertNull(BackpackPasswordStatus.decode(BackpackFrame.build(0x10, byteArrayOf(3))))
        assertNull(BackpackPasswordStatus.decode(BackpackFrame.build(0x0F, byteArrayOf(3, 0))))
    }

    @Test fun formatContainsRawAndVerdict() {
        val lines = BackpackPasswordStatus.format(reply(3))
        assertTrue(lines[0].startsWith("Cmd 0x0F raw: 54 0F 00 03 03"))
        assertTrue(lines.any { it.contains("r=3") })
        assertTrue(lines.last().contains("not set"))
        assertTrue(BackpackPasswordStatus.format(null)[0].contains("no valid answer"))
        assertTrue(BackpackPasswordStatus.format(reply(3).copyOf(5)).last().contains("malformed"))
    }
}
