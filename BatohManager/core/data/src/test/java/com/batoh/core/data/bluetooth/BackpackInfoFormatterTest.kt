package com.batoh.core.data.bluetooth

import org.junit.Assert.*
import org.junit.Test

class BackpackInfoFormatterTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    // 02 01 06 | 01 'T' 'B' 'D' 05, h=64 w=64 color=3 unused=0xAB ver=6 cust=1 fun=0x0044 | tail
    private val record = bytes(
        0x02, 0x01, 0x06, 0x01, 0x54, 0x42, 0x44, 0x05, 0x00, 0x40, 0x00, 0x40, 0x03,
        0xAB, 0x00, 0x06, 0x00, 0x01, 0x00, 0x44, 0xDE, 0xAD
    )

    @Test fun funCodeBitsAreNamed() {
        assertEquals("gif+password", BackpackInfoFormatter.funCodeNames(0x0044))
        assertEquals("none", BackpackInfoFormatter.funCodeNames(0))
        assertEquals("time+unknown=0x8000", BackpackInfoFormatter.funCodeNames(0x8001))
    }

    @Test fun advertisementShowsRawFieldsUnusedByteAndTail() {
        val text = BackpackInfoFormatter.formatAdvertisement(record).joinToString("\n")
        assertTrue(text.contains("ADV raw [22 B]: 02 01 06 01 54"))
        assertTrue(text.contains("size: 64x64 colorType=3"))
        assertTrue(text.contains("versionCode=6 customerId=1"))
        assertTrue(text.contains("funCode=0x0044 (gif+password)"))
        assertTrue(text.contains("unused b[10]=0xAB"))
        assertTrue(text.contains("tail [2 B]: DE AD"))
        assertTrue(text.contains("nn=5"))
    }

    @Test fun missingOrForeignAdvertisement() {
        assertEquals(listOf("ADV: no stored advertisement"), BackpackInfoFormatter.formatAdvertisement(null))
        val foreign = BackpackInfoFormatter.formatAdvertisement(bytes(1, 2, 3))
        assertEquals("ADV raw [3 B]: 01 02 03", foreign[0])
        assertEquals(2, foreign.size)
    }

    @Test fun stateShowsPayloadAndDecodedValues() {
        // payload: 00 00 01 05 01 -> display on, brightness 11-5=6, rotation index 1
        val frame = BackpackFrame.build(0x10, bytes(0, 0, 1, 5, 1))
        val lines = BackpackInfoFormatter.formatState(frame)
        assertTrue(lines[0].startsWith("Cmd 0x10 raw: 54 10 00 07"))
        assertEquals("Cmd 0x10 frameLen=${frame.size} B", lines[1])
        assertTrue(lines[2].contains("p0=0x00 p1=0x00 p2=0x01 p3=0x05 p4=0x01"))
        assertTrue(lines[3].contains("display=on brightness=6 rotationIndex=1 mirror=false"))
        assertEquals(listOf("Cmd 0x10: no valid answer"), BackpackInfoFormatter.formatState(null))
    }

    @Test fun builtInVariants() {
        val v7 = BackpackFrame.build(0x0D, bytes(7))
        val v8 = BackpackFrame.build(0x0D, bytes(1, 2))
        assertTrue(BackpackInfoFormatter.formatBuiltIn(v7)[1].contains("variant=7 B count=7"))
        assertTrue(BackpackInfoFormatter.formatBuiltIn(v8)[1].contains("variant=8 B count=258"))
        assertTrue(BackpackInfoFormatter.formatBuiltIn(null)[0].contains("no valid answer"))
    }

    @Test fun rcspIsPrintedRawOnly() {
        val r = bytes(0xFE, 0xDC, 0xBA, 0xC0, 0x01, 0xEF)
        assertEquals(listOf("RCSP raw [6 B]: FE DC BA C0 01 EF"), BackpackInfoFormatter.formatRcsp(r))
        assertEquals(listOf("RCSP: no stored answer"), BackpackInfoFormatter.formatRcsp(null))
    }
}
