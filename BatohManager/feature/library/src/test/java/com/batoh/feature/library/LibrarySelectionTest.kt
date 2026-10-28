package com.batoh.feature.library

import com.batoh.core.domain.model.Gif
import com.batoh.core.domain.model.LibraryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySelectionTest {
    private fun gif(id: String) = Gif(id = id, title = id, thumbnailUrl = "t/$id", originalUrl = "content://x/$id",
        width = 64, height = 64)

    @Test fun toggleAddsInOrderAndRemoves() {
        var s = emptyList<String>()
        s = LibrarySelection.toggle(s, "b")
        s = LibrarySelection.toggle(s, "a")
        assertEquals(listOf("b", "a"), s)
        s = LibrarySelection.toggle(s, "b")
        assertEquals(listOf("a"), s)
    }

    @Test fun toggleRespectsMaximum() {
        val full = listOf("1", "2")
        assertEquals(full, LibrarySelection.toggle(full, "3", max = 2))
        assertEquals(listOf("2"), LibrarySelection.toggle(full, "1", max = 2))
    }

    @Test fun pruneDropsMissingKeepingOrder() {
        assertEquals(listOf("c", "a"), LibrarySelection.prune(listOf("c", "x", "a"), setOf("a", "b", "c")))
    }

    @Test fun needsTwoToChain() {
        assertFalse(LibrarySelection.canChain(listOf("a")))
        assertTrue(LibrarySelection.canChain(listOf("a", "b")))
    }

    @Test fun urisFollowSelectionOrderAndSkipUnknown() {
        val gifs = listOf(gif("a"), gif("b"), gif("c"))
        assertEquals(listOf("content://x/c", "content://x/a"),
            LibrarySelection.uris(listOf("c", "gone", "a"), gifs))
    }

    @Test fun cannotChainMoreThanMaximum() {
        assertTrue(LibrarySelection.canChain((1..LibrarySelection.MAX_TO_CHAIN).map { "$it" }))
        assertFalse(LibrarySelection.canChain((1..LibrarySelection.MAX_TO_CHAIN + 1).map { "$it" }))
    }

    @Test fun selectAllKeepsOrderAndAppendsMissing() {
        assertEquals(listOf("c", "a", "b"), LibrarySelection.selectAll(listOf("c", "a"), listOf("a", "b", "c")))
        assertEquals(listOf("a", "b"), LibrarySelection.selectAll(emptyList(), listOf("a", "b")))
    }

    @Test fun allSelectedNeedsNonEmptyShownList() {
        assertFalse(LibrarySelection.allSelected(listOf("a"), emptyList()))
        assertFalse(LibrarySelection.allSelected(listOf("a"), listOf("a", "b")))
        assertTrue(LibrarySelection.allSelected(listOf("b", "a", "x"), listOf("a", "b")))
    }

    @Test fun resolveSkipsMissingAndKeepsSelectionOrder() {
        val gifs = listOf(gif("a"), gif("b"), gif("c"))
        assertEquals(listOf("c", "a"), LibrarySelection.resolve(listOf("c", "gone", "a"), gifs).map { it.id })
    }

    @Test fun totalBytesIsNullWhenASelectedGifIsNotShown() {
        val entries = listOf(LibraryEntry(gif("a"), 100, 0), LibraryEntry(gif("b"), 50, 0))
        assertEquals(150L, LibrarySelection.totalBytes(listOf("a", "b"), entries))
        assertEquals(null, LibrarySelection.totalBytes(listOf("a", "hidden"), entries))
        assertEquals(0L, LibrarySelection.totalBytes(emptyList(), entries))
    }
}
