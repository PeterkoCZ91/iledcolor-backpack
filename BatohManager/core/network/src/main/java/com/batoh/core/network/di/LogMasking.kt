package com.batoh.core.network.di

private val KEY_PARAMETER = Regex("""(?i)\b((?:api[_-]?)?key|token)=([^&\s"]+)""")

/** Hides the value of key-like query parameters so request lines are safe to share. */
internal fun maskApiKeys(message: String): String =
    KEY_PARAMETER.replace(message) { "${it.groupValues[1]}=***" }
