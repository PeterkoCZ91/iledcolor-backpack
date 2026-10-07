package com.batoh.core.data.bluetooth

/**
 * Read-only "password status" diagnostic (Cmd 0x0F, docs/ble-protocol.md section 10.10).
 *
 * The only frame this class can build is the status query: Cmd 0x0F with six zero bytes.
 * It deliberately offers no way to build a password check or any Cmd 0x0E write.
 */
object BackpackPasswordStatus {
    private const val CMD_PASSWORD_CHECK = 0x0F
    private const val PASSWORD_LENGTH = 6

    /** Decoded reply code `r` of `54 0F 00 03 r cs16`. */
    enum class Verdict { OK, NOT_SET, OTHER }

    data class Decoded(val raw: Int, val verdict: Verdict)

    /** Query frame `54 0F 00 08 00 00 00 00 00 00 00 6B`; the payload is always six zero bytes. */
    fun queryFrame(): ByteArray =
        BackpackFrame.build(CMD_PASSWORD_CHECK, ByteArray(PASSWORD_LENGTH))

    /** Returns null for a missing, truncated, mis-checksummed or wrong-command frame. */
    fun decode(frame: ByteArray?): Decoded? {
        if (frame == null || frame.size != 7 || !BackpackFrame.isValid(frame)) return null
        if (frame[1] != CMD_PASSWORD_CHECK.toByte()) return null
        val r = frame[4].toInt() and 0xFF
        val verdict = when (r) {
            1 -> Verdict.OK
            3 -> Verdict.NOT_SET
            else -> Verdict.OTHER
        }
        return Decoded(r, verdict)
    }

    /** Log lines for the BLE log: raw frame, reply code and a human-readable verdict. */
    fun format(frame: ByteArray?): List<String> {
        if (frame == null) return listOf("Cmd 0x0F: no valid answer")
        val raw = "Cmd 0x0F raw: ${BackpackInfoFormatter.hex(frame)}"
        val d = decode(frame) ?: return listOf(raw, "Cmd 0x0F decoded: malformed frame")
        val text = when (d.verdict) {
            Verdict.NOT_SET -> "password is not set"
            Verdict.OK -> "OK"
            Verdict.OTHER -> "password is set or the answer is unknown"
        }
        return listOf(raw, "Cmd 0x0F r=${d.raw}", "Cmd 0x0F decoded: $text")
    }
}
