package com.batoh.core.domain.model

/** Why a proposed collection file name was rejected. */
enum class GifNameProblem { EMPTY, TOO_LONG, FORBIDDEN_CHARACTERS, LEADING_DOT }

/** Outcome of [GifDisplayName.validate]: either a ready `*.gif` file name or a problem. */
sealed interface GifNameValidation {
    data class Valid(val fileName: String) : GifNameValidation
    data class Invalid(val problem: GifNameProblem) : GifNameValidation
}

/** Pure rules for renaming files in the app's collection; the `.gif` extension is always kept. */
object GifDisplayName {
    const val EXTENSION = ".gif"
    /** MediaProvider / file-system limit for a whole file name, in UTF-8 bytes. */
    const val MAX_FILE_NAME_BYTES = 255
    /** Base-name limit in UTF-8 bytes (the limit minus the `.gif` extension). */
    const val MAX_BASE_BYTES = MAX_FILE_NAME_BYTES - EXTENSION.length
    /** Upper bound of base-name characters (UTF-16 units); a char is at least 1 byte, so this is safe as an input cap. */
    const val MAX_BASE_LENGTH = MAX_BASE_BYTES
    private val forbidden = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

    /** The editable part of [displayName], i.e. without a trailing `.gif` (any case). */
    fun baseName(displayName: String): String =
        if (displayName.endsWith(EXTENSION, ignoreCase = true)) displayName.dropLast(EXTENSION.length)
        else displayName

    fun validate(input: String): GifNameValidation {
        val base = baseName(input.trim()).trim()
        val problem = when {
            base.isEmpty() -> GifNameProblem.EMPTY
            utf8Length(base) > MAX_BASE_BYTES -> GifNameProblem.TOO_LONG
            base.any { it in forbidden || it.isISOControl() } -> GifNameProblem.FORBIDDEN_CHARACTERS
            base.startsWith('.') -> GifNameProblem.LEADING_DOT
            else -> null
        }
        return if (problem != null) GifNameValidation.Invalid(problem) else GifNameValidation.Valid(base + EXTENSION)
    }

    /** UTF-8 size of [text]; an unpaired surrogate is counted as 3 bytes (conservative). */
    fun utf8Length(text: String): Int {
        var bytes = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.code < 0x80 -> bytes += 1
                c.code < 0x800 -> bytes += 2
                Character.isHighSurrogate(c) && i + 1 < text.length && Character.isLowSurrogate(text[i + 1]) -> {
                    bytes += 4
                    i++
                }
                else -> bytes += 3
            }
            i++
        }
        return bytes
    }
}
