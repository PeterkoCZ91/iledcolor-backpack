package com.batoh.feature.backpack

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Holds a reservation through cancellation cleanup, not just while Job.isActive. */
internal class SingleOperationOwner {
    private var job: Job? = null
    val isOccupied: Boolean
        @Synchronized get() = job?.isCompleted == false

    @Synchronized
    fun launch(scope: CoroutineScope, block: suspend CoroutineScope.() -> Unit): Boolean {
        if (isOccupied) return false
        // Reserve before executing code; even immediate dispatch cannot race assignment.
        val next = scope.launch(start = CoroutineStart.LAZY, block = block)
        job = next
        next.start()
        return true
    }

    @Synchronized
    fun cancel(cause: CancellationException? = null) {
        job?.cancel(cause)
    }
}
