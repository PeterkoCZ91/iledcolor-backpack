package com.batoh.core.data.bluetooth

/**
 * Programme payload for Cmd 06 uploads, as iledcolor 1.0.58 builds it
 * (re_iledcolor/02_gif_resource_upload.md §3, verified byte-for-byte on 7 captured payloads):
 *
 * - 24 B header: fileID = CRC-32C(payload[24:]) BE, [partitionCount,0,0,0], 4× u32 BE item offset
 * - item: rect x,y,w,h (u16 BE), 00 00 00, type (6 = GIF file), frameCount u16,
 *   effect, speed, 4, 0, light, 00 00 00, then the unmodified GIF file
 */
object BackpackPayload {
    const val ITEM_TYPE_GIF = 6
    /** "Play programme N that is already stored in the firmware" (iledcolor `ResourceBean.caseToItemDataBean` 0x645bd4). */
    const val ITEM_TYPE_BUILT_IN = 5

    private val CRC32C_TABLE = IntArray(256).also { table ->
        for (i in 0 until 256) {
            var crc = i
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0x82F63B78.toInt() else crc ushr 1 }
            table[i] = crc
        }
    }

    /** CRC-32C (Castagnoli), init/xorout 0xFFFFFFFF, as crclib `Crc32C` in the app. */
    fun crc32c(data: ByteArray, from: Int = 0): Int {
        var crc = -1
        for (i in from until data.size) crc = (crc ushr 8) xor CRC32C_TABLE[(crc xor data[i].toInt()) and 0xFF]
        return crc.inv()
    }

    /** Logical screen size from a GIF header, or null if [gif] is not a GIF. */
    fun gifSize(gif: ByteArray): Pair<Int, Int>? {
        if (gif.size < 13) return null
        val signature = String(gif, 0, 6, Charsets.US_ASCII)
        if (signature != "GIF87a" && signature != "GIF89a") return null
        val w = (gif[6].toInt() and 0xFF) or ((gif[7].toInt() and 0xFF) shl 8)
        val h = (gif[8].toInt() and 0xFF) or ((gif[9].toInt() and 0xFF) shl 8)
        return w to h
    }

    /**
     * Wraps a GIF (must already match the panel, 64×64) into an upload payload.
     * The manufacturer resizes non-matching GIFs with ffmpeg first; we reject them instead.
     */
    fun fromGif(gif: ByteArray, width: Int = 64, height: Int = 64, speed: Int = 100, light: Int = 100, effect: Int = 0): ByteArray {
        require(width == 64 && height == 64) { "Panel must be 64×64" }
        val size = requireNotNull(gifSize(gif)) { "Not a GIF file" }
        require(size == width to height) { "GIF is ${size.first}×${size.second}, panel needs ${width}×$height" }
        return programme(width, height, ITEM_TYPE_GIF, effect, speed, light, gif)
    }

    /**
     * Payload that makes the panel play built-in programme [id] instead of uploading pixels.
     *
     * iledcolor 1.0.58 `ResourceBean.caseToItemDataBean` 0x645ae4–0x645d7c: when
     * `animationType == 3` and `sendId <= ResourceManager.builtInCount` it builds an item with
     * type 5 (0x645be0), rect (0,0,screenSize), effect/speed/light from the resource, stayTime 4,
     * frameType 0 (ItemDataBean default) and one 8-byte frame `[0,0,0,0] + int2Byte(sendId)`
     * (`ByteUtils.int2Byte` 0x5cbad8 = u32 big-endian). It is sent like any upload (Cmd 06 → chunks → end).
     */
    fun builtIn(id: Int, width: Int = 64, height: Int = 64, speed: Int = 100, light: Int = 100, effect: Int = 0): ByteArray {
        require(id >= 0) { "Built-in programme id must not be negative" }
        require(width in 1..0xFFFF && height in 1..0xFFFF) { "Invalid panel size" }
        return programme(width, height, ITEM_TYPE_BUILT_IN, effect, speed, light, ByteArray(4) + BackpackFrame.int32(id))
    }

    /** 24 B header + one 22 B item with a single frame [frame]. */
    private fun programme(width: Int, height: Int, type: Int, effect: Int, speed: Int, light: Int, frame: ByteArray): ByteArray {
        val item = u16(0) + u16(0) + u16(width) + u16(height) +
            byteArrayOf(0, 0, 0, type.toByte()) +
            u16(1) +
            byteArrayOf(effect.toByte(), speed.toByte(), 4, 0, light.toByte(), 0, 0, 0) +
            frame
        val body = byteArrayOf(1, 0, 0, 0) + ByteArray(16) + item // 1 partition, offsets all 0
        return BackpackFrame.int32(crc32c(body.copyOfRange(20, body.size))) + body
    }

    /** Cmd 06 frame announcing [payload]: fileID(4) + size BE(4) + 00 00 00. */
    fun startFrame(payload: ByteArray): ByteArray {
        require(payload.size >= 24) { "Payload is too short" }
        return BackpackFrame.build(0x06, payload.copyOfRange(0, 4) + BackpackFrame.int32(payload.size) + byteArrayOf(0, 0, 0))

    }

    private fun u16(v: Int) = byteArrayOf((v shr 8).toByte(), v.toByte())
}
