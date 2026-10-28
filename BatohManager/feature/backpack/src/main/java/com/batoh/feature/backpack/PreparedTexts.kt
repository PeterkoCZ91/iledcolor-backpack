package com.batoh.feature.backpack

import com.batoh.core.conversion.TextBannerOptions
import com.batoh.core.conversion.TextBannerSize
import com.batoh.core.conversion.TextBannerSpeed
import java.net.URLDecoder
import java.net.URLEncoder

/** A saved scrolling-text banner; rendered at the connected panel's size only when it is sent. */
data class PreparedText(val id: Long, val options: TextBannerOptions)

/**
 * Prepared texts kept in SharedPreferences as plain text (one entry per line,
 * `id<TAB>textColor<TAB>backgroundColor<TAB>speed<TAB>size<TAB>bold<TAB>url-encoded text`), newest first.
 * Pure JVM so it is unit-testable.
 */
object PreparedTexts {
    const val MAX = 30

    /** Newest first; blank text and an entry with identical options are ignored; the oldest drop over [MAX]. */
    fun add(list: List<PreparedText>, item: PreparedText): List<PreparedText> {
        if (item.options.text.isBlank() || list.any { it.options == item.options }) return list
        return (listOf(item) + list).take(MAX)
    }

    fun remove(list: List<PreparedText>, id: Long): List<PreparedText> = list.filterNot { it.id == id }

    fun encode(list: List<PreparedText>): String = list.joinToString("\n") {
        val o = it.options
        listOf(it.id, o.textColor, o.backgroundColor, o.speed.name, o.size.name, o.bold,
            URLEncoder.encode(o.text, "UTF-8")).joinToString("\t")
    }

    /** Skips malformed lines instead of losing the whole list. */
    fun decode(raw: String?): List<PreparedText> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size != 7) return@mapNotNull null
            val id = p[0].toLongOrNull() ?: return@mapNotNull null
            val textColor = p[1].toIntOrNull() ?: return@mapNotNull null
            val background = p[2].toIntOrNull() ?: return@mapNotNull null
            val speed = TextBannerSpeed.entries.firstOrNull { it.name == p[3] } ?: return@mapNotNull null
            val size = TextBannerSize.entries.firstOrNull { it.name == p[4] } ?: return@mapNotNull null
            val bold = p[5].toBooleanStrictOrNull() ?: return@mapNotNull null
            val text = runCatching { URLDecoder.decode(p[6], "UTF-8") }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            PreparedText(id, TextBannerOptions(text, textColor, background, speed, size, bold))
        }.take(MAX).toList()
    }
}
