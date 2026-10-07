package com.batoh.feature.library

import com.batoh.core.domain.model.Gif

/** Čistá logika výběru GIFů ke spojení; pořadí výběru je pořadí ve spojení. */
object LibrarySelection {
    const val MIN_TO_CHAIN = 2
    const val MAX_TO_CHAIN = 20

    /** Přidá nebo odebere [id]; při plném výběru přidání ignoruje (vrací původní seznam). */
    fun toggle(selection: List<String>, id: String, max: Int = MAX_TO_CHAIN): List<String> = when {
        id in selection -> selection - id
        selection.size >= max -> selection
        else -> selection + id
    }

    /** Zahodí identifikátory, které už ve sbírce nejsou (smazané, přejmenované). */
    fun prune(selection: List<String>, available: Set<String>): List<String> =
        selection.filter { it in available }

    fun canChain(selection: List<String>) = selection.size >= MIN_TO_CHAIN

    /** URI vybraných GIFů v pořadí výběru. */
    fun uris(selection: List<String>, gifs: List<Gif>): List<String> {
        val byId = gifs.associateBy { it.id }
        return selection.mapNotNull { byId[it]?.originalUrl }
    }
}
