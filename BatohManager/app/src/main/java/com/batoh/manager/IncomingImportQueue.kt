package com.batoh.manager

import androidx.lifecycle.SavedStateHandle

/**
 * Serializes incoming share intents and ignores the launch-intent replay after rotation.
 * With a [Store], the active and queued items are persisted so they survive process death.
 */
internal class IncomingImportQueue<T : Any>(private val store: Store<T>? = null) {
    sealed interface Admission<out T> {
        data class Start<T>(val item: T) : Admission<T>
        data object Queued : Admission<Nothing>
        data object Ignored : Admission<Nothing>
    }

    /** Persistence for pending items (active first, then queued). */
    interface Store<T> {
        fun load(): List<T>
        fun save(pending: List<T>)
    }

    private var active: T? = null
    private val queued = ArrayDeque<T>()

    fun submit(item: T, restoring: Boolean, previousImportCompleted: Boolean): Admission<T> {
        if (restoring && previousImportCompleted) return Admission.Ignored
        if (active != null) {
            if (restoring) return Admission.Ignored
            queued.addLast(item)
            persist()
            return Admission.Queued
        }

        active = item
        persist()
        return Admission.Start(item)
    }

    /** Ends the active import and reserves the next queued one, if any. */
    fun finish(): T? {
        active = queued.removeFirstOrNull()
        persist()
        return active
    }

    /**
     * Reloads items that were pending when the previous process died. The first one becomes
     * active and must be started by the caller; returns all restored items in order.
     */
    fun restore(alreadyDone: (T) -> Boolean = { false }): List<T> {
        if (active != null || queued.isNotEmpty()) return emptyList()
        val stored = store?.load().orEmpty()
        val items = stored.filterNot(alreadyDone)
        if (items.size != stored.size) store?.save(items)
        if (items.isEmpty()) return emptyList()
        active = items.first()
        queued.addAll(items.drop(1))
        return items
    }

    private fun persist() {
        store?.save(listOfNotNull(active) + queued)
    }
}

/** Stores pending imports as strings in a [SavedStateHandle] (Uri itself is not needed). */
internal class SavedStateImportStore<T : Any>(
    private val handle: SavedStateHandle,
    private val key: String,
    private val encode: (T) -> String,
    private val decode: (String) -> T?
) : IncomingImportQueue.Store<T> {
    override fun load(): List<T> =
        handle.get<ArrayList<String>>(key).orEmpty().mapNotNull { runCatching { decode(it) }.getOrNull() }

    override fun save(pending: List<T>) {
        handle[key] = ArrayList(pending.map(encode))
    }
}

/** Durable (outside SavedStateHandle) record of imports that already succeeded. */
internal interface CompletedImports<T> {
    fun contains(item: T): Boolean
    fun add(item: T)
    fun remove(item: T)
}

/** Keeps the last [limit] completed items as strings in SharedPreferences. */
internal class PrefsCompletedImports<T : Any>(
    private val prefs: android.content.SharedPreferences,
    private val encode: (T) -> String,
    private val limit: Int = 32
) : CompletedImports<T> {
    override fun contains(item: T): Boolean = read().contains(encode(item))

    override fun add(item: T) {
        val updated = (read() - encode(item)) + encode(item)
        // commit(): must be on disk before the process can be killed. Blocking: the caller
        // must run it off the main thread (IncomingImportController uses Dispatchers.IO).
        prefs.edit().putString(KEY, updated.takeLast(limit).joinToString("\n")).commit()
    }

    override fun remove(item: T) {
        val key = encode(item)
        val current = read()
        if (key !in current) return
        // apply(): called from the main thread when a fresh share reuses a recycled URI.
        prefs.edit().putString(KEY, (current - key).joinToString("\n")).apply()
    }

    private fun read(): List<String> =
        prefs.getString(KEY, null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()

    private companion object { const val KEY = "completed_uris" }
}
