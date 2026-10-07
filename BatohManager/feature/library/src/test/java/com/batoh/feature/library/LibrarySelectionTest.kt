package com.batoh.feature.library

import com.batoh.core.domain.model.Gif
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
}
