package com.batoh.core.data.repository

import com.batoh.core.domain.model.GifCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryCachePolicyTest {
    private val mem = listOf(GifCategory("a", "a"))
    private val db = listOf(GifCategory("b", "b"))

    @Test
    fun ttlReusesGifCachePolicy() {
        val now = 10_000_000_000L
        assertEquals(now - GifCachePolicy.TTL_MS, CategoryCachePolicy.minValidCachedAt(now))
        assertTrue(CategoryCachePolicy.isFresh(now - GifCachePolicy.TTL_MS, now))
        assertFalse(CategoryCachePolicy.isFresh(now - GifCachePolicy.TTL_MS - 1, now))
        assertEquals(7L * 24 * 60 * 60 * 1000, GifCachePolicy.TTL_MS)
    }

    @Test
    fun memoryWinsThenStoredThenNone() {
        assertSame(mem, CategoryCachePolicy.initial(mem, db))
        assertSame(db, CategoryCachePolicy.initial(null, db))
        assertSame(db, CategoryCachePolicy.initial(emptyList(), db))
        assertNull(CategoryCachePolicy.initial(null, null))
        assertNull(CategoryCachePolicy.initial(null, emptyList()))
    }

    @Test
    fun loadingAndErrorOnlyWithoutInitial() {
        assertTrue(CategoryCachePolicy.shouldEmitLoading(null))
        assertTrue(CategoryCachePolicy.shouldEmitError(null))
        assertFalse(CategoryCachePolicy.shouldEmitLoading(db))
        assertFalse(CategoryCachePolicy.shouldEmitError(db))
    }
}
