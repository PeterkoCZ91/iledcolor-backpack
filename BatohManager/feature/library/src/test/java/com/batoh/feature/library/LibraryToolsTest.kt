package com.batoh.feature.library

import com.batoh.core.domain.model.Gif
import com.batoh.core.domain.model.LibraryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LibraryToolsTest {
    private fun entry(id: String, title: String, size: Long, date: Long) = LibraryEntry(
        gif = Gif(id = id, title = title, thumbnailUrl = "content://x/$id", originalUrl = "content://x/$id",
            width = 64, height = 64),
        sizeBytes = size,
        dateAddedSeconds = date
    )

    private val cat = entry("1", "Kočka_64x64.gif", 300, 100)
    private val dog = entry("2", "dog dance.gif", 1000, 300)
    private val cloud = entry("3", "Cloud.gif", 50, 200)
    private val all = listOf(cat, dog, cloud)

    private fun ids(list: List<LibraryEntry>) = list.map { it.gif.id }

    @Test fun blankQueryKeepsEverything() {
        assertEquals(all, LibraryTools.filter(all, "   "))
    }

    @Test fun filterIgnoresCaseAndDiacritics() {
        assertEquals(listOf("1"), ids(LibraryTools.filter(all, "KOCKA")))
        assertEquals(listOf("1"), ids(LibraryTools.filter(all, "kočk")))
    }

    @Test fun everyQueryWordMustMatch() {
        assertEquals(listOf("2"), ids(LibraryTools.filter(all, "dance dog")))
        assertTrue(LibraryTools.filter(all, "dog cat").isEmpty())
    }

    @Test fun sortNewestAndOldest() {
        assertEquals(listOf("2", "3", "1"), ids(LibraryTools.sort(all, LibrarySort.NEWEST)))
        assertEquals(listOf("1", "3", "2"), ids(LibraryTools.sort(all, LibrarySort.OLDEST)))
    }

    @Test fun sortBySizeLargestFirst() {
        assertEquals(listOf("2", "1", "3"), ids(LibraryTools.sort(all, LibrarySort.SIZE)))
    }

    @Test fun sortByNameIsCaseInsensitiveAndUsesCzechCollation() {
        val czech = Locale.forLanguageTag("cs-CZ")
        // Czech: c < č < d; upper/lower case does not decide the order.
        val stork = entry("5", "Čáp.gif", 1, 1)
        assertEquals(listOf("3", "5", "2", "1"), ids(LibraryTools.sort(all + stork, LibrarySort.NAME, czech)))
        val clock = entry("4", "clock.gif", 1, 1)
        assertEquals(listOf("4", "3"), ids(LibraryTools.sort(listOf(cloud, clock), LibrarySort.NAME, czech)))
    }

    @Test fun ties_fallBackToNewestThenId() {
        val a = entry("a", "same.gif", 10, 5)
        val b = entry("b", "same.gif", 10, 9)
        val c = entry("c", "same.gif", 10, 9)
        assertEquals(listOf("b", "c", "a"), ids(LibraryTools.sort(listOf(a, c, b), LibrarySort.SIZE)))
        assertEquals(listOf("b", "c", "a"), ids(LibraryTools.sort(listOf(c, a, b), LibrarySort.NAME, Locale.ENGLISH)))
    }

    @Test fun filterAndSortCombines() {
        // "d" matches "dog dance" and "Cloud", not "Kočka".
        assertEquals(listOf("3", "2"), ids(LibraryTools.filterAndSort(all, "d", LibrarySort.OLDEST)))
    }

    @Test fun summaryCountsShownAndTotal() {
        val shown = LibraryTools.filter(all, "dog")
        val summary = LibraryTools.summarize(shown, all)
        assertEquals(1, summary.shownCount)
        assertEquals(3, summary.totalCount)
        assertEquals(1000L, summary.shownBytes)
        assertEquals(1350L, summary.totalBytes)
        assertTrue(summary.filtered)
        assertFalse(LibraryTools.summarize(all, all).filtered)
    }

    @Test fun summaryIgnoresUnknownNegativeSizes() {
        val broken = entry("9", "x.gif", -1, 0)
        assertEquals(300L, LibraryTools.summarize(listOf(cat, broken), listOf(cat, broken)).totalBytes)
    }

    @Test fun sortPreferenceRoundTripsAndFallsBack() {
        LibrarySort.values().forEach { assertEquals(it, LibrarySort.fromPref(it.prefValue)) }
        assertEquals(LibrarySort.NEWEST, LibrarySort.fromPref(null))
        assertEquals(LibrarySort.NEWEST, LibrarySort.fromPref("bogus"))
    }
}
