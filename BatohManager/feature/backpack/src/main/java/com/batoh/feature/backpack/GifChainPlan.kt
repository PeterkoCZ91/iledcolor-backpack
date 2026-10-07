package com.batoh.feature.backpack

import com.batoh.core.conversion.ChainLevel
import com.batoh.core.conversion.ChainLimits
import com.batoh.core.conversion.GifConcatException
import kotlin.coroutines.cancellation.CancellationException

/** Chain item; [id] is a stable key (the same GIF may appear in the chain multiple times). */
data class ChainItem(val id: Long, val uri: String)

/** Typed merge errors; the UI maps them to localized text and never shows the exception text. */
sealed class GifChainError {
    object Empty : GifChainError()
    data class SourceUnreadable(val index: Int) : GifChainError()
    data class SourceInvalid(val index: Int) : GifChainError()
    data class SourceTooLarge(val index: Int) : GifChainError()
    object InputsTooLarge : GifChainError()
    data class TooManyFrames(val limit: Int) : GifChainError()
    object TooLarge : GifChainError()
    object SaveFailed : GifChainError()
    object Generic : GifChainError()

    /** Errors that prevent both saving and sending the result. */
    val isLimit: Boolean get() = this is TooManyFrames || this is TooLarge || this is InputsTooLarge
}

/** The source failed to load (I/O, missing file, file too large). */
class ChainSourceException(val index: Int, val tooLarge: Boolean = false, cause: Throwable? = null) :
    Exception("Source $index unavailable", cause)

enum class ChainStatusKind { OK, WARN, BLOCK }

/** Result state for the UI: frame count, size and warning level. */
data class ChainStatus(val frames: Int, val bytes: Long, val kind: ChainStatusKind) {
    val sizeKb: Long get() = GifChainPlan.sizeKb(bytes)
    val allowsOutput: Boolean get() = kind != ChainStatusKind.BLOCK
}

/** Pure logic of the GIF merge screen (JVM-testable). */
object GifChainPlan {
    const val MIN_ITEMS = 2
    const val MAX_ITEMS = 20
    /** Sum of the sizes of the sources held in memory. */
    const val MAX_TOTAL_SOURCE_BYTES = 40L * 1024 * 1024
    const val MAX_PAUSE_MS = 3000
    const val MIN_SPEED_MS = 20
    const val MAX_SPEED_MS = 500

    /** List of URIs in the route argument: each URI is encoded and separated by a comma. */
    fun parseUris(arg: String?): List<String> =
        arg.orEmpty().split(',').filter { it.isNotBlank() }
            .map { java.net.URLDecoder.decode(it, "UTF-8") }
            .filter { it.isNotBlank() }
            .take(MAX_ITEMS)

    fun <T> moveUp(items: List<T>, index: Int): List<T> =
        if (index !in 1..items.lastIndex) items else swap(items, index, index - 1)

    fun <T> moveDown(items: List<T>, index: Int): List<T> =
        if (index !in 0 until items.lastIndex) items else swap(items, index, index + 1)

    fun <T> remove(items: List<T>, index: Int): List<T> =
        if (index !in items.indices) items else items.toMutableList().also { it.removeAt(index) }

    private fun <T> swap(items: List<T>, a: Int, b: Int): List<T> =
        items.toMutableList().also { val t = it[a]; it[a] = it[b]; it[b] = t }

    fun canRender(itemCount: Int) = itemCount >= MIN_ITEMS

    fun clampPause(ms: Int) = ms.coerceIn(0, MAX_PAUSE_MS)
    fun clampSpeed(ms: Int) = ms.coerceIn(MIN_SPEED_MS, MAX_SPEED_MS)

    fun sizeKb(bytes: Long): Long = (bytes + 1023) / 1024

    fun status(frames: Int, bytes: Long): ChainStatus {
        val kind = when (ChainLimits.assess(frames, bytes).level) {
            ChainLevel.OK -> ChainStatusKind.OK
            ChainLevel.WARN -> ChainStatusKind.WARN
            ChainLevel.BLOCK -> ChainStatusKind.BLOCK
        }
        return ChainStatus(frames, bytes, kind)
    }

    /** Converts an exception to a typed error; cancellation is always propagated. */
    fun errorFor(t: Throwable): GifChainError = when (t) {
        is CancellationException -> throw t
        is GifConcatException.EmptyInput -> GifChainError.Empty
        is GifConcatException.InvalidSource -> GifChainError.SourceInvalid(t.index)
        is GifConcatException.TooManyFrames -> GifChainError.TooManyFrames(t.limit)
        is GifConcatException.TooLarge -> GifChainError.TooLarge
        is ChainSourceException ->
            if (t.tooLarge) GifChainError.SourceTooLarge(t.index) else GifChainError.SourceUnreadable(t.index)
        else -> GifChainError.Generic
    }
}
