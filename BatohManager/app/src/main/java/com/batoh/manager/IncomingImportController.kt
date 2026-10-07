package com.batoh.manager

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import com.batoh.core.common.Result
import com.batoh.core.storage.repository.GifImportFailure
import com.batoh.core.storage.repository.classifyGifImportFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** User-facing category of a failed shared-GIF import. */
internal enum class IncomingImportProblem {
    PermissionLost, SourceMissing, SourceUnreadable, TooLarge, Corrupt, Unsupported, Incomplete, Unknown;

    companion object {
        fun from(error: Throwable): IncomingImportProblem = when (classifyGifImportFailure(error)) {
            GifImportFailure.PermissionRevoked -> PermissionLost
            GifImportFailure.SourceMissing -> SourceMissing
            GifImportFailure.SourceUnreadable -> SourceUnreadable
            GifImportFailure.TooLarge -> TooLarge
            GifImportFailure.Corrupt -> Corrupt
            null -> Unknown
        }
    }
}

/** Message for a failed import; a resumed share whose grant is gone asks the user to share again. */
@StringRes
internal fun IncomingImportProblem.messageRes(resumed: Boolean): Int = when (this) {
    IncomingImportProblem.PermissionLost,
    IncomingImportProblem.SourceMissing ->
        if (resumed) R.string.import_error_restored_unreadable
        else if (this == IncomingImportProblem.PermissionLost) R.string.import_error_permission_revoked
        else R.string.import_error_source_missing
    IncomingImportProblem.SourceUnreadable -> R.string.import_error_source_unreadable
    IncomingImportProblem.TooLarge -> R.string.import_error_too_large
    IncomingImportProblem.Corrupt -> R.string.import_error_corrupt
    IncomingImportProblem.Unsupported -> R.string.import_error_unsupported
    IncomingImportProblem.Incomplete -> R.string.incoming_import_incomplete
    IncomingImportProblem.Unknown -> R.string.import_error_unknown
}

internal sealed interface IncomingImportEvent {
    val id: Long

    data class Started(override val id: Long, val resumed: Boolean) : IncomingImportEvent
    data class Succeeded(override val id: Long, val copiedUri: String) : IncomingImportEvent
    data class Failed(
        override val id: Long,
        val problem: IncomingImportProblem,
        val resumed: Boolean
    ) : IncomingImportEvent
}

/**
 * Android-free core of the incoming share flow: serializes imports, persists pending shares in
 * [savedState] so they survive process death, resumes them after recreation and maps every
 * failure to an [IncomingImportEvent.Failed] instead of crashing.
 */
internal class IncomingImportController<T : Any>(
    private val savedState: SavedStateHandle,
    encode: (T) -> String,
    decode: (String) -> T?,
    private val scope: CoroutineScope,
    private val isSupported: (T) -> Boolean,
    private val importer: suspend (T) -> Result<Any>,
    private val onEvent: (IncomingImportEvent) -> Unit,
    private val newEventId: () -> Long = System::nanoTime,
    private val completedImports: CompletedImports<T>? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val queue = IncomingImportQueue(SavedStateImportStore(savedState, KEY_PENDING, encode, decode))
    private val restored = HashSet<T>()

    /** Restarts shares that were still pending when the previous process was killed. */
    fun resumePending() {
        val items = queue.restore { isAlreadyImported(it) }
        if (items.isNotEmpty()) {
            restored += items
            start(items.first())
        }
    }

    fun submit(item: T, restoring: Boolean) {
        // Launch-intent replay after process death of a share that already finished in the background.
        if (restoring && isAlreadyImported(item)) return
        // A fresh share of a URI seen before (providers recycle URIs) is a new import: drop the
        // stale "done" marker, otherwise a process death would silently discard it on restore.
        if (!restoring) runCatching { completedImports?.remove(item) }
        val admission = queue.submit(
            item = item,
            restoring = restoring,
            previousImportCompleted = savedState.get<Boolean>(KEY_COMPLETED) == true
        )
        if (admission is IncomingImportQueue.Admission.Start) start(admission.item)
    }

    private fun isAlreadyImported(item: T) = runCatching { completedImports?.contains(item) }.getOrNull() == true

    private fun start(item: T) {
        savedState[KEY_COMPLETED] = false
        val resumed = item in restored
        val id = newEventId()
        onEvent(IncomingImportEvent.Started(id, resumed))
        scope.launch {
            var completed = false
            try {
                val event = try {
                    runImport(item, id, resumed)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    IncomingImportEvent.Failed(id, IncomingImportProblem.Unknown, resumed)
                }
                onEvent(event)
                savedState[KEY_COMPLETED] = true
                completed = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Non-cancellation failure (e.g. in event delivery): still release the queue
                // so the next pending share is not stuck behind this one.
                runCatching { onEvent(IncomingImportEvent.Failed(id, IncomingImportProblem.Unknown, resumed)) }
                runCatching { savedState[KEY_COMPLETED] = true }
                completed = true
            } finally {
                restored -= item
                // On cancellation (ViewModel cleared) the pending entry stays persisted.
                if (completed) queue.finish()?.let(::start)
            }
        }
    }

    private suspend fun runImport(item: T, id: Long, resumed: Boolean): IncomingImportEvent {
        if (!isSupported(item)) return IncomingImportEvent.Failed(id, IncomingImportProblem.Unsupported, resumed)
        val result = try {
            importer(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(e)
        }
        return when (result) {
            is Result.Success -> {
                // Record before the queue/SavedStateHandle is updated, so a kill in between cannot re-import.
                // Blocking commit() off the main thread; NonCancellable so it finishes before queue.finish().
                completedImports?.let { done ->
                    runCatching { withContext(ioDispatcher + NonCancellable) { done.add(item) } }
                }
                IncomingImportEvent.Succeeded(id, result.data.toString())
            }
            is Result.Error -> IncomingImportEvent.Failed(id, IncomingImportProblem.from(result.exception), resumed)
            Result.Loading -> IncomingImportEvent.Failed(id, IncomingImportProblem.Incomplete, resumed)
        }
    }

    companion object {
        const val KEY_PENDING = "incoming_pending_imports"
        const val KEY_COMPLETED = "share_completed"
    }
}
