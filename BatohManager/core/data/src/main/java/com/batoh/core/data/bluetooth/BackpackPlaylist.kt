package com.batoh.core.data.bluetooth

/**
 * Experimental playlist announcement frames (Cmd 0x03 per item, Cmd 0x08 terminator),
 * derived from an analysis of the manufacturer app.
 *
 * Item frame: `54 03 00 10 | N i | CRC(4) | LEN(4) | 01 00 | 00 00 | cs16`. CRC and LEN are the same
 * values as in the Cmd 0x06 header of the same programme (see [BackpackPayload.startFrame]).
 * This class builds no Cmd 0x02 or any other writing command.
 */
object BackpackPlaylist {
    private const val CMD_ITEM = 0x03
    private const val CMD_END = 0x08

    /** Item frame for programme number [index] (0-based) of [count], announcing [payload]. */
    fun itemFrame(index: Int, count: Int, payload: ByteArray): ByteArray {
        require(payload.size >= 24) { "Payload is too short" }
        return itemFrame(index, count, payload.copyOfRange(0, 4), payload.size)
    }

    /** Item frame from an explicit 4-byte [fileId] and programme [length] in bytes. */
    fun itemFrame(index: Int, count: Int, fileId: ByteArray, length: Int): ByteArray {
        require(fileId.size == 4) { "File ID must be 4 bytes" }
        require(count in 1..255) { "Invalid playlist length: $count" }
        require(index in 0 until count) { "Playlist index $index out of range for $count items" }
        require(length > 0) { "Invalid programme length: $length" }
        val body = byteArrayOf(count.toByte(), index.toByte()) + fileId + BackpackFrame.int32(length) +
            byteArrayOf(0x01, 0x00, 0x00, 0x00)
        return BackpackFrame.build(CMD_ITEM, body)
    }

    /** Terminator frame `54 08 00 03 01 00 60`, sent after the last programme. */
    fun endFrame(): ByteArray = BackpackFrame.build(CMD_END, byteArrayOf(0x01))
}
