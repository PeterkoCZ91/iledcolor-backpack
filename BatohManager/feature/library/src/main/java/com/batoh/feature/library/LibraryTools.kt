package com.batoh.feature.library

import com.batoh.core.domain.model.LibraryEntry
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** Collection sort orders; [prefValue] is persisted, so never rename existing values. */
enum class LibrarySort(val prefValue: String) {
    NEWEST("newest"),
    OLDEST("oldest"),
    NAME("name"),
    SIZE("size");

    companion object {
        val DEFAULT = NEWEST
        fun fromPref(value: String?): LibrarySort = values().firstOrNull { it.prefValue == value } ?: DEFAULT
    }
}

/** Numbers for the summary line: what is shown versus the whole collection. */
data class LibrarySummary(
    val shownCount: Int,
    val totalCount: Int,
    val shownBytes: Long,
    val totalBytes: Long
) {
    val filtered: Boolean get() = shownCount != totalCount
}

/** Pure filtering, sorting and summary logic for "My collection" (JVM unit-tested). */
object LibraryTools {
    private val combiningMarks = Regex("\\p{Mn}+")

    /** Lower-cases and strips diacritics so "zluty" finds "Žlutý". */
    fun normalizeForSearch(text: String, locale: Locale = Locale.ROOT): String =
        combiningMarks.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(locale).trim()

    /** Every whitespace-separated word of [query] must appear in the title; blank keeps everything. */
    fun filter(entries: List<LibraryEntry>, query: String): List<LibraryEntry> {
        val words = normalizeForSearch(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return entries
        return entries.filter { entry ->
            val title = normalizeForSearch(entry.gif.title)
            words.all { title.contains(it) }
        }
    }

    /**
     * Stable sort; ties fall back to newest first and then id so the grid does not jump.
     * Names compare with a locale collator (Czech "č" after "c", case-insensitive).
     */
    fun sort(entries: List<LibraryEntry>, sort: LibrarySort, locale: Locale = Locale.getDefault()): List<LibraryEntry> {
        val tieBreak = compareByDescending<LibraryEntry> { it.dateAddedSeconds }.thenBy { it.gif.id }
        val comparator: Comparator<LibraryEntry> = when (sort) {
            LibrarySort.NEWEST -> tieBreak
            LibrarySort.OLDEST -> compareBy<LibraryEntry> { it.dateAddedSeconds }.thenBy { it.gif.id }
            LibrarySort.NAME -> {
                val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
                Comparator<LibraryEntry> { a, b -> collator.compare(a.gif.title, b.gif.title) }.then(tieBreak)
            }
            LibrarySort.SIZE -> compareByDescending<LibraryEntry> { it.sizeBytes }.then(tieBreak)
        }
        return entries.sortedWith(comparator)
    }

    fun filterAndSort(entries: List<LibraryEntry>, query: String, sort: LibrarySort,
                      locale: Locale = Locale.getDefault()): List<LibraryEntry> =
        sort(filter(entries, query), sort, locale)

    fun summarize(shown: List<LibraryEntry>, all: List<LibraryEntry>): LibrarySummary = LibrarySummary(
        shownCount = shown.size,
        totalCount = all.size,
        shownBytes = shown.sumOf { it.sizeBytes.coerceAtLeast(0) },
        totalBytes = all.sumOf { it.sizeBytes.coerceAtLeast(0) }
    )
}
