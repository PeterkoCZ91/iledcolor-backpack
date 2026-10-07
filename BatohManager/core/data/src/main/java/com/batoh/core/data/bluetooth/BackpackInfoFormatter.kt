package com.batoh.core.data.bluetooth

/**
 * Pure, read-only decoding and formatting for the "Backpack info" diagnostic action.
 * Nothing here touches BLE; callers pass in bytes that were already stored or received.
 */
object BackpackInfoFormatter {
    private val FUN_BITS = listOf(
        0x0001 to "time", 0x0002 to "partition", 0x0004 to "gif", 0x0020 to "border",
        0x0040 to "password", 0x0100 to "rotation", 0x0400 to "gifText",
    )

    fun hex(bytes: ByteArray, from: Int = 0, to: Int = bytes.size): String =
        (from until to).joinToString(" ") { "%02X".format(bytes[it]) }

    fun funCodeNames(funCode: Int): String {
        val known = FUN_BITS.filter { funCode and it.first != 0 }.map { it.second }
        val unknownMask = funCode and FUN_BITS.fold(0) { m, b -> m or b.first }.inv() and 0xFFFF
        val all = known + (if (unknownMask != 0) listOf("unknown=0x%04X".format(unknownMask)) else emptyList())
        return if (all.isEmpty()) "none" else all.joinToString("+")
    }

    /** Raw scan record as hex plus the decoded manufacturer block; [raw] null = nothing cached. */
    fun formatAdvertisement(raw: ByteArray?): List<String> {
        if (raw == null) return listOf("ADV: no stored advertisement")
        val lines = mutableListOf("ADV raw [${raw.size} B]: ${hex(raw)}")
        val start = BackpackAdvertisement.locate(raw)
        val adv = BackpackAdvertisement.parse(raw)
        if (start == null || adv == null) return lines + "ADV: no backpack manufacturer block found"
        val old = raw[start + 1] == 0x53.toByte()
        val type = if (old) "SL(old) id=0x%08X".format(adv.screenTypeId)
        else "TBD id=0x%08X nn=%d".format(adv.screenTypeId, raw[start + 4].toInt() and 0xFF)
        val unused = raw[start + 10].toInt() and 0xFF
        val blockEnd = start + 17
        lines += "ADV block @$start: ${hex(raw, start, blockEnd)}"
        lines += "ADV type: $type"
        lines += "ADV size: ${adv.width}x${adv.height} colorType=${adv.colorType}"
        lines += "ADV versionCode=${adv.versionCode} customerId=${adv.customerId}"
        lines += "ADV funCode=0x%04X (%s)".format(adv.funCode, funCodeNames(adv.funCode))
        lines += "ADV unused b[10]=0x%02X%s".format(unused, if (old) " (part of versionCode on old devices)" else "")
        lines += "ADV tail [${raw.size - blockEnd} B]: " + (if (blockEnd < raw.size) hex(raw, blockEnd, raw.size) else "-")
        return lines
    }

    /** Cmd 0x10 answer; [frame] null = no valid answer. */
    fun formatState(frame: ByteArray?): List<String> {
        if (frame == null) return listOf("Cmd 0x10: no valid answer")
        val lines = mutableListOf("Cmd 0x10 raw: ${hex(frame)}", "Cmd 0x10 frameLen=${frame.size} B")
        if (frame.size >= 6) {
            val payload = frame.copyOfRange(4, frame.size - 2)
            lines += "Cmd 0x10 payload [${payload.size} B]: " +
                payload.withIndex().joinToString(" ") { "p${it.index}=0x%02X".format(it.value) }
        }
        val s = BackpackCommands.parseState(frame)
        lines += if (s == null) "Cmd 0x10 decoded: not parseable"
        else "Cmd 0x10 decoded: display=${if (s.screenOn) "on" else "off"} brightness=${s.brightness} rotationIndex=${s.rotationIndex} mirror=${s.mirror}"
        return lines
    }

    /** Cmd 0x0D answer (7 B or 8 B variant). */
    fun formatBuiltIn(frame: ByteArray?): List<String> {
        if (frame == null) return listOf("Cmd 0x0D: no valid answer")
        val count = BackpackCommands.parseBuiltInCount(frame)
        val variant = when (frame.size) { 7 -> "7 B"; 8 -> "8 B"; else -> "${frame.size} B (unknown)" }
        return listOf("Cmd 0x0D raw: ${hex(frame)}", "Cmd 0x0D variant=$variant count=${count ?: "n/a"}")
    }

    /** Already received RCSP/OTA answer (FE DC BA ... EF), printed without interpretation. */
    fun formatRcsp(frame: ByteArray?): List<String> =
        if (frame == null) listOf("RCSP: no stored answer")
        else listOf("RCSP raw [${frame.size} B]: ${hex(frame)}")
}
