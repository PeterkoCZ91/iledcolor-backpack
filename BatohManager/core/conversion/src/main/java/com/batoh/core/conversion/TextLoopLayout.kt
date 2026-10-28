package com.batoh.core.conversion

/** Four scrolling messages, each occupying exactly ten seconds of one repeating GIF. */
object TextLoopLayout {
    const val ITEM_COUNT = 4
    const val ITEM_DURATION_MS = 10_000
    const val FRAME_DELAY_MS = 100
    const val FRAMES_PER_ITEM = ITEM_DURATION_MS / FRAME_DELAY_MS
    const val FRAME_COUNT = ITEM_COUNT * FRAMES_PER_ITEM
    const val DURATION_MS = ITEM_COUNT * ITEM_DURATION_MS

    init {
        check(FRAME_COUNT <= SafeGifDecoder.MAX_FRAMES)
    }

    /** A message starts just outside the right edge and ends just outside the left edge. */
    fun offsetX(panelWidth: Int, textWidth: Int, frameInItem: Int): Int {
        require(panelWidth > 0 && textWidth >= 0)
        require(frameInItem in 0 until FRAMES_PER_ITEM)
        val distance = panelWidth.toLong() + textWidth
        val travelled = (distance * frameInItem + (FRAMES_PER_ITEM - 2)) / (FRAMES_PER_ITEM - 1)
        return (panelWidth - travelled).toInt()
    }
}
