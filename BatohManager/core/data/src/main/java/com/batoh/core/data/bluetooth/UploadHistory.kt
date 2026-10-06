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

/**
 * Typed reason why an upload did not complete. Shown in the UI (localised in the feature module)
 * and persisted in [UploadHistory] via the stable [code] string (`name` or `name:arg:arg`).
 * Chunk indexes are the 0-based protocol indexes used in the BLE log.
 */
sealed class UploadFailure(val code: String) {
    /** The backpack could not be connected (or initialised) in time. */
    object NotConnected : UploadFailure("not_connected")
    /** The BLE link dropped while the upload was running. */
    object ConnectionLost : UploadFailure("connection_lost")
    /** The JieLi authentication on AE01/AE02 got no or a too short answer. */
    object AuthFailed : UploadFailure("auth_failed")
    /** Cmd 06 (upload header) got no answer in time. */
    object HeaderTimeout : UploadFailure("header_timeout")
    /** Cmd 06 answered with an error status ([status] = -1 when the answer was malformed). */
    data class HeaderRejected(val status: Int) : UploadFailure("header_rejected:$status")
    /** Cmd 06 status 2. */
    object InsufficientSpace : UploadFailure("insufficient_space")
    /** Android refused or failed to write data chunk [index]. */
    data class ChunkWriteFailed(val index: Int, val total: Int) : UploadFailure("chunk_write_failed:$index:$total")
    /** Chunk [index] got an ACK with a status other than 1. */
    data class ChunkRejected(val index: Int, val total: Int, val status: Int) :
        UploadFailure("chunk_rejected:$index:$total:$status")
    /** No ACK for chunk [index] in time. */
    data class ChunkTimeout(val index: Int, val total: Int) : UploadFailure("chunk_timeout:$index:$total")
    /** The end-of-transfer frame got no echo in time. */
    object EndTimeout : UploadFailure("end_timeout")
    /** The end-of-transfer echo carried an error status ([status] = -1 when malformed). */
    data class EndRejected(val status: Int) : UploadFailure("end_rejected:$status")
    /** The negotiated MTU leaves too little room for a data chunk. */
    data class MtuTooSmall(val mtu: Int) : UploadFailure("mtu_too_small:$mtu")
    /** The GIF is larger than the app accepts. */
    object GifTooLarge : UploadFailure("gif_too_large")
    /** The selected file could not be opened or read (e.g. the URI grant is gone). */
    object GifUnreadable : UploadFailure("gif_unreadable")
    /** The selected file is not a GIF. */
    object NotAGif : UploadFailure("not_a_gif")
    /** The GIF is damaged, unsupported, or could not be converted to 64×64. */
    object GifInvalid : UploadFailure("gif_invalid")
    /** The programme payload itself is invalid (too short, failed to build). */
    object PayloadInvalid : UploadFailure("payload_invalid")
    /** The user cancelled the upload. */
    object Cancelled : UploadFailure("cancelled")
    /** Anything not covered above, including codes written by a newer app version. */
    object Unknown : UploadFailure("unknown")

    override fun toString(): String = code

    companion object {
        private val simple: Map<String, UploadFailure> by lazy {
            listOf(NotConnected, ConnectionLost, AuthFailed, HeaderTimeout, InsufficientSpace, EndTimeout,
                GifTooLarge, GifUnreadable, NotAGif, GifInvalid, PayloadInvalid, Cancelled, Unknown)
                .associateBy { it.code }
        }

        /** Parses a [code]; unknown or malformed codes become [Unknown], never null. */
        fun fromCode(code: String): UploadFailure {
            simple[code]?.let { return it }
            val parts = code.split(':')
            val args = parts.drop(1).map { it.toIntOrNull() ?: return Unknown }
            return when {
                parts[0] == "header_rejected" && args.size == 1 -> HeaderRejected(args[0])
                parts[0] == "chunk_write_failed" && args.size == 2 -> ChunkWriteFailed(args[0], args[1])
                parts[0] == "chunk_rejected" && args.size == 3 -> ChunkRejected(args[0], args[1], args[2])
                parts[0] == "chunk_timeout" && args.size == 2 -> ChunkTimeout(args[0], args[1])
                parts[0] == "end_rejected" && args.size == 1 -> EndRejected(args[0])
                parts[0] == "mtu_too_small" && args.size == 1 -> MtuTooSmall(args[0])
                else -> Unknown
            }
        }
    }
}

data class UploadHistoryEntry(
    val name: String,
    val timeMillis: Long,
    val outcome: UploadOutcome,
    /** Why a [UploadOutcome.Failed]/[UploadOutcome.Cancelled] upload ended; null for successes and old entries. */
    val failure: UploadFailure? = null,
)

/**
 * Last uploads kept in SharedPreferences as plain text (one entry per line,
 * `time<TAB>outcome<TAB>url-encoded name[<TAB>failure code]`), newest first. The optional
 * fourth field was added later; lines without it still decode. Pure JVM so it is unit-testable.
 */
object UploadHistory {
    const val MAX_ENTRIES = 10

    fun add(history: List<UploadHistoryEntry>, entry: UploadHistoryEntry, max: Int = MAX_ENTRIES): List<UploadHistoryEntry> =
        (listOf(entry) + history).take(max)

    fun encode(history: List<UploadHistoryEntry>): String = history.joinToString("\n") {
        val base = "${it.timeMillis}\t${it.outcome.name}\t${URLEncoder.encode(it.name, "UTF-8")}"
        if (it.failure == null) base else "$base\t${it.failure.code}"
    }

    /** Skips malformed lines instead of losing the whole history. */
    fun decode(text: String?): List<UploadHistoryEntry> {
        if (text.isNullOrEmpty()) return emptyList()
        return text.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 3 && parts.size != 4) return@mapNotNull null
            val time = parts[0].toLongOrNull() ?: return@mapNotNull null
            val outcome = UploadOutcome.values().firstOrNull { it.name == parts[1] } ?: return@mapNotNull null
            val name = runCatching { URLDecoder.decode(parts[2], "UTF-8") }.getOrNull() ?: return@mapNotNull null
            val failure = parts.getOrNull(3)?.takeIf { it.isNotEmpty() }?.let(UploadFailure::fromCode)
            UploadHistoryEntry(name, time, outcome, failure)
        }.take(MAX_ENTRIES).toList()
    }
}
