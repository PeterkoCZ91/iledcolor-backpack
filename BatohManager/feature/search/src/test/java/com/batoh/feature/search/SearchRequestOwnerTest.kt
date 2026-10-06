package com.batoh.feature.search

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SearchRequestOwnerTest {
    @Test fun changingQueryCancelsSearchAndPageAndRejectsDelayedOldResult() = runBlocking {
        val owner = SearchRequestOwner()
        var searchCancelled = false
        owner.search(this) {
            try { awaitCancellation() } finally { searchCancelled = true }
        }
        yield()
        owner.invalidate()
        yield()
        assertTrue(searchCancelled)

        val delayedResult = CompletableDeferred<Unit>()
        var displayed = "new query"
        try {
            assertTrue(owner.page(this) { token ->
                // Simulate a backend delivering a stale result despite cancellation.
                withContext(NonCancellable) {
                    delayedResult.await()
                    if (owner.accepts(token)) displayed = "old query page"
                }
            })
            yield()
            owner.invalidate()
        } finally { delayedResult.complete(Unit) }
        yield()
        yield()
        assertEquals("new query", displayed)
    }

    @Test fun offlinePageFailureStopsAutomaticRequestsUntilExplicitRetry() = runBlocking {
        val owner = SearchRequestOwner()
        var calls = 0
        assertTrue(owner.page(this) { token -> calls++; owner.failPage(token, "Offline") })
        yield()
        repeat(10) { assertFalse(owner.page(this) { calls++ }) }
        assertEquals(1, calls)
        assertEquals("Offline", owner.pageError)
        assertTrue(owner.page(this, retry = true) { calls++ })
        yield()
        assertEquals(2, calls)
        assertNull(owner.pageError)
    }

    @Test fun changingFilterResetsFailedPageAndRejectsOldFailure() = runBlocking {
        val owner = SearchRequestOwner()
        var oldToken = -1L
        owner.page(this) { token -> oldToken = token; owner.failPage(token, "Offline") }
        yield()
        owner.invalidate()
        owner.failPage(oldToken, "Late old error")
        assertNull(owner.pageError)
        assertTrue(owner.page(this) { })
    }

    @Test fun initialSearchAndSecondPageCannotRaceCurrentPage() = runBlocking {
        val owner = SearchRequestOwner()
        val finishSearch = CompletableDeferred<Unit>()
        owner.search(this) { finishSearch.await() }
        assertFalse(owner.page(this) { fail("Page must wait for initial search") })
        finishSearch.complete(Unit)
        yield()
        val finishPage = CompletableDeferred<Unit>()
        assertTrue(owner.page(this) { finishPage.await() })
        assertFalse(owner.page(this) { fail("Duplicate page") })
        finishPage.complete(Unit)
        Unit
    }
}
