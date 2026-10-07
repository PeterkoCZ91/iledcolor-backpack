package com.batoh.core.data.bluetooth

/**
 * Decides whether a failed first GATT connection is retried automatically.
 *
 * Android sometimes drops the very first connection with status 133 (GATT_ERROR) before
 * services are discovered; an immediate manual retry then works. The policy is deliberately
 * conservative: only link-establishment failures, only before the connection is Ready
 * (so never during an upload), and at most [maxRetries] times per user-initiated connect.
 *
 * Pure and Android-free so it can be unit tested on the JVM. Thread-safe because GATT
 * callbacks arrive on a binder thread while connect()/disconnect() run on the main thread.
 */
class GattRetryPolicy(
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
    val retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS,
    private val retryableStatuses: Set<Int> = DEFAULT_RETRYABLE_STATUSES,
) {
    /** Where the connection was when it dropped. */
    enum class Phase {
        /** No connection attempt in progress (idle or after a user disconnect). */
        IDLE,
        /** connectGatt() issued, link not yet up. */
        CONNECTING,
        /** Link up; service discovery, notification setup and MTU request in progress. */
        DISCOVERING,
        /** "Ready" was reported; uploads and commands may be running. Never retried. */
        READY,
    }

    /** A scheduled retry; valid only while [token] matches the policy's current generation. */
    data class Retry(val attempt: Int, val maxRetries: Int, val delayMs: Long, val token: Long)

    private var attempts = 0
    private var generation = 0L

    /** A new user-initiated connection starts with a fresh retry budget. */
    @Synchronized
    fun reset() {
        attempts = 0
        generation++
    }

    /**
     * Called on an unexpected disconnect. Returns the retry to schedule, or null when the
     * failure must be surfaced as a normal disconnect.
     */
    @Synchronized
    fun onDisconnected(status: Int, phase: Phase, operationInFlight: Boolean = false): Retry? {
        if (status !in retryableStatuses) return null
        if (phase != Phase.CONNECTING && phase != Phase.DISCOVERING) return null
        if (operationInFlight) return null
        if (attempts >= maxRetries) return null
        attempts++
        return Retry(attempts, maxRetries, retryDelayMs, generation)
    }

    /** Invalidates any pending retry (user disconnect or a new manual connect). */
    @Synchronized
    fun cancel() {
        generation++
    }

    /** True if a retry scheduled with [token] may still run. */
    @Synchronized
    fun isCurrent(token: Long): Boolean = token == generation

    companion object {
        const val DEFAULT_MAX_RETRIES = 1
        const val DEFAULT_RETRY_DELAY_MS = 800L

        /** GATT_ERROR: Android's generic failure, typical for a flaky first connection. */
        const val STATUS_GATT_ERROR = 133
        /** GATT_CONN_FAIL_ESTABLISH: the link never came up, same class of failure as 133. */
        const val STATUS_CONN_FAIL_ESTABLISH = 62
        /**
         * GATT_CONN_TIMEOUT (supervision timeout) is intentionally NOT retried: it usually
         * means the backpack went out of range or was switched off, where a retry only delays
         * the error the user needs to see.
         */
        const val STATUS_CONN_TIMEOUT = 8

        val DEFAULT_RETRYABLE_STATUSES = setOf(STATUS_GATT_ERROR, STATUS_CONN_FAIL_ESTABLISH)
    }
}
