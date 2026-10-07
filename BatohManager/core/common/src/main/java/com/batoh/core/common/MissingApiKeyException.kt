package com.batoh.core.common

/** A GIF source cannot be queried because no API key is configured; the UI shows a localized hint. */
class MissingApiKeyException(source: String) : IllegalStateException("$source API key is missing")
