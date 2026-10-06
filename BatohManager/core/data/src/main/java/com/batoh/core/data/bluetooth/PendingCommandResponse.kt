package com.batoh.core.data.bluetooth

import kotlinx.coroutines.CompletableDeferred

/**
 * Matches validated A953 notifications to the one serialized control command in flight.
 * Android/GATT stays outside this class so response ordering can be tested on the JVM.
 */
internal class PendingCommandResponse {
    private data class Pending(
        val opcode: Int,
        val response: CompletableDeferred<ByteArray?>
    )

    private var pending: Pending? = null

    @Synchronized
    fun register(opcode: Int): CompletableDeferred<ByteArray?> {
        check(pending == null) { "A BLE command response is already pending" }
        return CompletableDeferred<ByteArray?>().also { pending = Pending(opcode and 0xFF, it) }
    }

    /** Returns true only when a valid notification acknowledged the pending opcode. */
    @Synchronized
    fun acceptA953Notification(data: ByteArray): Boolean {
        if (!BackpackFrame.isValidNotification(data)) return false
        val current = pending?.takeIf { it.opcode == (data[1].toInt() and 0xFF) } ?: return false
        return current.response.complete(data.copyOf())
    }

    /** A disconnect ends the current transaction; its caller will observe a null response. */
    @Synchronized
    fun disconnect() {
        pending?.response?.complete(null)
    }

    @Synchronized
    fun clear(response: CompletableDeferred<ByteArray?>) {
        if (pending?.response === response) pending = null
    }
}
