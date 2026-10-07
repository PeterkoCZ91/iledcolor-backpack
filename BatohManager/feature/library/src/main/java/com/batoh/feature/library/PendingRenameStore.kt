package com.batoh.feature.library

import androidx.lifecycle.SavedStateHandle
import com.batoh.core.domain.model.Gif

/** Rename waiting for the system write-consent dialog; persisted so it survives process death. */
class PendingRenameStore(private val handle: SavedStateHandle) {
    fun save(gif: Gif, newName: String) {
        handle[KEY] = arrayListOf(
            gif.id, gif.title, gif.thumbnailUrl, gif.originalUrl, gif.mp4Url,
            gif.width.toString(), gif.height.toString(), gif.source, newName
        )
    }

    fun peek(): Pair<Gif, String>? {
        val f = handle.get<ArrayList<String>>(KEY) ?: return null
        if (f.size != 9) return null
        val width = f[5].toIntOrNull() ?: return null
        val height = f[6].toIntOrNull() ?: return null
        return Gif(f[0], f[1], f[2], f[3], f[4], width, height, f[7]) to f[8]
    }

    /** Returns and clears the pending rename. */
    fun take(): Pair<Gif, String>? = peek().also { handle.remove<ArrayList<String>>(KEY) }

    private companion object { const val KEY = "pending_rename" }
}
