package com.batoh.core.data.repository

import com.batoh.core.domain.model.GifCategory

/** Pure logic of stored categories: TTL shared with [GifCachePolicy] and emission selection. */
object CategoryCachePolicy {
    fun minValidCachedAt(now: Long): Long = GifCachePolicy.minValidCachedAt(now)

    fun isFresh(cachedAt: Long, now: Long): Boolean = GifCachePolicy.isFresh(cachedAt, now)

    /** First emitted list: the in-memory layer takes precedence over the stored one (Room). */
    fun initial(memory: List<GifCategory>?, stored: List<GifCategory>?): List<GifCategory>? =
        memory?.takeIf { it.isNotEmpty() } ?: stored?.takeIf { it.isNotEmpty() }

    /** Loading only when there is nothing to show. */
    fun shouldEmitLoading(initial: List<GifCategory>?): Boolean = initial == null

    /** A network error is reported only without a stored list (otherwise the last emission stays Success). */
    fun shouldEmitError(initial: List<GifCategory>?): Boolean = initial == null
}
