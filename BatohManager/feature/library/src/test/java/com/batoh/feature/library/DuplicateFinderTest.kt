package com.batoh.feature.library

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class DuplicateFinderTest {
    private fun file(id: String, name: String, bytes: ByteArray) = DuplicateCandidate(id, name, bytes.size.toLong())

    private fun finder(content: Map<String, ByteArray>, broken: Set<String> = emptySet()) =
        DuplicateFinder { c ->
            when {
                c.id in broken -> throw IOException("unreadable")
                else -> content[c.id]?.let { ByteArrayInputStream(it) as InputStream }
            }
        }

    @Test fun equalContentDifferentNamesGrouped() = runBlocking {
        val a = byteArrayOf(1, 2, 3)
        val content = mapOf("1" to a, "2" to a.copyOf(), "3" to byteArrayOf(9, 9, 9))
        val groups = finder(content).find(listOf(file("2", "b.gif", a), file("1", "a.gif", a), file("3", "c.gif", content.getValue("3"))))
        assertEquals(1, groups.size)
        assertEquals(listOf("1", "2"), groups[0].files.map { it.id })
        assertEquals("1", groups[0].keep.id)
        assertEquals(listOf("2"), groups[0].extras.map { it.id })
    }

    @Test fun sameSizeDifferentContentNotGrouped() = runBlocking {
        val content = mapOf("1" to byteArrayOf(1, 2, 3), "2" to byteArrayOf(1, 2, 4))
        val groups = finder(content).find(content.map { (id, b) -> file(id, "$id.gif", b) })
        assertTrue(groups.isEmpty())
    }

    @Test fun differentSizeIsNeverOpened() = runBlocking {
        var opened = 0
        val finder = DuplicateFinder { opened++; ByteArrayInputStream(ByteArray(1)) }
        val groups = finder.find(listOf(DuplicateCandidate("1", "a.gif", 1), DuplicateCandidate("2", "b.gif", 2)))
        assertTrue(groups.isEmpty())
        assertEquals(0, opened)
    }

    @Test fun singleFileAndEmptyList() = runBlocking {
        assertTrue(finder(emptyMap()).find(emptyList()).isEmpty())
        assertTrue(finder(mapOf("1" to byteArrayOf(1))).find(listOf(file("1", "a.gif", byteArrayOf(1)))).isEmpty())
    }

    @Test fun unreadableFileIsSkipped() = runBlocking {
        val a = byteArrayOf(5, 6)
        val content = mapOf("1" to a, "2" to a, "3" to a)
        val groups = finder(content, broken = setOf("2")).find(content.map { (id, b) -> file(id, "$id.gif", b) })
        assertEquals(1, groups.size)
        assertEquals(listOf("1", "3"), groups[0].files.map { it.id })
    }

    @Test fun unreadableLeavingOneIsNotAGroup() = runBlocking {
        val a = byteArrayOf(5, 6)
        val content = mapOf("1" to a, "2" to a)
        val groups = finder(content, broken = setOf("2")).find(content.map { (id, b) -> file(id, "$id.gif", b) })
        assertTrue(groups.isEmpty())
    }

    @Test fun nullStreamIsSkippedAndEmptyFilesIgnored() = runBlocking {
        val groups = finder(emptyMap()).find(listOf(
            DuplicateCandidate("1", "a.gif", 2), DuplicateCandidate("2", "b.gif", 2),
            DuplicateCandidate("3", "e1.gif", 0), DuplicateCandidate("4", "e2.gif", 0)))
        assertTrue(groups.isEmpty())
    }
}
