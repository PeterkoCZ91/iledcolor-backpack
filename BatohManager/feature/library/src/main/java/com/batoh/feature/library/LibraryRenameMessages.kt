package com.batoh.feature.library

import androidx.annotation.StringRes
import com.batoh.core.domain.model.GifNameProblem
import com.batoh.core.domain.model.GifRenameFailure

@StringRes
internal fun GifRenameFailure.messageRes(): Int = when (this) {
    GifRenameFailure.INVALID_NAME -> R.string.library_rename_invalid
    GifRenameFailure.NOT_IN_COLLECTION -> R.string.library_rename_not_in_collection
    GifRenameFailure.NOT_FOUND -> R.string.library_rename_not_found
    GifRenameFailure.NAME_TAKEN -> R.string.library_rename_name_taken
    GifRenameFailure.NOT_CHANGED -> R.string.library_rename_failed
}

@StringRes
internal fun GifNameProblem.messageRes(): Int = when (this) {
    GifNameProblem.EMPTY -> R.string.library_rename_error_empty
    GifNameProblem.TOO_LONG -> R.string.library_rename_error_too_long
    GifNameProblem.FORBIDDEN_CHARACTERS -> R.string.library_rename_error_characters
    GifNameProblem.LEADING_DOT -> R.string.library_rename_error_leading_dot
}

@StringRes
internal fun LibrarySort.labelRes(): Int = when (this) {
    LibrarySort.NEWEST -> R.string.library_sort_newest
    LibrarySort.OLDEST -> R.string.library_sort_oldest
    LibrarySort.NAME -> R.string.library_sort_name
    LibrarySort.SIZE -> R.string.library_sort_size
}
