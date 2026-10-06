package com.batoh.feature.search

import kotlinx.coroutines.*

/** Query changes invalidate both initial and paged requests before the debounce delay. */
internal class SearchRequestOwner {
    private var generation = 0L
    private var search: Job? = null
    private var page: Job? = null
    var pageError: String? = null
        private set
    val searchRunning: Boolean get() = search?.isCompleted == false
    val pageRunning: Boolean get() = page?.isCompleted == false
    fun invalidate() {
        generation++
        pageError = null
        search?.cancel()
        page?.cancel()
    }
    fun failPage(token: Long, message: String) {
        if (accepts(token)) pageError = message
    }
    fun accepts(token: Long): Boolean = token == generation
    fun search(scope: CoroutineScope, block: suspend CoroutineScope.(Long) -> Unit) {
        val token = generation
        val job = scope.launch(start = CoroutineStart.LAZY) { block(token) }
        search = job
        job.start()
    }
    fun page(scope: CoroutineScope, retry: Boolean = false, block: suspend CoroutineScope.(Long) -> Unit): Boolean {
        if (pageRunning || searchRunning || (!retry && pageError != null)) return false
        pageError = null
        val token = generation
        val job = scope.launch(start = CoroutineStart.LAZY) { block(token) }
        page = job
        job.start()
        return true
    }
}
