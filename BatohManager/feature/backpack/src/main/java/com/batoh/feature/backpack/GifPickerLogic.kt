package com.batoh.feature.backpack

import com.batoh.core.domain.model.Gif
import java.text.Normalizer
import kotlin.math.abs

/** Pure search and shape helpers for [BackpackGifPicker] (JVM unit-tested). */
internal object GifPickerLogic {
    private val combiningMarks = Regex("\\p{Mn}+")
    private val whitespace = Regex("\\s+")

    /** Lower-cases and strips diacritics so "zluty" finds "Žlutý". */
    fun normalize(text: String): String =
        combiningMarks.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase().trim()

    /**
     * Every word of [query] must appear in the title (diacritics and case ignored); a blank query
     * keeps the list as it is, so the caller's newest-first order is preserved.
     */
    fun filter(gifs: List<Gif>, query: String): List<Gif> {
        val words = normalize(query).split(whitespace).filter { it.isNotEmpty() }
        if (words.isEmpty()) return gifs
        return gifs.filter { gif ->
            val title = normalize(gif.title)
            words.all { title.contains(it) }
        }
    }

    /**
     * True when the stored dimensions are known and clearly not square (more than 5 % apart), so the
     * panel will rescale the picture. Unknown dimensions (0) never show the hint.
     */
    fun isNotSquare(width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        return abs(width - height).toFloat() / maxOf(width, height) > 0.05f
    }
}
