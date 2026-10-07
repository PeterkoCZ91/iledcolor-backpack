package com.batoh.feature.backpack

import com.batoh.core.conversion.ChainLevel
import com.batoh.core.conversion.ChainLimits
import com.batoh.core.conversion.GifConcatException
import com.batoh.core.data.bluetooth.UploadFailure
import kotlin.coroutines.cancellation.CancellationException

/** One GIF scaled to 64x64 and wrapped into a programme payload, plus the frame count of its source. */
internal class PreparedProgramme(val payload: ByteArray, val frames: Int)

/**
 * Estimate of a programme sequence. [kind] is BLOCK above the app limits, WARN above the sizes verified on
 * hardware (see the two flags) and OK otherwise.
 */
data class SequenceStatus(
    val programmes: Int,
    val totalBytes: Long,
    val totalFrames: Int,
    val kind: ChainStatusKind,
    val overVerifiedBytes: Boolean,
    val overVerifiedCount: Boolean
) {
    val sizeKb: Long get() = GifChainPlan.sizeKb(totalBytes)
    val allowsOutput: Boolean get() = kind != ChainStatusKind.BLOCK
}

/** Pure logic of the "send as sequence" mode of the GIF merge screen (JVM-testable). */
object GifSequencePlan {
    const val MIN_PROGRAMMES = 2
    /** Programme count above which the sequence is not verified on the backpack (the real maximum is unknown). */
    const val WARN_PROGRAMMES = 8
    /** Total payload size verified on hardware. */
    const val WARN_TOTAL_BYTES = ChainLimits.WARN_BYTES
    /** Hard ceiling of the item count byte of Cmd 0x03. */
    const val MAX_PROGRAMMES = 255

    fun canPrepare(count: Int) = count >= MIN_PROGRAMMES

    /** Assesses sequence totals; frames and bytes are summed over all programmes. */
    fun assess(payloadBytes: List<Int>, frames: List<Int>): SequenceStatus {
        require(payloadBytes.size == frames.size) { "Payload and frame lists differ in length" }
        val count = payloadBytes.size
        val bytes = payloadBytes.sumOf { it.toLong() }
        val totalFrames = frames.sum()
        val overBytes = bytes > WARN_TOTAL_BYTES
        val overCount = count > WARN_PROGRAMMES
        val blocked = count < MIN_PROGRAMMES || count > MAX_PROGRAMMES ||
            ChainLimits.assess(totalFrames, bytes).level == ChainLevel.BLOCK
        val kind = when {
            blocked -> ChainStatusKind.BLOCK
            overBytes || overCount -> ChainStatusKind.WARN
            else -> ChainStatusKind.OK
        }
        return SequenceStatus(count, bytes, totalFrames, kind, overBytes, overCount)
    }

    internal fun assess(programmes: List<PreparedProgramme>): SequenceStatus =
        assess(programmes.map { it.payload.size }, programmes.map { it.frames })

    /**
     * Prepares the programmes of [uris] strictly in the given order (the order of the list is the playing
     * order). [prepare] receives the 0-based index of the failing item so errors can be attributed.
     */
    suspend fun <T> prepareAll(uris: List<String>, prepare: suspend (index: Int, uri: String) -> T): List<T> {
        if (uris.isEmpty()) throw GifConcatException.EmptyInput()
        return uris.mapIndexed { index, uri -> prepare(index, uri) }
    }

    /** Maps an upload failure raised while preparing item [index] to a typed screen error. */
    fun errorFor(index: Int, failure: UploadFailure): GifChainError = when (failure) {
        UploadFailure.GifTooLarge -> GifChainError.SourceTooLarge(index)
        UploadFailure.GifUnreadable -> GifChainError.SourceUnreadable(index)
        UploadFailure.NotAGif, UploadFailure.GifInvalid, UploadFailure.PayloadInvalid -> GifChainError.SourceInvalid(index)
        else -> GifChainError.Generic
    }

    /** Converts any preparation exception to a typed error; cancellation is always propagated. */
    internal fun errorFor(t: Throwable, index: Int): GifChainError = when (t) {
        is CancellationException -> throw t
        is UploadFailureException -> errorFor(index, t.failure)
        else -> GifChainPlan.errorFor(t)
    }
}
