package com.batoh.feature.backpack

import com.batoh.core.domain.model.Gif
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GifPickerLogicTest {
    private fun gif(id: String, title: String) =
        Gif(id = id, title = title, thumbnailUrl = "", originalUrl = "", width = 64, height = 64)

    private val gifs = listOf(gif("1", "Žlutý kocour"), gif("2", "Blue Cat"), gif("3", "Zelený pes"))

    @Test fun blankQueryKeepsOrder() {
        assertEquals(listOf("1", "2", "3"), GifPickerLogic.filter(gifs, "   ").map { it.id })
    }

    @Test fun diacriticsAndCaseAreIgnored() {
        assertEquals(listOf("1"), GifPickerLogic.filter(gifs, "ZLUTY").map { it.id })
        assertEquals(listOf("1"), GifPickerLogic.filter(gifs, "žlutý").map { it.id })
    }

    @Test fun allWordsMustMatchInAnyOrder() {
        assertEquals(listOf("2"), GifPickerLogic.filter(gifs, "cat blue").map { it.id })
        assertTrue(GifPickerLogic.filter(gifs, "cat pes").isEmpty())
    }

    @Test fun noMatchGivesEmptyList() {
        assertTrue(GifPickerLogic.filter(gifs, "xyz").isEmpty())
    }

    @Test fun squareAndUnknownSizesShowNoHint() {
        assertFalse(GifPickerLogic.isNotSquare(64, 64))
        assertFalse(GifPickerLogic.isNotSquare(100, 103))
        assertFalse(GifPickerLogic.isNotSquare(0, 0))
        assertFalse(GifPickerLogic.isNotSquare(480, 0))
    }

    @Test fun clearlyNonSquareShowsHint() {
        assertTrue(GifPickerLogic.isNotSquare(480, 270))
        assertTrue(GifPickerLogic.isNotSquare(270, 480))
    }
}
