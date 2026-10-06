package com.batoh.feature.library

import com.batoh.core.domain.model.GifFileProblem

/** Result of checking a library tile whose preview failed to load. */
sealed interface PreviewDiagnosis {
    /** File is being read with the app's GIF parser. */
    object Checking : PreviewDiagnosis
    /** Only the system preview decoder failed; editing and sending accept the file. */
    object PreviewOnly : PreviewDiagnosis
    /** File cannot be edited or sent; offer removal instead. */
    data class Broken(val problem: GifFileProblem) : PreviewDiagnosis
}

/** Whether edit/send may be offered for a tile with this diagnosis (null = preview loaded/unknown). */
fun PreviewDiagnosis?.allowsEditAndSend(): Boolean = this == null || this == PreviewDiagnosis.PreviewOnly

fun GifFileProblem?.toDiagnosis(): PreviewDiagnosis =
    if (this == null) PreviewDiagnosis.PreviewOnly else PreviewDiagnosis.Broken(this)

fun GifFileProblem.reasonRes(): Int = when (this) {
    GifFileProblem.MISSING -> R.string.library_problem_missing
    GifFileProblem.NO_ACCESS -> R.string.library_problem_no_access
    GifFileProblem.EMPTY -> R.string.library_problem_empty
    GifFileProblem.NOT_GIF -> R.string.library_problem_not_gif
    GifFileProblem.TOO_LARGE -> R.string.library_problem_too_large
    GifFileProblem.CORRUPT -> R.string.library_problem_corrupt
    GifFileProblem.UNREADABLE -> R.string.library_problem_unreadable
}
