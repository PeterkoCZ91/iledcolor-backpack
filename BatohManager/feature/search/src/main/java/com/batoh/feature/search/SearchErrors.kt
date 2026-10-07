package com.batoh.feature.search

import androidx.annotation.StringRes
import com.batoh.core.common.MissingApiKeyException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Why loading GIFs or categories failed. Exception messages are English and often null, so the
 * UI never shows them; it shows the localized text of the kind instead.
 */
enum class LoadError { MissingApiKey, Network, Server, Unknown }

internal fun Throwable?.toLoadError(): LoadError {
    var cause = this
    var depth = 0
    while (cause != null && depth++ < 8) {
        when (cause) {
            is MissingApiKeyException -> return LoadError.MissingApiKey
            // SocketTimeoutException is an InterruptedIOException.
            is UnknownHostException, is ConnectException, is NoRouteToHostException,
            is InterruptedIOException, is SSLException -> return LoadError.Network
        }
        // Retrofit is not a dependency of this module; match its HTTP error by name.
        if (cause.javaClass.name == "retrofit2.HttpException") return LoadError.Server
        cause = cause.cause
    }
    return LoadError.Unknown
}

@StringRes
internal fun LoadError.messageRes(): Int = when (this) {
    LoadError.MissingApiKey -> R.string.search_error_missing_key
    LoadError.Network -> R.string.search_error_network
    LoadError.Server -> R.string.search_error_server
    LoadError.Unknown -> R.string.search_error_generic
}
