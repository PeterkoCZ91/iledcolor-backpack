package com.batoh.core.data.remote.model

import com.batoh.core.domain.model.Gif
import com.batoh.core.domain.model.GifCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryMapperTest {
    @Test
    fun roundTripKeepsBaseFields() {
        val c = GifCategory("Cats", "cats", Gif("i", "t", "th", "or", "mp4", 3, 4, "giphy"))
        val e = c.toCachedEntity(5, 99L)
        assertEquals(5, e.position)
        assertEquals(99L, e.cachedAt)
        assertEquals(c, e.toDomain())
    }

    @Test
    fun missingPreviewStaysNull() {
        assertNull(GifCategory("x", "x", null).toCachedEntity(0, 1L).toDomain().previewGif)
    }
}
