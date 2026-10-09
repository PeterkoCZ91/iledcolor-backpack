package com.batoh.feature.home

import com.batoh.core.domain.model.Gif
import com.batoh.core.domain.model.LibraryEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRecentGifTest {
    private fun entry(id: String, added: Long) = LibraryEntry(
        gif = Gif(id = id, title = "t$id", thumbnailUrl = "th$id", originalUrl = "o$id", width = 64, height = 64),
        sizeBytes = 1,
        dateAddedSeconds = added
    )

    @Test
    fun newestFirstAndLimited() {
        val result = HomeRecentGif.recentOf(listOf(entry("a", 10), entry("b", 30), entry("c", 20)), limit = 2)
        assertEquals(listOf("b", "c"), result.map { it.id })
        assertEquals("ob", result.first().originalUrl)
    }

    @Test
    fun unknownDatesGoLast() {
        val result = HomeRecentGif.recentOf(listOf(entry("a", 0), entry("b", 5)))
        assertEquals(listOf("b", "a"), result.map { it.id })
    }
}
