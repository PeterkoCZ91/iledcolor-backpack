package com.batoh.core.data.repository

import com.batoh.core.domain.model.GifFilter
import com.batoh.core.domain.model.GifType

/** Pure logic of the search/trending cache: keys and TTL. */
object GifCachePolicy {
    const val TTL_MS: Long = 7L * 24 * 60 * 60 * 1000

    /** Oldest still-valid storage time. */
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

    // Klipy does not use the GIF/STICKER type, so we do not put it in the key.
    fun klipySearchKey(query: String, filter: GifFilter): String =
        "klipy_search_${filterPart(filter, false)}_${normalize(query)}"

    fun klipyTrendingKey(filter: GifFilter): String =
        "klipy_trending_${filterPart(filter, false)}"
}
