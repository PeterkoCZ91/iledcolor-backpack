package com.batoh.core.data.bluetooth

import java.util.zip.CRC32C
import org.junit.Assert.*
import org.junit.Test

class BuiltInProgramTest {
    private fun hex(s: String) = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun builtInPayloadMatchesManufacturerItemLayout() {
        // ResourceBean.caseToItemDataBean 0x645bd4–0x645d38: type 5, rect (0,0,64,64), effect 0, speed 100,
        // stayTime 4, frameType 0, light 100, one frame [0,0,0,0] + int2Byte(sendId) (u32 BE).
        val payload = BackpackPayload.builtIn(7)
        assertEquals(24 + 22 + 8, payload.size)
        assertArrayEquals(hex("01000000" + "00".repeat(16)), payload.copyOfRange(4, 24))
        assertArrayEquals(
            hex("0000 0000 0040 0040 000000 05 0001 00 64 04 00 64 000000" + "00000000 00000007"),
            payload.copyOfRange(24, payload.size)
        )
        val crc = CRC32C().apply { update(payload, 24, payload.size - 24) }
        assertArrayEquals(BackpackFrame.int32(crc.value.toInt()), payload.copyOfRange(0, 4))
        assertTrue(BackpackFrame.isValid(BackpackPayload.startFrame(payload)))
    }

    @Test fun builtInIdIsBigEndianAndOptionsAreApplied() {
        val payload = BackpackPayload.builtIn(0x01020304, width = 32, height = 16, speed = 50, light = 80, effect = 2)
        assertArrayEquals(hex("0000 0000 0020 0010 000000 05 0001 02 32 04 00 50 000000 00000000 01020304"),
            payload.copyOfRange(24, payload.size))
        assertThrows(IllegalArgumentException::class.java) { BackpackPayload.builtIn(-1) }
    }

    @Test fun countQueryMatchesCapturedFrame() {
        // official_success_dump.txt: writeDataByBle 540D0003000064
        assertArrayEquals(hex("540D0003000064"), BackpackCommands.queryBuiltInCount())
    }

    @Test fun countParsesBothResponseLengths() {
        // Captured answer of this backpack (manufacturer logged "内置节目数量:0").
        assertEquals(0, BackpackCommands.parseBuiltInCount(hex("540D000400000065")))
        assertEquals(0x0123, BackpackCommands.parseBuiltInCount(BackpackFrame.build(0x0D, hex("0123"))))
        assertEquals(12, BackpackCommands.parseBuiltInCount(BackpackFrame.build(0x0D, hex("0C"))))
        assertEquals(200, BackpackCommands.parseBuiltInCount(BackpackFrame.build(0x0D, hex("C8"))))
    }

    @Test fun countRejectsCorruptOrForeignFrames() {
        assertNull(BackpackCommands.parseBuiltInCount(hex("540D000400000066"))) // bad checksum
        assertNull(BackpackCommands.parseBuiltInCount(BackpackFrame.build(0x10, hex("0C"))))
        assertNull(BackpackCommands.parseBuiltInCount(BackpackFrame.build(0x0D, hex("000102"))))
        assertNull(BackpackCommands.parseBuiltInCount(hex("540D00")))
    }

    @Test fun timeSyncFollowsManufacturerRule() {
        fun adv(id: Int, fun_: Int) = BackpackAdvertisement(id, 64, 64, 3, 14, 1, fun_)
        assertFalse(adv(0x54424405, 0x0044).syncsTimeOnConnect) // this backpack
        assertTrue(adv(0x54424405, 0x0045).syncsTimeOnConnect)
        assertFalse(adv(0x534C0005, 0x0001).syncsTimeOnConnect) // old device never gets 0x0C
    }
}
