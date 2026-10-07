package com.batoh.core.domain.model

/**
 * A GIF in the local collection together with the file metadata the library needs for
 * sorting and the summary line. [sizeBytes] and [dateAddedSeconds] are 0 when unknown.
 */
data class LibraryEntry(
    val gif: Gif,
    val sizeBytes: Long,
    val dateAddedSeconds: Long
)
