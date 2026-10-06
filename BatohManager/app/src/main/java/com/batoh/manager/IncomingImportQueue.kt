package com.batoh.manager

/** Serializes incoming share intents and ignores the launch-intent replay after rotation. */
internal class IncomingImportQueue<T : Any> {
    sealed interface Admission<out T> {
        data class Start<T>(val item: T) : Admission<T>
        data object Queued : Admission<Nothing>
        data object Ignored : Admission<Nothing>
    }

    private var active = false
    private val queued = ArrayDeque<T>()

    fun submit(item: T, restoring: Boolean, previousImportCompleted: Boolean): Admission<T> {
        if (restoring && previousImportCompleted) return Admission.Ignored
        if (active) {
            if (restoring) return Admission.Ignored
            queued.addLast(item)
            return Admission.Queued
        }

        active = true
        return Admission.Start(item)
    }

    /** Ends the active import and reserves the next queued one, if any. */
    fun finish(): T? {
        active = false
        val next = queued.removeFirstOrNull() ?: return null
        active = true
        return next
    }
}
