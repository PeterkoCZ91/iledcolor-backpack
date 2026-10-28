package com.batoh.feature.backpack

import com.batoh.core.conversion.TextBannerOptions
import com.batoh.core.conversion.TextBannerSize
import com.batoh.core.conversion.TextBannerSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreparedTextsTest {
    private fun item(id: Long, text: String = "text $id") = PreparedText(id, TextBannerOptions(text = text))

    @Test
    fun roundTrip() {
        val list = listOf(
            PreparedText(2, TextBannerOptions("Ahoj\tsvět\nřádek 100 % & ž", 0xFFFF0000.toInt(), 0xFF0000FF.toInt(),
                TextBannerSpeed.Fast, TextBannerSize.Large, false)),
            item(1)
        )
        assertEquals(list, PreparedTexts.decode(PreparedTexts.encode(list)))
    }

    @Test
    fun add_newestFirstAndCapped() {
        var list = emptyList<PreparedText>()
        for (i in 1L..(PreparedTexts.MAX + 5)) list = PreparedTexts.add(list, item(i))
        assertEquals(PreparedTexts.MAX, list.size)
        assertEquals(PreparedTexts.MAX + 5L, list.first().id)
        assertEquals(6L, list.last().id)
    }

    @Test
    fun add_ignoresBlank() {
        assertTrue(PreparedTexts.add(emptyList(), item(1, "   ")).isEmpty())
    }

    @Test
    fun add_dedupes() {
        val list = PreparedTexts.add(listOf(item(1, "same")), item(2, "same"))
        assertEquals(listOf(item(1, "same")), list)
    }

    @Test
    fun decode_skipsCorruptEntries() {
        val good = item(7)
        val raw = "garbage\n" + PreparedTexts.encode(listOf(good)) + "\nx\t1\t2\tNOPE\tMedium\ttrue\tabc\n1\t2\t3\tNormal\tMedium\ttrue\t%ZZ"
        assertEquals(listOf(good), PreparedTexts.decode(raw))
        assertTrue(PreparedTexts.decode(null).isEmpty())
        assertTrue(PreparedTexts.decode("").isEmpty())
    }

    @Test
    fun remove_byId() {
        val list = listOf(item(3), item(2), item(1))
        assertEquals(listOf(item(3), item(1)), PreparedTexts.remove(list, 2))
        assertEquals(list, PreparedTexts.remove(list, 99))
    }
}
