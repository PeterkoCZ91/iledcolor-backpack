package com.batoh.core.data.bluetooth

import java.net.URLDecoder
import java.net.URLEncoder

/** How an upload ended. Only [Confirmed] and [AlreadyPresent] come from an answer of the backpack. */
enum class UploadOutcome {
    /** End frame echoed with status 1: the backpack confirmed the stored programme. */
    Confirmed,
    /** Cmd 06 status 3: the backpack already had this file ID, no data was sent. */
    AlreadyPresent,
    Failed,
    Cancelled,
}

data class UploadHistoryEntry(val name: String, val timeMillis: Long, val outcome: UploadOutcome)

/**
 * Last uploads kept in SharedPreferences as plain text (one entry per line,
 * `time<TAB>outcome<TAB>url-encoded name`), newest first. Pure JVM so it is unit-testable.
 */
object UploadHistory {
    const val MAX_ENTRIES = 10

    fun add(history: List<UploadHistoryEntry>, entry: UploadHistoryEntry, max: Int = MAX_ENTRIES): List<UploadHistoryEntry> =
        (listOf(entry) + history).take(max)

    fun encode(history: List<UploadHistoryEntry>): String = history.joinToString("\n") {
        "${it.timeMillis}\t${it.outcome.name}\t${URLEncoder.encode(it.name, "UTF-8")}"
    }

    /** Skips malformed lines instead of losing the whole history. */
    fun decode(text: String?): List<UploadHistoryEntry> {
        if (text.isNullOrEmpty()) return emptyList()
        return text.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 3) return@mapNotNull null
            val time = parts[0].toLongOrNull() ?: return@mapNotNull null
            val outcome = UploadOutcome.values().firstOrNull { it.name == parts[1] } ?: return@mapNotNull null
            val name = runCatching { URLDecoder.decode(parts[2], "UTF-8") }.getOrNull() ?: return@mapNotNull null
            UploadHistoryEntry(name, time, outcome)
        }.take(MAX_ENTRIES).toList()
    }
}
