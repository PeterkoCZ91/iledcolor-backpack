package com.batoh.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingImportQueueTest {
    @Test
    fun restoredLaunchIntentIsIgnoredWhileImportIsActive() {
        val queue = IncomingImportQueue<String>()

        assertEquals(IncomingImportQueue.Admission.Start("first"), queue.submit("first", restoring = false, previousImportCompleted = false))
        assertEquals(IncomingImportQueue.Admission.Ignored, queue.submit("first", restoring = true, previousImportCompleted = false))
        assertNull(queue.finish())
    }

    @Test
    fun distinctSharesReceivedWhileBusyAreStartedInOrder() {
        val queue = IncomingImportQueue<String>()

        assertEquals(IncomingImportQueue.Admission.Start("first"), queue.submit("first", restoring = false, previousImportCompleted = false))
        assertEquals(IncomingImportQueue.Admission.Queued, queue.submit("second", restoring = false, previousImportCompleted = false))
        assertEquals(IncomingImportQueue.Admission.Queued, queue.submit("third", restoring = false, previousImportCompleted = false))
        assertEquals("second", queue.finish())
        assertEquals("third", queue.finish())
        assertNull(queue.finish())
    }

    @Test
    fun completedShareIsNotRepeatedWhenActivityRestores() {
        val queue = IncomingImportQueue<String>()

        assertEquals(IncomingImportQueue.Admission.Ignored, queue.submit("already-imported", restoring = true, previousImportCompleted = true))
        assertEquals(IncomingImportQueue.Admission.Start("new-share"), queue.submit("new-share", restoring = false, previousImportCompleted = true))
    }
}
