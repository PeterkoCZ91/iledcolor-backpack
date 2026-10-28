package com.batoh.core.common

import android.content.Context

/** Size of the last connected panel, so rendering knows the target while disconnected (64 x 64 if none). */
object LastPanelSize {
    private const val PREFS_NAME = "panel_size"
    private const val KEY_WIDTH = "width"
    private const val KEY_HEIGHT = "height"
    private const val DEFAULT = 64

    fun read(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_WIDTH, DEFAULT) to prefs.getInt(KEY_HEIGHT, DEFAULT)
    }

    fun write(context: Context, width: Int, height: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_WIDTH, width).putInt(KEY_HEIGHT, height).apply()
    }
}
