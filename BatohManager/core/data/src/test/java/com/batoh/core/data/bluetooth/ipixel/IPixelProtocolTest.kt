package com.batoh.core.data.bluetooth.ipixel

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.zip.CRC32

class IPixelProtocolTest {
    private fun hex(s: String) = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun u(b: Byte) = b.toInt() and 0xFF
    private fun le(m: ByteArray, at: Int, n: Int) = (0 until n).sumOf { (u(m[at + it]).toLong()) shl (8 * it) }

    @Test fun parse_reads32x32Panel() {
        val adv = IPixelAdvertisement.parse("LED_BLE_12345678", hex("020106" + "0AFF" + "5452" + "00720007020081"))!!
        assertEquals(0x81, adv.typeByte)
        assertEquals(32 to 32, adv.size)
    }

    @Test fun parse_reads64x16Panel() {
        val adv = IPixelAdvertisement.parse(null, hex("020106" + "0AFF" + "5452" + "00720007010083"))!!
        assertEquals(64 to 16, adv.size)
    }

    @Test fun parse_ignoresBackpackRecord() {
        val record = hex("020106" + "15FF" + "0154424405" + "0040" + "0040" + "03" + "00" + "001F" + "0002" + "0105" + "0000")
        assertNull(IPixelAdvertisement.parse("TBD", record))
    }

    @Test fun sizeFor_unknownTypeIsNull() {
        assertNull(IPixelProtocol.sizeFor(0x7F))
        assertNull(IPixelProtocol.sizeFor(0x94))
        assertEquals(448 to 32, IPixelProtocol.sizeFor(0x93))
    }

    @Test fun deviceInfoCommand_layout() {
        assertArrayEquals(hex("08 00 01 80 11 1B 31 00"), IPixelProtocol.deviceInfoCommand(17, 27, 49))
    }

    @Test fun parseDeviceInfo_hardwareAnswers() {
        assertEquals(0x81, IPixelProtocol.parseDeviceInfo(hex("0C 00 01 80 81 1B 31 00 00 01 00 01")))
        assertEquals(0x83, IPixelProtocol.parseDeviceInfo(hex("0B 00 01 80 83 20 03 00 00 01 00")))
        assertNull(IPixelProtocol.parseDeviceInfo(hex("0C 00 01")))
    }

    @Test fun uploadMessages_singlePng() {
        val file = ByteArray(169) { it.toByte() }
        val msgs = IPixelProtocol.uploadMessages(file, isGif = false)
        assertEquals(1, msgs.size)
        val m = msgs[0]
        assertEquals(2 + 13 + 169, m.size)
        assertEquals((169 + 13 + 2).toLong(), le(m, 0, 2))
        assertArrayEquals(hex("02 00 00"), m.copyOfRange(2, 5))
        assertEquals(169L, le(m, 5, 4))
        assertEquals(CRC32().also { it.update(file) }.value, le(m, 9, 4))
        assertArrayEquals(hex("00 00"), m.copyOfRange(13, 15))
        assertArrayEquals(file, m.copyOfRange(15, m.size))
    }

    @Test fun uploadMessages_gifWindows() {
        val file = ByteArray(30_000) { (it * 7).toByte() }
        val crc = CRC32().also { it.update(file) }.value
        val msgs = IPixelProtocol.uploadMessages(file, isGif = true)
        assertEquals(3, msgs.size)
        assertEquals(listOf(0, 2, 2), msgs.map { u(it[4]) })
        assertEquals(listOf(12288, 12288, 5424), msgs.map { it.size - 15 })
        msgs.forEach {
            assertEquals(3, u(it[2])); assertEquals(0, u(it[3]))
            assertArrayEquals(hex("02 00"), it.copyOfRange(13, 15))
            assertEquals(30_000L, le(it, 5, 4)); assertEquals(crc, le(it, 9, 4))
            assertEquals((it.size - 2 + 2).toLong(), le(it, 0, 2))
        }
        assertArrayEquals(file, msgs.map { it.copyOfRange(15, it.size) }.reduce { a, b -> a + b })
    }

    @Test fun parseUploadAck_codes() {
        assertEquals(IPixelProtocol.UploadAck.WindowAccepted, IPixelProtocol.parseUploadAck(hex("05 00 03 00 01")))
        assertEquals(IPixelProtocol.UploadAck.Done, IPixelProtocol.parseUploadAck(hex("05 00 02 00 03")))
        assertEquals(IPixelProtocol.UploadAck.Rejected(5), IPixelProtocol.parseUploadAck(hex("05 00 03 00 05")))
        assertNull(IPixelProtocol.parseUploadAck(hex("0C 00 01 80 81")))
    }

    @Test fun pieceSize_isMtuMinusHeaderClampedToGattAttributeLimit() {
        assertEquals(20, IPixelProtocol.pieceSize(23))
        assertEquals(244, IPixelProtocol.pieceSize(247))
        assertEquals(512, IPixelProtocol.pieceSize(517))
        assertEquals(20, IPixelProtocol.pieceSize(0))
    }

    @Test fun brightnessCommand_layout() {
        assertArrayEquals(hex("05 00 04 80 14"), IPixelProtocol.brightnessCommand(20))
        assertThrows(IllegalArgumentException::class.java) { IPixelProtocol.brightnessCommand(101) }
    }

    @Test fun rotationCommand_layout() {
        assertArrayEquals(hex("05 00 06 80 02"), IPixelProtocol.rotationCommand(2))
        assertThrows(IllegalArgumentException::class.java) { IPixelProtocol.rotationCommand(4) }
    }

    @Test fun screenCommand_layout() {
        assertArrayEquals(hex("05 00 07 01 00"), IPixelProtocol.screenCommand(false))
        assertArrayEquals(hex("05 00 07 01 01"), IPixelProtocol.screenCommand(true))
    }

    @Test fun clockCommand_layout() {
        assertArrayEquals(hex("0B 00 06 01 01 01 01 1A 0A 1B 02"),
            IPixelProtocol.clockCommand(1, use24h = true, showDate = true, date = LocalDate.of(2026, 10, 27)))
        assertThrows(IllegalArgumentException::class.java) {
            IPixelProtocol.clockCommand(9, use24h = false, showDate = false, date = LocalDate.of(2026, 10, 27))
        }
    }

    @Test fun isSettingAck_hardwareAnswers() {
        assertTrue(IPixelProtocol.isSettingAck(hex("05 00 04 80 01"), 0x04))
        assertTrue(IPixelProtocol.isSettingAck(hex("05 00 06 01 01"), 0x06))
        assertFalse(IPixelProtocol.isSettingAck(hex("05 00 04 80 00"), 0x04))
        assertFalse(IPixelProtocol.isSettingAck(hex("05 00 06 80 01"), 0x04))
    }

    @Test fun deviceInfoCommand_usesBinaryTime() {
        assertArrayEquals(hex("08 00 01 80 12 33 28 00"), IPixelProtocol.deviceInfoCommand(18, 51, 40))
    }
}
