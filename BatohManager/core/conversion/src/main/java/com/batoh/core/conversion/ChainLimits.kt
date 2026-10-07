package com.batoh.core.conversion

enum class ChainLevel { OK, WARN, BLOCK }

data class ChainAssessment(val level: ChainLevel, val message: String?)

/** Čisté posouzení výsledku řetězení GIFů. Tvrdé limity shodné s [SafeGifDecoder]. */
object ChainLimits {
    const val WARN_FRAMES = 96
    const val WARN_BYTES = 453 * 1024L
    const val BLOCK_FRAMES = SafeGifDecoder.MAX_FRAMES
    const val BLOCK_BYTES = SafeGifDecoder.MAX_BYTES.toLong()

    fun assess(frames: Int, bytes: Long): ChainAssessment = when {
        frames > BLOCK_FRAMES -> ChainAssessment(ChainLevel.BLOCK, "Řetěz má $frames snímků, nejvýše je $BLOCK_FRAMES")
        bytes > BLOCK_BYTES -> ChainAssessment(ChainLevel.BLOCK, "Řetěz má více než 20 MB")
        frames > WARN_FRAMES || bytes > WARN_BYTES ->
            ChainAssessment(ChainLevel.WARN, "Nad $WARN_FRAMES snímků nebo 453 KB: neověřeno na hardwaru")
        else -> ChainAssessment(ChainLevel.OK, null)
    }
}
