package com.batoh.feature.library

import androidx.lifecycle.SavedStateHandle
import com.batoh.core.domain.model.Gif
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingRenameStoreTest {
    private val gif = Gif("1", "t", "th", "content://x/1", "", 64, 64, "local")

    @Test fun restoredAfterProcessDeath() {
        val saved = mutableMapOf<String, Any?>()
        val first = SavedStateHandle()
        PendingRenameStore(first).save(gif, "new")
        first.keys().forEach { saved[it] = first.get<Any>(it) }
        val restored = PendingRenameStore(SavedStateHandle(saved))
        assertEquals(gif to "new", restored.take())
    }

    @Test fun takeClearsPending() {
        val store = PendingRenameStore(SavedStateHandle())
        store.save(gif, "n")
        store.take()
        assertNull(store.peek())
        assertNull(store.take())
    }

    @Test fun emptyWhenNothingSaved() = assertNull(PendingRenameStore(SavedStateHandle()).peek())
}
