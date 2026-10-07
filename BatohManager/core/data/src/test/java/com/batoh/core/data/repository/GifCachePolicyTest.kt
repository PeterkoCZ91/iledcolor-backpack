package com.batoh.core.data.repository

import com.batoh.core.domain.model.ContentSafety
import com.batoh.core.domain.model.GifFilter
import com.batoh.core.domain.model.GifType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GifCachePolicyTest {
    private val base = GifFilter()

    @Test fun searchKeyDiffersByType() {
        assertNotEquals(
            GifCachePolicy.giphySearchKey("cat", base.copy(type = GifType.GIF)),
            GifCachePolicy.giphySearchKey("cat", base.copy(type = GifType.STICKER))
        )
    }

    @Test fun searchKeyDiffersBySafety() {
        assertNotEquals(
            GifCachePolicy.giphySearchKey("cat", base.copy(safety = ContentSafety.FAMILY)),
            GifCachePolicy.giphySearchKey("cat", base.copy(safety = ContentSafety.ADULT))
        )
        assertNotEquals(
            GifCachePolicy.giphyTrendingKey(base.copy(safety = ContentSafety.FAMILY)),
            GifCachePolicy.giphyTrendingKey(base.copy(safety = ContentSafety.TEEN))
        )
    }

    @Test fun searchKeyDiffersByAspectRatio() {
        assertNotEquals(
            GifCachePolicy.giphyTrendingKey(base.copy(aspectRatio = null)),
            GifCachePolicy.giphyTrendingKey(base)
        )
    }

    @Test fun queryIsNormalized() {
        assertEquals(
            GifCachePolicy.giphySearchKey("Cat ", base),
            GifCachePolicy.giphySearchKey("cat", base)
        )
    }

    @Test fun trendingNeverCollidesWithSearchOrOtherProvider() {
        val keys = setOf(
            GifCachePolicy.giphySearchKey("trending", base),
            GifCachePolicy.giphyTrendingKey(base),
            GifCachePolicy.klipySearchKey("trending", base),
            GifCachePolicy.klipyTrendingKey(base)
        )
        assertEquals(4, keys.size)
    }

    @Test fun klipyKeyIgnoresGifType() {
        assertEquals(
            GifCachePolicy.klipyTrendingKey(base.copy(type = GifType.GIF)),
            GifCachePolicy.klipyTrendingKey(base.copy(type = GifType.STICKER))
        )
    }

    @Test fun ttlBoundary() {
        val now = 10_000_000_000L
        assertTrue(GifCachePolicy.isFresh(now, now))
        assertTrue(GifCachePolicy.isFresh(now - GifCachePolicy.TTL_MS, now))
        assertFalse(GifCachePolicy.isFresh(now - GifCachePolicy.TTL_MS - 1, now))
        assertEquals(now - 7L * 24 * 3600 * 1000, GifCachePolicy.minValidCachedAt(now))
    }
}
