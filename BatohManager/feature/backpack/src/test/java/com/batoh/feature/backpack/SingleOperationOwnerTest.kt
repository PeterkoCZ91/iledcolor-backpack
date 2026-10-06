package com.batoh.feature.backpack

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SingleOperationOwnerTest {
    @Test fun secondOperationIsRejectedUntilFirstCompletes() = runBlocking {
        val owner = SingleOperationOwner()
        val finish = CompletableDeferred<Unit>()
        var starts = 0
        assertTrue(owner.launch(this) { starts++; finish.await() })
        yield()
        assertTrue(owner.isOccupied)
        assertFalse(owner.launch(this) { starts++ })
        assertEquals(1, starts)
        finish.complete(Unit)
        yield()
        assertFalse(owner.isOccupied)
        assertTrue(owner.launch(this) { starts++ })
        yield()
        assertEquals(2, starts)
    }

    @Test fun cancelledOperationReservesSlotUntilCleanupCompletes() = runBlocking {
        val owner = SingleOperationOwner()
        val cleanupEntered = CompletableDeferred<Unit>()
        val cleanupFinish = CompletableDeferred<Unit>()
        var retryStarted = false
        assertTrue(owner.launch(this) {
            try { awaitCancellation() }
            finally {
                withContext(NonCancellable) {
                    cleanupEntered.complete(Unit)
                    cleanupFinish.await()
                }
            }
        })
        yield()
        owner.cancel()
        cleanupEntered.await()
        assertTrue(owner.isOccupied)
        assertFalse(owner.launch(this) { retryStarted = true })
        assertFalse(retryStarted)
        cleanupFinish.complete(Unit)
        yield()
        yield()
        assertFalse(owner.isOccupied)
        assertTrue(owner.launch(this) { retryStarted = true })
        yield()
        assertTrue(retryStarted)
    }

    @Test fun immediateDispatchReservesBeforeRunningOperation() = runBlocking {
        val owner = SingleOperationOwner()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var nestedAccepted = true
            assertTrue(owner.launch(scope) {
                nestedAccepted = owner.launch(scope) { error("Must not run concurrently") }
            })
            assertFalse(nestedAccepted)
            assertFalse(owner.isOccupied)
        } finally { scope.cancel() }
    }

    @Test fun failureReleasesReservationForRetry() = runBlocking {
        val owner = SingleOperationOwner()
        val failure = CompletableDeferred<Throwable>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, e -> failure.complete(e) })
        try {
            assertTrue(owner.launch(scope) { error("Simulated transfer failure") })
            assertEquals("Simulated transfer failure", failure.await().message)
            assertFalse(owner.isOccupied)
            var retried = false
            assertTrue(owner.launch(scope) { retried = true })
            assertTrue(retried)
        } finally { scope.cancel() }
    }
}
