package com.batoh.core.domain.model

/** Expected, user-explainable reasons a collection rename did not happen. */
enum class GifRenameFailure {
    /** The proposed name breaks [GifDisplayName] rules. */
    INVALID_NAME,
    /** The item is not a file in the app's own `Pictures/GifPack` folder. */
    NOT_IN_COLLECTION,
    /** The file no longer exists in MediaStore. */
    NOT_FOUND,
    /** Another collection file already uses the name. */
    NAME_TAKEN,
    /** MediaStore accepted the call but changed nothing. */
    NOT_CHANGED
}

class GifRenameException(val failure: GifRenameFailure) : Exception("Rename failed: $failure")
