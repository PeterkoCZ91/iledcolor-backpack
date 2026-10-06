package com.batoh.core.data.remote

import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Sliding-window rate limiter.
 * Ensures no more than [maxRequests] calls within [windowMs] milliseconds.
 * If the limit is reached, suspends until the oldest request falls out of the window.
 */
class RateLimiter(
    private val maxRequests: Int,
    private val windowMs: Long
) {
    private val timestamps = ConcurrentLinkedDeque<Long>()

    suspend fun acquire() {
        while (true) {
            val now = System.currentTimeMillis()
            // Prune expired timestamps
            while (timestamps.peekFirst()?.let { now - it > windowMs } == true) {
                timestamps.pollFirst()
            }
            if (timestamps.size < maxRequests) {
                timestamps.addLast(now)
                return
            }
            // Wait until the oldest request expires
            val oldest = timestamps.peekFirst() ?: return
            val waitMs = (oldest + windowMs) - now + 1
            if (waitMs > 0) delay(waitMs)
        }
    }
}
