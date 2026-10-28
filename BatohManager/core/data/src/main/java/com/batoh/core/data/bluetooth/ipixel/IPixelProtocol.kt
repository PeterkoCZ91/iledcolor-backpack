package com.batoh.core.data.bluetooth.ipixel

import java.time.LocalDate
import java.util.UUID
import java.util.zip.CRC32

/** Byte layouts of the iPixel Color panels (GATT service 0x00FA), re-implemented from hardware captures. */
object IPixelProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("000000fa-0000-1000-8000-00805f9b34fb")
    val WRITE_UUID: UUID = UUID.fromString("0000fa02-0000-1000-8000-00805f9b34fb")
    val NOTIFY_UUID: UUID = UUID.fromString("0000fa03-0000-1000-8000-00805f9b34fb")

    const val WINDOW_BYTES = 12 * 1024

    /** Android rejects GATT attribute values longer than this, even when the MTU (517) would allow more. */
    const val MAX_ATTRIBUTE_BYTES = 512

    /** Bytes per FA02 write for a negotiated MTU: `mtu - 3`, within 20..[MAX_ATTRIBUTE_BYTES]. */
    fun pieceSize(mtu: Int): Int = (mtu - 3).coerceIn(20, MAX_ATTRIBUTE_BYTES)

    /** Type bytes whose size was confirmed on real panels (32 x 32, 64 x 16). */
    val VERIFIED_TYPES = setOf(0x81, 0x83)

    private val SIZES: Map<Int, Pair<Int, Int>> = mapOf(
        0x80 to (64 to 64), 0x81 to (32 to 32), 0x82 to (32 to 16), 0x83 to (64 to 16),
        0x84 to (96 to 16), 0x85 to (64 to 20), 0x86 to (128 to 32), 0x87 to (144 to 16),
        0x88 to (192 to 16), 0x89 to (48 to 24), 0x8A to (64 to 32), 0x8B to (96 to 32),
        0x8C to (128 to 32), 0x8D to (96 to 32), 0x8E to (160 to 32), 0x8F to (192 to 32),
        0x90 to (256 to 32), 0x91 to (320 to 32), 0x92 to (384 to 32), 0x93 to (448 to 32),
    )

    fun sizeFor(typeByte: Int): Pair<Int, Int>? = SIZES[typeByte]

    /** `08 00 01 80 hh mm ss 00` — sets the clock (language 0) and makes the panel answer with its type. */
    fun deviceInfoCommand(hour: Int, minute: Int, second: Int): ByteArray =
        byteArrayOf(0x08, 0x00, 0x01, 0x80.toByte(), hour.toByte(), minute.toByte(), second.toByte(), 0x00)

    /** `05 00 04 80 LL` — brightness 0..100. */
    fun brightnessCommand(level: Int): ByteArray {
        require(level in 0..100) { "Brightness $level outside 0..100" }
        return byteArrayOf(0x05, 0x00, 0x04, 0x80.toByte(), level.toByte())
    }

    /** `05 00 06 80 OO` — rotation in quarter turns (0..3 = 0°, 90°, 180°, 270°). */
    fun rotationCommand(quarterTurns: Int): ByteArray {
        require(quarterTurns in 0..3) { "Rotation $quarterTurns outside 0..3" }
        return byteArrayOf(0x05, 0x00, 0x06, 0x80.toByte(), quarterTurns.toByte())
    }

    /** `05 00 07 01 00|01` — display off / on. */
    fun screenCommand(on: Boolean): ByteArray = byteArrayOf(0x05, 0x00, 0x07, 0x01, if (on) 0x01 else 0x00)

    /** `0B 00 06 01 style 24h date yy mm dd dow` — shows clock face [style] (0..8); dow 1 = Monday. */
    fun clockCommand(style: Int, use24h: Boolean, showDate: Boolean, date: LocalDate): ByteArray {
        require(style in 0..8) { "Clock style $style outside 0..8" }
        return byteArrayOf(
            0x0B, 0x00, 0x06, 0x01, style.toByte(), if (use24h) 0x01 else 0x00, if (showDate) 0x01 else 0x00,
            (date.year % 100).toByte(), date.monthValue.toByte(), date.dayOfMonth.toByte(), date.dayOfWeek.value.toByte()
        )
    }

    /** True for the OK answer `05 00 id xx 01` to the setting command [commandId]. */
    fun isSettingAck(n: ByteArray, commandId: Int): Boolean =
        n.size >= 5 && n[0] == 0x05.toByte() && n[1] == 0x00.toByte() && n[2] == commandId.toByte() && n[4] == 0x01.toByte()

    /** Device type byte of the info reply, or null when [response] is not one. */
    fun parseDeviceInfo(response: ByteArray): Int? =
        if (response.size >= 5 && response[3] == 0x80.toByte()) response[4].toInt() and 0xFF else null

    /**
     * One message per [WINDOW_BYTES] window of [file] (PNG when [isGif] is false), always to slot 0:
     * `len16LE(frame + 2) | kind 00 option | size32LE | crc32LE | tail | window`, size and CRC of the whole file.
     */
    fun uploadMessages(file: ByteArray, isGif: Boolean): List<ByteArray> {
        val crc = CRC32().also { it.update(file) }.value
        val kind = if (isGif) 0x03 else 0x02
        val tailFirst = if (isGif) 0x02 else 0x00
        val windows = maxOf(1, (file.size + WINDOW_BYTES - 1) / WINDOW_BYTES)
        return List(windows) { w ->
            val from = w * WINDOW_BYTES
            val part = file.copyOfRange(from, minOf(file.size, from + WINDOW_BYTES))
            val frameLen = 13 + part.size
            val out = ByteArray(2 + frameLen)
            out.putLe(0, frameLen + 2, 2)
            out[2] = kind.toByte()
            out[3] = 0x00
            out[4] = if (w == 0) 0x00 else 0x02
            out.putLe(5, file.size, 4)
            out.putLe(9, crc.toInt(), 4)
            out[13] = tailFirst.toByte()
            out[14] = 0x00
            part.copyInto(out, 15)
            out
        }
    }

    sealed interface UploadAck {
        data object WindowAccepted : UploadAck
        data object Done : UploadAck
        data class Rejected(val code: Int) : UploadAck
    }

    /** `05 00 kk 00 cc` notification after each window; null for anything else (e.g. info replies). */
    fun parseUploadAck(n: ByteArray): UploadAck? {
        if (n.size < 5 || n[0] != 0x05.toByte()) return null
        return when (val code = n[4].toInt() and 0xFF) {
            0, 1 -> UploadAck.WindowAccepted
            3 -> UploadAck.Done
            else -> UploadAck.Rejected(code)
        }
    }

    private fun ByteArray.putLe(at: Int, value: Int, bytes: Int) {
        for (k in 0 until bytes) this[at + k] = (value ushr (8 * k)).toByte()
    }
}
