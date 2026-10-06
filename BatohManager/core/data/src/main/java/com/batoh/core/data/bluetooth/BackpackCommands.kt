package com.batoh.core.data.bluetooth

/**
 * Settings commands for A951 (re_iledcolor/03_command_catalog.md). Device answers on A953
 * with `54 cmd 00 03 status cs16`: 1/3 = OK, 2 = insufficient space, 0/4 = error.
 */
object BackpackCommands {
    /** Delete all programmes ("Clear programs"). */
    fun clearPrograms() = BackpackFrame.build(0x02, byteArrayOf(0x00))

    /** Brightness 1 (dimmest) .. 10 (brightest); device wants 11 - level. Needs versionCode >= 6. */
    fun brightness(level: Int): ByteArray {
        require(level in 1..10)
        return BackpackFrame.build(0x09, byteArrayOf((11 - level).toByte(), 0, 0, 0, 0, 0, 0, 0, 0))
    }

    /** Panel on/off. This is the old "Cmd 0A activate slot" — slot 0 turns the panel OFF. */
    fun screen(on: Boolean) = BackpackFrame.build(0x0A, byteArrayOf(if (on) 1 else 0, 0, 0, 0, 0, 0, 0, 0, 0))

    /** Rotation index 0..3 (probably 0/90/180/270°) + mirror. */
    fun rotate(index: Int, mirror: Boolean): ByteArray {
        require(index in 0..3)
        val rot = when (index) { 1 -> 1; 2 -> 2; 3 -> 4; else -> 0 }
        return BackpackFrame.build(0x0B, byteArrayOf((rot or (if (mirror) 0x10 else 0)).toByte()))
    }

    /** Clock sync; the manufacturer sends it after connecting only when funCode has bit 0x0001. */
    fun setTime(t: java.time.LocalDateTime = java.time.LocalDateTime.now()) = BackpackFrame.build(
        0x0C, byteArrayOf(
            (t.year % 100).toByte(), t.monthValue.toByte(), t.dayOfMonth.toByte(),
            t.hour.toByte(), t.minute.toByte(), t.second.toByte(), 0
        )
    )

    /** Query screen/brightness/rotation; answer parsed by [parseState]. */
    fun queryState() = BackpackFrame.build(0x10, byteArrayOf(0x00))

    /**
     * Ask how many built-in programmes the firmware holds. Same frame as the older iledcolor build
     * (`PlayListUtil.getDeviceResCount`) and the manufacturer capture: `54 0D 00 03 00 00 64`.
     */
    fun queryBuiltInCount() = BackpackFrame.build(0x0D, byteArrayOf(0x00))

    /**
     * Built-in programme count from a Cmd 0D answer (`NewBleSendManager.onData`): a 7 B frame carries
     * it in d[4] (0xa32c58), an 8 B frame as d[4]<<8 | d[5] (0xa330d4). The captured answer of this
     * backpack `54 0D 00 04 00 00 00 65` means 0.
     */
    fun parseBuiltInCount(frame: ByteArray): Int? {
        if (!BackpackFrame.isValid(frame) || frame[1] != 0x0D.toByte()) return null
        return when (frame.size) {
            7 -> frame[4].toInt() and 0xFF
            8 -> ((frame[4].toInt() and 0xFF) shl 8) or (frame[5].toInt() and 0xFF)
            else -> null
        }
    }

    data class State(val screenOn: Boolean, val brightness: Int, val rotationIndex: Int, val mirror: Boolean)

    fun parseState(frame: ByteArray): State? {
        if (frame.size < 11 || !BackpackFrame.isValid(frame) || frame[1] != 0x10.toByte()) return null
        val p = frame.copyOfRange(4, frame.size - 2).map { it.toInt() and 0xFF }
        val rot = when (p[4] and 0x0F) { 1 -> 1; 2 -> 2; 4 -> 3; else -> 0 }
        val brightness = 11 - p[3]
        if (brightness !in 1..10) return null
        return State(p[2] and 1 == 1, brightness, rot, (p[4] shr 4) != 0)
    }
}
