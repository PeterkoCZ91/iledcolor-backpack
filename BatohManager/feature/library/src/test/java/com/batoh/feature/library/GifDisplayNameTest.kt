package com.batoh.feature.library

import com.batoh.core.domain.model.GifDisplayName
import com.batoh.core.domain.model.GifNameProblem
import com.batoh.core.domain.model.GifNameValidation
import org.junit.Assert.assertEquals
import org.junit.Test

class GifDisplayNameTest {
    private fun valid(input: String) = (GifDisplayName.validate(input) as GifNameValidation.Valid).fileName
    private fun problem(input: String) = (GifDisplayName.validate(input) as GifNameValidation.Invalid).problem

    @Test fun keepsGifExtensionAndTrims() {
        assertEquals("Kočka tančí.gif", valid("  Kočka tančí  "))
        assertEquals("cat.gif", valid("cat.GIF"))
        assertEquals("cat.gif", valid("cat.gif "))
        assertEquals("archive.gif.gif", valid("archive.gif.gif"))
        assertEquals("v1.2 final.gif", valid("v1.2 final"))
    }

    @Test fun baseNameDropsOnlyTrailingGif() {
        assertEquals("cat", GifDisplayName.baseName("cat.gif"))
        assertEquals("cat.png", GifDisplayName.baseName("cat.png"))
        assertEquals("cat", GifDisplayName.baseName("cat.GiF"))
    }

    @Test fun rejectsEmpty() {
        assertEquals(GifNameProblem.EMPTY, problem(""))
        assertEquals(GifNameProblem.EMPTY, problem("   "))
        assertEquals(GifNameProblem.EMPTY, problem(".gif"))
    }

    @Test fun rejectsForbiddenCharacters() {
        listOf("a/b", "a\\b", "a:b", "a*b", "a?b", "a\"b", "a<b", "a>b", "a|b", "a\u0000b", "a\nb").forEach {
            assertEquals(it, GifNameProblem.FORBIDDEN_CHARACTERS, problem(it))
        }
    }

    @Test fun rejectsLeadingDotAndDotNames() {
        assertEquals(GifNameProblem.LEADING_DOT, problem(".hidden"))
        assertEquals(GifNameProblem.LEADING_DOT, problem(".."))
    }

    @Test fun lengthLimitIsUtf8BytesIncludingExtension() {
        // ASCII: 251 + ".gif" = 255 bytes is the maximum.
        assertEquals("a".repeat(251) + ".gif", valid("a".repeat(251)))
        assertEquals(GifNameProblem.TOO_LONG, problem("a".repeat(252)))
        // Czech: 2 bytes per letter, 125 * 2 = 250 <= 251, 126 * 2 = 252 > 251.
        assertEquals("ž".repeat(125) + ".gif", valid("ž".repeat(125)))
        assertEquals(GifNameProblem.TOO_LONG, problem("ž".repeat(126)))
        // Emoji: 4 bytes each (surrogate pair), 62 * 4 = 248 fits, 63 * 4 = 252 does not.
        assertEquals("😀".repeat(62) + ".gif", valid("😀".repeat(62)))
        assertEquals(GifNameProblem.TOO_LONG, problem("😀".repeat(63)))
        // Mixed boundary: 247 ASCII + one emoji = 251 fits exactly; one more ASCII does not.
        assertEquals("a".repeat(247) + "😀.gif", valid("a".repeat(247) + "😀"))
        assertEquals(GifNameProblem.TOO_LONG, problem("a".repeat(248) + "😀"))
        // Final name never exceeds 255 UTF-8 bytes.
        assertEquals(255, valid("a".repeat(247) + "😀").toByteArray(Charsets.UTF_8).size)
    }

    @Test fun utf8LengthMatchesEncoder() {
        listOf("abc", "Příliš žluťoučký kůň", "😀a😀", "日本語").forEach {
            assertEquals(it, it.toByteArray(Charsets.UTF_8).size, GifDisplayName.utf8Length(it))
        }
    }
}
