package com.batoh.core.domain.model

/** Why a library GIF cannot be read or decoded by the app's own (bounded) GIF parser. */
enum class GifFileProblem {
    /** MediaStore row exists but the file is gone (deleted outside the app, stale index). */
    MISSING,
    /** The file belongs to another app or a previous installation and cannot be read. */
    NO_ACCESS,
    /** The file has zero bytes (interrupted write). */
    EMPTY,
    /** The bytes are not a GIF (e.g. a video or PNG saved with the .gif extension). */
    NOT_GIF,
    /** Larger than the 20 MiB limit used for editing and upload. */
    TOO_LARGE,
    /** GIF header is fine but the data is truncated, malformed or outside supported limits. */
    CORRUPT,
    /** Reading failed with an I/O error. */
    UNREADABLE
}
