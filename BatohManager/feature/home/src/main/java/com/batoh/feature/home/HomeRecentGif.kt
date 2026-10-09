package com.batoh.feature.home

import com.batoh.core.domain.model.LibraryEntry

/** Minimal view of a collection item for the "recently added" row on the home screen. */
data class HomeRecentGif(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    /** Same key the collection passes to the detail screen. */
    val originalUrl: String
) {
    companion object {
        const val LIMIT = 8

        /** Newest first by date added (unknown dates, i.e. 0, last); stable for ties. */
        fun recentOf(entries: List<LibraryEntry>, limit: Int = LIMIT): List<HomeRecentGif> =
            entries
                .sortedByDescending { it.dateAddedSeconds }
                .take(limit)
                .map { HomeRecentGif(it.gif.id, it.gif.title, it.gif.thumbnailUrl, it.gif.originalUrl) }
    }
}
