package com.batoh.core.data.bluetooth

/**
 * Device capabilities advertised in the BLE scan record, as iledcolor 1.0.58 reads them
 * (`BleManager.isSupport` 0x606e2c, `BleBean` getters; re_iledcolor/01_transport_framing.md §2.5).
 * The app has no GATT command for this — panel size, firmware version and features exist
 * only in the advertisement.
 */
data class BackpackAdvertisement(
    val screenTypeId: Int,
    val width: Int,
    val height: Int,
    val colorType: Int,
    val versionCode: Int,
    val customerId: Int,
    val funCode: Int,
) {
    val isOldDevice: Boolean get() = screenTypeId == OLD_DEVICE_ID
    val supportsTime: Boolean get() = funCode and 0x0001 != 0
    val supportsGif: Boolean get() = funCode and 0x0004 != 0
    val supportsPassword: Boolean get() = funCode and 0x0040 != 0
    val supportsRotation: Boolean get() = funCode and 0x0100 != 0
    val supportsPartition: Boolean get() = funCode and 0x0002 != 0
    val supportsBorder: Boolean get() = funCode and 0x0020 != 0
    val supportsGifText: Boolean get() = funCode and 0x0400 != 0
    val supportsBrightness: Boolean get() = versionCode >= 6

    /** `BleManager.onOtaAvailable` 0x5c60a8: `TimeUtil.setTime` only for non-old devices with funCode bit 0. */
    val syncsTimeOnConnect: Boolean get() = !isOldDevice && supportsTime

    companion object {
        private const val OLD_DEVICE_ID = 0x534C0005

        /** Finds `01 53 4C 00 05` ("SL") or `01 54 42 44 nn` ("TBD", nn 1..21) and parses from there. */
        fun parse(scanRecord: ByteArray): BackpackAdvertisement? {
            val start = locate(scanRecord) ?: return null
            val b = { i: Int -> scanRecord[start + i].toInt() and 0xFF }
            val old = b(1) == 0x53
            return BackpackAdvertisement(
                screenTypeId = (b(1) shl 24) or (b(2) shl 16) or (b(3) shl 8) or b(4),
                height = (b(5) shl 8) or b(6),
                width = (b(7) shl 8) or b(8),
                colorType = b(9),
                versionCode = if (old) (b(10) shl 8) or b(11) else (b(11) shl 8) or b(12),
                customerId = if (old) (b(12) shl 8) or b(13) else (b(13) shl 8) or b(14),
                funCode = (b(15) shl 8) or b(16),
            )
        }

        /** Offset of the manufacturer block (`01 53 4C 00 05` / `01 54 42 44 nn`) in [scanRecord], or null. */
        fun locate(scanRecord: ByteArray): Int? =
            (0..scanRecord.size - 17).firstOrNull { matches(scanRecord, it) }

        private fun matches(r: ByteArray, i: Int): Boolean {
            if (r[i] != 0x01.toByte()) return false
            val sl = r[i + 1] == 0x53.toByte() && r[i + 2] == 0x4C.toByte() && r[i + 3] == 0x00.toByte() && r[i + 4] == 0x05.toByte()
            val tbd = r[i + 1] == 0x54.toByte() && r[i + 2] == 0x42.toByte() && r[i + 3] == 0x44.toByte() && r[i + 4].toInt() in 1..21
            return sl || tbd
        }
    }
}
