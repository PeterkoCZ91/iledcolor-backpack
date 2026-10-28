package com.batoh.core.data.bluetooth.ipixel

/**
 * iPixel Color panels (`LED_BLE_xxxxxxxx`) advertise manufacturer data with company id 0x5254
 * (bytes `54 52` on the wire) and a 7-byte value whose last byte equals the device type byte
 * of the info reply. Seen on two units only, so treat [size] as a hint until the info query confirms it.
 */
data class IPixelAdvertisement(val typeByte: Int) {
    val size: Pair<Int, Int>? get() = IPixelProtocol.sizeFor(typeByte)

    companion object {
        private const val NAME_PREFIX = "LED_BLE_"
        private const val MIN_VALUE_BYTES = 7

        /** Walks the AD structures of [scanRecord]; [name] is only an extra hint and is not required. */
        @Suppress("UNUSED_PARAMETER")
        fun parse(name: String?, scanRecord: ByteArray): IPixelAdvertisement? {
            var i = 0
            while (i < scanRecord.size) {
                val len = scanRecord[i].toInt() and 0xFF
                if (len == 0) break
                val end = i + 1 + len
                if (end > scanRecord.size) break
                val type = scanRecord[i + 1].toInt() and 0xFF
                // data = company(2) + value(>= 7)
                if (type == 0xFF && len - 1 >= 2 + MIN_VALUE_BYTES &&
                    scanRecord[i + 2] == 0x54.toByte() && scanRecord[i + 3] == 0x52.toByte()
                ) {
                    return IPixelAdvertisement(scanRecord[end - 1].toInt() and 0xFF)
                }
                i = end
            }
            return null
        }

        fun hasPanelName(name: String?): Boolean = name?.startsWith(NAME_PREFIX) == true
    }
}
