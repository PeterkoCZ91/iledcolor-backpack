package com.batoh.feature.library

import com.batoh.core.domain.model.Gif
import com.batoh.core.domain.model.LibraryEntry

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

    /** Joining needs 2..[MAX_TO_CHAIN] GIFs; a larger selection (from "Select all") can still be deleted. */
    fun canChain(selection: List<String>) = selection.size in MIN_TO_CHAIN..MAX_TO_CHAIN

    /** "Select all": keeps the current selection order and appends the shown ids that are not selected yet. */
    fun selectAll(selection: List<String>, shown: List<String>): List<String> =
        selection + shown.filter { it !in selection }

    fun allSelected(selection: List<String>, shown: List<String>): Boolean =
        shown.isNotEmpty() && shown.all { it in selection }

    /** The selected GIFs that still exist, in selection order (what a bulk delete acts on). */
    fun resolve(selection: List<String>, gifs: List<Gif>): List<Gif> {
        val byId = gifs.associateBy { it.id }
        return selection.mapNotNull { byId[it] }
    }

    /** Total size of the selected GIFs, or null when one of them is not among [entries] (e.g. filtered out). */
    fun totalBytes(selection: List<String>, entries: List<LibraryEntry>): Long? {
        val byId = entries.associateBy { it.gif.id }
        if (selection.any { it !in byId }) return null
        return selection.sumOf { byId.getValue(it).sizeBytes.coerceAtLeast(0) }
    }

    /** URIs of the selected GIFs in selection order. */
    fun uris(selection: List<String>, gifs: List<Gif>): List<String> {
        val byId = gifs.associateBy { it.id }
        return selection.mapNotNull { byId[it]?.originalUrl }
    }
}
