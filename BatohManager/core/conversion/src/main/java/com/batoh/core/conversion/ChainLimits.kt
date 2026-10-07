package com.batoh.core.conversion

enum class ChainLevel { OK, WARN, BLOCK }

data class ChainAssessment(val level: ChainLevel, val message: String?)

/** Pure assessment of the GIF chaining result. Hard limits identical to [SafeGifDecoder]. */
object ChainLimits {
    const val WARN_FRAMES = 96
    const val WARN_BYTES = 453 * 1024L
    const val BLOCK_FRAMES = SafeGifDecoder.MAX_FRAMES
    const val BLOCK_BYTES = SafeGifDecoder.MAX_BYTES.toLong()

    fun assess(frames: Int, bytes: Long): ChainAssessment = when {
        frames > BLOCK_FRAMES -> ChainAssessment(ChainLevel.BLOCK, "Chain has $frames frames, the maximum is $BLOCK_FRAMES")
        bytes > BLOCK_BYTES -> ChainAssessment(ChainLevel.BLOCK, "Chain is larger than 20 MB")
        frames > WARN_FRAMES || bytes > WARN_BYTES ->
            ChainAssessment(ChainLevel.WARN, "Over $WARN_FRAMES frames or 453 KB: not verified on hardware")
        else -> ChainAssessment(ChainLevel.OK, null)
    }
}
