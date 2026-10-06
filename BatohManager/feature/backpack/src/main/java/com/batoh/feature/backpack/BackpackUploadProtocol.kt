package com.batoh.feature.backpack

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

/** Classifies the Cmd 06 response before any data chunks are sent. */
internal fun classifyUploadStart(response: ByteArray): UploadStartDecision {
    require(response.size >= 7) { "Cmd 06: neplatná odpověď" }
    return when (response[4].toInt() and 0xFF) {
        1 -> UploadStartDecision.SendChunks
        2 -> UploadStartDecision.InsufficientSpace
        3 -> UploadStartDecision.AlreadyPresent
        else -> UploadStartDecision.Rejected
    }
}
