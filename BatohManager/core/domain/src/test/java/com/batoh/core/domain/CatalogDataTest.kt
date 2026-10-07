package com.batoh.core.domain

import com.batoh.core.domain.model.AspectRatio
import com.batoh.core.domain.model.ContentSafety
import com.batoh.core.domain.model.CuratedCategories
import com.batoh.core.domain.model.GifFilter
import com.batoh.core.domain.model.GifSource
import com.batoh.core.domain.model.GifType
import com.batoh.core.domain.model.PredefinedInterests
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogDataTest {
    @Test fun curatedCategoriesHaveUniqueNonBlankNamesAndQueries() {
        val list = CuratedCategories.list
        assertTrue(list.isNotEmpty())
        assertTrue(list.all { it.name.isNotBlank() && it.nameEncoded.isNotBlank() })
        assertEquals(list.size, list.map { it.name }.toSet().size)
        assertEquals(list.size, list.map { it.nameEncoded }.toSet().size)
        assertTrue(list.all { it.previewGif == null })
    }

    @Test fun interestsAreWellFormedAndGroupedWithoutLoss() {
        val all = PredefinedInterests.all
        assertTrue(all.all { it.name.isNotBlank() && it.searchQuery.isNotBlank() && it.group.isNotBlank() })
        assertEquals(all.size, all.map { it.name }.toSet().size)
        assertEquals(all.size, PredefinedInterests.groups.values.sumOf { it.size })
        PredefinedInterests.groups.forEach { (group, tags) -> assertTrue(tags.all { it.group == group }) }
    }

    @Test fun defaultFilterIsSafeSquareGiphyGif() {
        val filter = GifFilter()
        assertEquals("", filter.query)
        assertEquals(GifSource.GIPHY, filter.source)
        assertEquals(GifType.GIF, filter.type)
        assertEquals(ContentSafety.FAMILY, filter.safety)
        assertEquals(AspectRatio.SQUARE, filter.aspectRatio)
    }

    @Test fun safetyLevelsMapToProviderValues() {
        assertEquals("g", ContentSafety.FAMILY.giphyValue)
        assertEquals("low", ContentSafety.FAMILY.klipyValue)
        assertEquals("pg-13", ContentSafety.TEEN.giphyValue)
        assertEquals("r", ContentSafety.ADULT.giphyValue)
        assertEquals("off", ContentSafety.ADULT.klipyValue)
        assertEquals("gifs", GifType.GIF.value)
        assertEquals("stickers", GifType.STICKER.value)
    }
}
