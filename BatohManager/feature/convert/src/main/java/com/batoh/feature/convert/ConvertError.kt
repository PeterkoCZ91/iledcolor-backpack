package com.batoh.feature.convert

import com.batoh.core.conversion.DownloadHttpException
import com.batoh.core.conversion.EmptyVideoResponseException
import com.batoh.core.conversion.VideoTooLargeException
import java.io.IOException
import java.net.URI

/** Typed reasons a video → GIF conversion can fail; the UI maps each to a localized message. */
sealed interface ConvertError {
    object EmptyUrl : ConvertError
    object InvalidUrl : ConvertError
    /** Plain http:// blocked by the network security policy. */
    object CleartextBlocked : ConvertError
    data class Http(val code: Int) : ConvertError
    /** Offline, DNS, TLS, timeout or a dropped connection. */
    object Network : ConvertError
    object Unreadable : ConvertError
    object TooLarge : ConvertError
    object SaveFailed : ConvertError
    object Unknown : ConvertError
}

/** Only absolute http(s) URLs with a host are worth sending to the downloader. */
internal fun isValidVideoUrl(value: String): Boolean = try {
    val uri = URI(value)
    (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) && !uri.host.isNullOrBlank()
} catch (_: Exception) {
    false
}

/**
 * Maps an exception thrown by the converter to a [ConvertError]. The technical detail is
 * deliberately dropped here — the caller logs the original throwable.
 */
internal fun classifyConvertError(t: Throwable): ConvertError {
    val message = t.message.orEmpty()
    return when {
        // Typed exceptions first: they are ours and unambiguous (a decoder's own
        // IllegalArgumentException with similar wording must not be mistaken for them).
        t is VideoTooLargeException || t is OutOfMemoryError -> ConvertError.TooLarge
        t is DownloadHttpException -> ConvertError.Http(t.code)
        t is EmptyVideoResponseException -> ConvertError.Unreadable
        // Text fallbacks only for messages produced by OkHttp / the platform.
        message.contains("CLEARTEXT", ignoreCase = true) -> ConvertError.CleartextBlocked
        t is IOException -> ConvertError.Network
        t is IllegalArgumentException &&
            (message.startsWith("Expected URL scheme") || message.startsWith("unexpected url") ||
                message.contains("Invalid URL host")) -> ConvertError.InvalidUrl
        t is IllegalArgumentException || t is IllegalStateException || t is RuntimeException &&
            message.contains("setDataSource", ignoreCase = true) -> ConvertError.Unreadable
        else -> ConvertError.Unknown
    }
}

/** Editing the URL invalidates a stale error: back to Idle, anything else is untouched. */
internal fun ConvertUiState.afterUrlEdit(): ConvertUiState =
    if (this is ConvertUiState.Error) ConvertUiState.Idle else this
