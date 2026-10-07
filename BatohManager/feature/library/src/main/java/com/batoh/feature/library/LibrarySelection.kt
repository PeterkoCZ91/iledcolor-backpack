package com.batoh.feature.library

import com.batoh.core.domain.model.Gif

/** Pure logic for selecting GIFs to merge; selection order is the merge order. */
object LibrarySelection {
    const val MIN_TO_CHAIN = 2
    const val MAX_TO_CHAIN = 20

    /** Adds or removes [id]; when the selection is full, the add is ignored (returns the original list). */
    fun toggle(selection: List<String>, id: String, max: Int = MAX_TO_CHAIN): List<String> = when {
        id in selection -> selection - id
        selection.size >= max -> selection
        else -> selection + id
    }

    /** Drops identifiers no longer in the collection (deleted, renamed). */
    fun prune(selection: List<String>, available: Set<String>): List<String> =
        selection.filter { it in available }

    fun canChain(selection: List<String>) = selection.size >= MIN_TO_CHAIN

    /** URIs of the selected GIFs in selection order. */
    fun uris(selection: List<String>, gifs: List<Gif>): List<String> {
        val byId = gifs.associateBy { it.id }
        return selection.mapNotNull { byId[it]?.originalUrl }
    }
}
