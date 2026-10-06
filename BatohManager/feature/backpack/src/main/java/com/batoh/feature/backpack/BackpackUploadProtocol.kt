package com.batoh.feature.backpack

import androidx.annotation.StringRes
import com.batoh.core.data.bluetooth.UploadFailure

internal enum class UploadStartDecision {
    SendChunks,
    AlreadyPresent,
    InsufficientSpace,
    Rejected
}

internal enum class UploadCompletion {
    Uploaded,
    AlreadyPresent
}

/** Thrown inside the upload task to end it with a typed [failure]. */
internal class UploadFailureException(val failure: UploadFailure, cause: Throwable? = null) :
    Exception(failure.code, cause)

/** Classifies the Cmd 06 response before any data chunks are sent. */
internal fun classifyUploadStart(response: ByteArray): UploadStartDecision {
    require(response.size >= 7) { "Cmd 06: malformed response" }
    return when (response[4].toInt() and 0xFF) {
        1 -> UploadStartDecision.SendChunks
        2 -> UploadStartDecision.InsufficientSpace
        3 -> UploadStartDecision.AlreadyPresent
        else -> UploadStartDecision.Rejected
    }
}

/** Status byte d[4] of a 7-byte command ACK, or -1 when the answer is too short. */
internal fun ackStatus(response: ByteArray): Int = if (response.size >= 7) response[4].toInt() and 0xFF else -1

/**
 * Maps the Cmd 06 answer to a failure; null means the upload may continue
 * (status 1, send chunks) or is already complete (status 3).
 */
internal fun uploadStartFailure(response: ByteArray?): UploadFailure? {
    if (response == null) return UploadFailure.HeaderTimeout
    if (response.size < 7) return UploadFailure.HeaderRejected(-1)
    return when (classifyUploadStart(response)) {
        UploadStartDecision.SendChunks, UploadStartDecision.AlreadyPresent -> null
        UploadStartDecision.InsufficientSpace -> UploadFailure.InsufficientSpace
        UploadStartDecision.Rejected -> UploadFailure.HeaderRejected(ackStatus(response))
    }
}

/**
 * Maps the result of one data chunk to a failure; null means the chunk was accepted.
 * [acked] is false when no ACK with the chunk's index arrived in time; only status 1 is OK.
 */
internal fun chunkFailure(index: Int, total: Int, written: Boolean, acked: Boolean, status: Int): UploadFailure? = when {
    !written -> UploadFailure.ChunkWriteFailed(index, total)
    !acked -> UploadFailure.ChunkTimeout(index, total)
    status != 1 -> UploadFailure.ChunkRejected(index, total, status)
    else -> null
}

/** Maps the end-of-transfer echo to a failure; GifPack accepts status 1 or 3 there. */
internal fun endFailure(response: ByteArray?): UploadFailure? {
    if (response == null) return UploadFailure.EndTimeout
    val status = ackStatus(response)
    return if (status == 1 || status == 3) null else UploadFailure.EndRejected(status)
}

/** True for reasons that only mean "no answer arrived", which a dropped link also produces. */
internal fun UploadFailure.isMissingAnswer(): Boolean =
    this is UploadFailure.ChunkTimeout || this is UploadFailure.ChunkWriteFailed ||
        this == UploadFailure.HeaderTimeout || this == UploadFailure.EndTimeout || this == UploadFailure.AuthFailed

/** Localised texts of a failure: a short [title] (with [args]) and a "what to do" [hint]. */
internal data class UploadFailureText(@StringRes val title: Int, val args: List<Any>, @StringRes val hint: Int)

internal fun UploadFailure.text(): UploadFailureText = when (this) {
    UploadFailure.NotConnected -> UploadFailureText(R.string.upload_error_not_connected, emptyList(), R.string.upload_hint_reconnect)
    UploadFailure.ConnectionLost -> UploadFailureText(R.string.upload_error_connection_lost, emptyList(), R.string.upload_hint_move_closer)
    UploadFailure.AuthFailed -> UploadFailureText(R.string.upload_error_auth_failed, emptyList(), R.string.upload_hint_restart_backpack)
    UploadFailure.HeaderTimeout -> UploadFailureText(R.string.upload_error_header_timeout, emptyList(), R.string.upload_hint_try_again)
    is UploadFailure.HeaderRejected -> UploadFailureText(R.string.upload_error_header_rejected, listOf(status), R.string.upload_hint_try_again_restart)
    UploadFailure.InsufficientSpace -> UploadFailureText(R.string.upload_error_insufficient_space, emptyList(), R.string.upload_hint_free_space)
    is UploadFailure.ChunkWriteFailed -> UploadFailureText(R.string.upload_error_chunk_write_failed, listOf(index + 1, total), R.string.upload_hint_move_closer)
    is UploadFailure.ChunkRejected -> UploadFailureText(R.string.upload_error_chunk_rejected, listOf(index + 1, total, status), R.string.upload_hint_try_again_restart)
    is UploadFailure.ChunkTimeout -> UploadFailureText(R.string.upload_error_chunk_timeout, listOf(index + 1, total), R.string.upload_hint_move_closer)
    UploadFailure.EndTimeout -> UploadFailureText(R.string.upload_error_end_timeout, emptyList(), R.string.upload_hint_not_stored)
    is UploadFailure.EndRejected -> UploadFailureText(R.string.upload_error_end_rejected, listOf(status), R.string.upload_hint_not_stored)
    is UploadFailure.MtuTooSmall -> UploadFailureText(R.string.upload_error_mtu_too_small, listOf(mtu), R.string.upload_hint_mtu)
    UploadFailure.GifTooLarge -> UploadFailureText(R.string.upload_error_gif_too_large, emptyList(), R.string.upload_hint_smaller_gif)
    UploadFailure.GifUnreadable -> UploadFailureText(R.string.upload_error_gif_unreadable, emptyList(), R.string.upload_hint_pick_again)
    UploadFailure.NotAGif -> UploadFailureText(R.string.upload_error_not_a_gif, emptyList(), R.string.upload_hint_pick_gif)
    UploadFailure.GifInvalid -> UploadFailureText(R.string.upload_error_gif_invalid, emptyList(), R.string.upload_hint_other_file)
    UploadFailure.PayloadInvalid -> UploadFailureText(R.string.upload_error_payload_invalid, emptyList(), R.string.upload_hint_recreate)
    UploadFailure.Cancelled -> UploadFailureText(R.string.upload_error_cancelled, emptyList(), R.string.upload_hint_cancelled)
    UploadFailure.Unknown -> UploadFailureText(R.string.upload_error_unknown, emptyList(), R.string.upload_hint_check_log)
}
