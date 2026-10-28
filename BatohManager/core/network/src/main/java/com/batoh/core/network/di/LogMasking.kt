package com.batoh.core.network.di

/**
 * Query parameter whose name is, or ends in a `_`/`-` separated word that is, a credential name
 * (key, api key, token, secret, password, auth). `\b` cannot be used: `_` is a word character,
 * so it would never split `access_token`; the lookbehind keeps words like `monkey` unmatched.
 */
private val KEY_PARAMETER = Regex(
    """(?i)(?<![A-Za-z0-9])((?:[A-Za-z0-9]+[_-])*(?:api[_-]?key|key|token|secret|password|passwd|auth))=([^&\s"]+)"""
)

/** Hides the value of key-like query parameters so request lines are safe to share. */
internal fun maskApiKeys(message: String): String =
    KEY_PARAMETER.replace(message) { "${it.groupValues[1]}=***" }
