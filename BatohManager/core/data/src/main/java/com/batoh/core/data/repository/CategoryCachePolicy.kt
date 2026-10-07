package com.batoh.core.data.repository

import com.batoh.core.domain.model.GifCategory

/** Čistá logika uložených kategorií: TTL sdílené s [GifCachePolicy] a výběr emisí. */
object CategoryCachePolicy {
    fun minValidCachedAt(now: Long): Long = GifCachePolicy.minValidCachedAt(now)

    fun isFresh(cachedAt: Long, now: Long): Boolean = GifCachePolicy.isFresh(cachedAt, now)

    /** První emitovaný seznam: in-memory vrstva má přednost před uloženým (Room). */
    fun initial(memory: List<GifCategory>?, stored: List<GifCategory>?): List<GifCategory>? =
        memory?.takeIf { it.isNotEmpty() } ?: stored?.takeIf { it.isNotEmpty() }

    /** Loading jen když není co ukázat. */
    fun shouldEmitLoading(initial: List<GifCategory>?): Boolean = initial == null

    /** Chyba sítě se hlásí jen bez uloženého seznamu (jinak poslední emise zůstane Success). */
    fun shouldEmitError(initial: List<GifCategory>?): Boolean = initial == null
}
