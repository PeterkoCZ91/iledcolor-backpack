package com.batoh.core.data.bluetooth

/**
 * Frame builders for the iledcolor backpack protocol (A951 control / A952 data).
 *
 * Reverse-engineered from iledcolor 1.0.58 (Dart AOT via blutter), see
 * re_iledcolor/01_transport_framing.md:
 * - `SimpleDataBean.getBytes` 0x5ce908, `CrcUtils.getShortCheckNum` 0x5cb758
 * - `SendBean.initSubcontracting` 0x5cb2ac + `funAdd` 0x5cb87c
 *
 * Frame: `54 cmd len16 payload cs16`, len = payload.size + 2, cs16 = sum of all
 * preceding bytes & 0xFFFF, both big-endian. Verified on 160/160 captured frames.
 */
object BackpackFrame {
    const val HEADER: Int = 0x54
    const val CMD_DATA: Int = 0x00
    const val CMD_END: Int = 0x01

    /** Reject truncated notifications and frames with a bad length or checksum. */
    fun isValid(frame: ByteArray): Boolean {
        if (frame.size < 6 || frame[0] != HEADER.toByte()) return false
        val length = ((frame[2].toInt() and 0xFF) shl 8) or (frame[3].toInt() and 0xFF)
        val checksum = ((frame[frame.size - 2].toInt() and 0xFF) shl 8) or
            (frame.last().toInt() and 0xFF)
        return length == frame.size - 4 && checksum == checksum16(frame, frame.size - 2)
    }

    /** Firmware data ACKs declare length 5, excluding their two checksum bytes. */
    fun isValidNotification(frame: ByteArray): Boolean {
        if (isValid(frame)) return true
        if (frame.size != 11 || frame[0] != HEADER.toByte() || frame[1] != 0.toByte() ||
            frame[2] != 0.toByte() || frame[3] != 5.toByte()) return false
        val checksum = ((frame[9].toInt() and 0xFF) shl 8) or (frame[10].toInt() and 0xFF)
        return checksum == checksum16(frame, 9)
    }

    /** Per-frame overhead of a data chunk: 54 00 len16 idx32 chunkLen16 cs16 = 12 B; plus 13 B ATT/app margin. */
    private const val CHUNK_MTU_OVERHEAD = 25

    /** 16-bit sum of all bytes, as `CrcUtils.getShortCheckNum`. */
    fun checksum16(bytes: ByteArray, length: Int = bytes.size): Int {
        var sum = 0
        for (i in 0 until length) sum += bytes[i].toInt() and 0xFF
        return sum and 0xFFFF
    }

    /** Single command frame: `54 cmd len16 payload cs16`. */
    fun build(cmd: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        val len = payload.size + 2
        val out = ByteArray(4 + payload.size + 2)
        out[0] = HEADER.toByte()
        out[1] = cmd.toByte()
        out[2] = (len shr 8).toByte()
        out[3] = len.toByte()
        payload.copyInto(out, 4)
        val cs = checksum16(out, 4 + payload.size)
        out[out.size - 2] = (cs shr 8).toByte()
        out[out.size - 1] = cs.toByte()
        return out
    }

    /** Data bytes per chunk for a negotiated MTU (487 at MTU 512). */
    fun chunkLength(mtu: Int): Int = mtu - CHUNK_MTU_OVERHEAD

    /** One data frame for A952: `54 00 len16 idx32 chunkLen16 data cs16`. */
    fun dataChunk(index: Int, data: ByteArray): ByteArray {
        val body = ByteArray(4 + 2 + data.size)
        body[0] = (index shr 24).toByte()
        body[1] = (index shr 16).toByte()
        body[2] = (index shr 8).toByte()
        body[3] = index.toByte()
        body[4] = (data.size shr 8).toByte()
        body[5] = data.size.toByte()
        data.copyInto(body, 6)
        return build(CMD_DATA, body)
    }

    /** Splits [data] into A952 data frames sized for [mtu]; the last chunk is just shorter. */
    fun dataChunks(data: ByteArray, mtu: Int): List<ByteArray> {
        val chunkLen = chunkLength(mtu)
        require(chunkLen > 0) { "MTU $mtu too small for data chunks" }
        return (data.indices step chunkLen).mapIndexed { index, start ->
            dataChunk(index, data.copyOfRange(start, minOf(start + chunkLen, data.size)))
        }
    }

    /** End-of-transfer frame sent on A952 after the last chunk: `54 01 00 03 01 00 59`. */
    fun end(): ByteArray = build(CMD_END, byteArrayOf(0x01))

    fun int32(value: Int): ByteArray = byteArrayOf(
        (value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte()
    )
}
