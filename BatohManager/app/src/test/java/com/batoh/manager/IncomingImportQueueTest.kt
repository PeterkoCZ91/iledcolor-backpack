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

class IncomingImportQueuePersistenceTest {
    private class MemoryStore(var saved: List<String> = emptyList()) : IncomingImportQueue.Store<String> {
        override fun load() = saved
        override fun save(pending: List<String>) { saved = pending }
    }

    @Test
    fun activeAndQueuedItemsArePersistedAndRestoredInOrder() {
        val store = MemoryStore()
        val queue = IncomingImportQueue(store)
        queue.submit("first", restoring = false, previousImportCompleted = false)
        queue.submit("second", restoring = false, previousImportCompleted = false)
        assertEquals(listOf("first", "second"), store.saved)

        val recreated = IncomingImportQueue(MemoryStore(store.saved))
        assertEquals(listOf("first", "second"), recreated.restore())
        assertEquals(IncomingImportQueue.Admission.Ignored, recreated.submit("first", restoring = true, previousImportCompleted = false))
        assertEquals("second", recreated.finish())
        assertNull(recreated.finish())
    }
}
