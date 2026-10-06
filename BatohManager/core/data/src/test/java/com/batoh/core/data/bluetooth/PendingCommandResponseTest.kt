package com.batoh.core.data.bluetooth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test

class PendingCommandResponseTest {
    private fun response(opcode: Int, status: Int) =
        BackpackFrame.build(opcode, byteArrayOf(status.toByte()))

    @Test fun onlyValidatedResponseForPendingOpcodeCompletesCommand() = runBlocking {
        val tracker = PendingCommandResponse()
        val pending = tracker.register(0x09)

        assertFalse(tracker.acceptA953Notification(response(0x0A, 1)))
        val corrupt = response(0x09, 1).apply { this[lastIndex] = (last() + 1).toByte() }
        assertFalse(tracker.acceptA953Notification(corrupt))
        assertFalse(pending.isCompleted)

        val matching = response(0x09, 1)
        assertTrue(tracker.acceptA953Notification(matching))
        assertArrayEquals(matching, pending.await())
    }

    @Test fun matchingNackIsDeliveredForCallerToInterpret() = runBlocking {
        val tracker = PendingCommandResponse()
        val pending = tracker.register(0x02)
        val nack = response(0x02, 4)

        assertTrue(tracker.acceptA953Notification(nack))
        assertEquals(4, requireNotNull(pending.await())[4].toInt() and 0xFF)
    }

    @Test fun timeoutCleanupIgnoresLateResponse() = runBlocking {
        val tracker = PendingCommandResponse()
        val pending = tracker.register(0x0C)

        assertNull(withTimeoutOrNull(1) { pending.await() })
        tracker.clear(pending)
        assertFalse(tracker.acceptA953Notification(response(0x0C, 1)))
        assertFalse(pending.isCompleted)
    }

    @Test fun disconnectCompletesPendingCommandWithNull() = runBlocking {
        val tracker = PendingCommandResponse()
        val pending = tracker.register(0x10)

        tracker.disconnect()

        assertNull(pending.await())
        tracker.clear(pending)
        assertFalse(tracker.acceptA953Notification(response(0x10, 1)))
    }

    @Test fun cancellationAndCleanupDoNotLeaveAResponseReceiver() = runBlocking {
        val tracker = PendingCommandResponse()
        val pending = tracker.register(0x09)
        val waiter = async { pending.await() }

        waiter.cancelAndJoin()
        tracker.clear(pending)

        assertFalse(tracker.acceptA953Notification(response(0x09, 1)))
    }

    @Test fun cleanupForOldRequestCannotClearNewRequest() = runBlocking {
        val tracker = PendingCommandResponse()
        val old = tracker.register(0x09)
        tracker.clear(old)
        val current = tracker.register(0x0A)

        tracker.clear(old)

        val ack = response(0x0A, 1)
        assertTrue(tracker.acceptA953Notification(ack))
        assertArrayEquals(ack, current.await())
    }
}
