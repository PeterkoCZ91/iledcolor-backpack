package com.batoh.core.data.bluetooth

import org.junit.Assert.*
import org.junit.Test

class BackpackAdvertisementTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun parsesTbdRecordAfterOtherAdStructures() {
        // flags AD + manufacturer data containing 01 'TBD' 05 | h=64 w=64 | color 3 | ? | ver 31 | customer 2 | funCode 0x0105
        val record = hex("020106" + "15FF" + "0154424405" + "0040" + "0040" + "03" + "00" + "001F" + "0002" + "0105" + "0000")
        val adv = BackpackAdvertisement.parse(record)!!
        assertEquals(64, adv.width); assertEquals(64, adv.height)
        assertEquals(3, adv.colorType); assertEquals(31, adv.versionCode); assertEquals(2, adv.customerId)
        assertEquals(0x0105, adv.funCode)
        assertTrue(adv.supportsRotation); assertTrue(adv.supportsTime); assertTrue(adv.supportsGif)
        assertFalse(adv.supportsPassword); assertFalse(adv.isOldDevice)
    }

    @Test fun oldSlDeviceUsesShiftedVersionField() {
        val adv = BackpackAdvertisement.parse(hex("01534C0005" + "0020" + "0040" + "01" + "0007" + "0003" + "00" + "0000"))!!
        assertTrue(adv.isOldDevice); assertEquals(7, adv.versionCode); assertEquals(3, adv.customerId)
        assertFalse(adv.supportsRotation)
    }

    @Test fun rejectsUnrelatedOrTruncatedRecords() {
        assertNull(BackpackAdvertisement.parse(hex("0201060AFF4C001005031C")))
        assertNull(BackpackAdvertisement.parse(hex("0154424405004000")))
        assertNull(BackpackAdvertisement.parse(hex("01544244160040004003000000000000000000"))) // nn=0x16 out of range
    }
}
