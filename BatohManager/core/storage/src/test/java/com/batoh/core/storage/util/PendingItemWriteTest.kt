package com.batoh.core.storage.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingItemWriteTest {
    /** In-memory stand-in for MediaStore: rows are created pending and must be published or deleted. */
    private class FakeMediaStore(private val failInsert: Boolean = false) : PendingItemSink<Int> {
        val rows = linkedMapOf<Int, ByteArrayOutputStream>()
        val published = mutableSetOf<Int>()
        private var nextId = 1
        override fun insert(): Int? = if (failInsert) null else nextId++.also { rows[it] = ByteArrayOutputStream() }
        override fun openOutput(item: Int): OutputStreamWrapper = OutputStreamWrapper(rows.getValue(item))
        override fun publish(item: Int) { published += item }
        override fun delete(item: Int) { rows.remove(item); published.remove(item) }
    }

    private class OutputStreamWrapper(private val target: ByteArrayOutputStream) : java.io.OutputStream() {
        override fun write(b: Int) = target.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = target.write(b, off, len)
    }

    private class BreaksAfter(private val bytes: Int, private val failure: Exception) : InputStream() {
        private var served = 0
        override fun read(): Int = throw UnsupportedOperationException()
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (served >= bytes) throw failure
            val count = minOf(len, bytes - served)
            served += count
            return count
        }
    }

    @Test
    fun publishesCompleteItem() {
        val store = FakeMediaStore()
        val bytes = ByteArray(10_000) { it.toByte() }

        val item = ByteArrayInputStream(bytes).writeToPendingItem(store, {}, bufferSize = 1024)

        assertTrue(item in store.published)
        assertArrayEquals(bytes, store.rows.getValue(item).toByteArray())
    }

    @Test
    fun errorMidCopyRemovesThePartialItem() {
        val store = FakeMediaStore()
        val failure = IllegalStateException("provider died")

        val thrown = assertThrows(IllegalStateException::class.java) {
            BreaksAfter(4096, failure).writeToPendingItem(store, {}, bufferSize = 1024)
        }

        assertSame(failure, thrown)
        assertTrue("partial row must be deleted", store.rows.isEmpty())
        assertTrue(store.published.isEmpty())
    }

    @Test
    fun cancellationMidCopyRemovesThePartialItem() {
        val store = FakeMediaStore()
        val cancellation = CancellationException("cancel")
        var checks = 0

        assertThrows(CancellationException::class.java) {
            ByteArrayInputStream(ByteArray(8192)).writeToPendingItem(
                store,
                ensureActive = { if (++checks == 3) throw cancellation },
                bufferSize = 1024
            )
        }

        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun failedInsertIsReportedWithoutLeavingRows() {
        val store = FakeMediaStore(failInsert = true)
        assertThrows(IOException::class.java) {
            ByteArrayInputStream(ByteArray(1)).writeToPendingItem(store, {})
        }
        assertEquals(0, store.rows.size)
    }
}
