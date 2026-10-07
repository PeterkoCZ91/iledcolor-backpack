package com.batoh.core.data.repository

import com.batoh.core.domain.model.GifFilter
import com.batoh.core.domain.model.GifType

/** Čistá logika cache vyhledávání/trendů: klíče a TTL. */
object GifCachePolicy {
    const val TTL_MS: Long = 7L * 24 * 60 * 60 * 1000

    /** Nejstarší ještě platný čas uložení. */
    fun minValidCachedAt(now: Long): Long = now - TTL_MS

    fun isFresh(cachedAt: Long, now: Long): Boolean = cachedAt >= minValidCachedAt(now)

    private fun filterPart(filter: GifFilter, withType: Boolean): String {
        val type = if (withType) "${filter.type.name}_" else ""
        val aspect = filter.aspectRatio?.name ?: "ANY"
        return "$type${filter.safety.name}_$aspect"
    }

    private fun normalize(query: String) = query.trim().lowercase()

    fun giphySearchKey(query: String, filter: GifFilter): String =
        "giphy_search_${filterPart(filter, true)}_${normalize(query)}"

    fun giphyTrendingKey(filter: GifFilter): String =
        "giphy_trending_${filterPart(filter, true)}"

    // Klipy typ GIF/STICKER nepoužívá, takže ho do klíče nedáváme.
    fun klipySearchKey(query: String, filter: GifFilter): String =
        "klipy_search_${filterPart(filter, false)}_${normalize(query)}"

    fun klipyTrendingKey(filter: GifFilter): String =
        "klipy_trending_${filterPart(filter, false)}"
}
