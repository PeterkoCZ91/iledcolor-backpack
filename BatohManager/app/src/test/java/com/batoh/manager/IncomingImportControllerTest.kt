package com.batoh.manager

import androidx.lifecycle.SavedStateHandle
import com.batoh.core.common.Result
import com.batoh.core.storage.repository.GifImportException
import com.batoh.core.storage.repository.GifImportFailure
import java.io.FileNotFoundException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingImportControllerTest {
    private val events = mutableListOf<IncomingImportEvent>()
    private var nextId = 0L

    private fun controller(
        handle: SavedStateHandle,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        importer: suspend (String) -> Result<Any>
    ) = IncomingImportController(
        savedState = handle,
        encode = { it },
        decode = { it },
        scope = scope,
        isSupported = { it.startsWith("content://") },
        importer = importer,
        onEvent = { events += it },
        newEventId = { ++nextId }
    )

    /** Simulates process death: only the SavedStateHandle contents survive. */
    private fun SavedStateHandle.afterProcessDeath() = SavedStateHandle(keys().associateWith { get<Any>(it) })

    @Test
    fun pendingShareIsPersistedWhileImporting() {
        val handle = SavedStateHandle()
        val gate = CompletableDeferred<Result<Any>>()
        val controller = controller(handle) { gate.await() }

        controller.submit("content://share/1", restoring = false)
        controller.submit("content://share/2", restoring = false)

        assertEquals(arrayListOf("content://share/1", "content://share/2"),
            handle.get<ArrayList<String>>(IncomingImportController.KEY_PENDING))
        gate.complete(Result.Success("content://media/1"))
        assertEquals(emptyList<String>(), handle.get<ArrayList<String>>(IncomingImportController.KEY_PENDING))
    }

    @Test
    fun restoredQueueWithUnreadableUriReportsShareAgainAndDoesNotCrash() {
        // First process: import starts, then the process is killed mid-import.
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val before = SavedStateHandle()
        controller(before, firstScope) { CompletableDeferred<Result<Any>>().await() }
            .submit("content://share/1", restoring = false)
        firstScope.cancel()
        events.clear()
        nextId = 0

        // Recreated process: the ACTION_SEND grant did not survive.
        val restoredHandle = before.afterProcessDeath()
        val attempts = mutableListOf<String>()
        val restored = controller(restoredHandle) { uri ->
            attempts += uri
            Result.Error(SecurityException("Permission Denial: reading provider $uri"), "Import selhal")
        }
        restored.resumePending()
        // The activity replays its launch intent with restoring = true; it must not import twice.
        restored.submit("content://share/1", restoring = true)

        assertEquals(listOf("content://share/1"), attempts)
        assertEquals(
            listOf(
                IncomingImportEvent.Started(1, resumed = true),
                IncomingImportEvent.Failed(1, IncomingImportProblem.PermissionLost, resumed = true)
            ),
            events
        )
        assertEquals(R.string.import_error_restored_unreadable,
            IncomingImportProblem.PermissionLost.messageRes(resumed = true))
        assertEquals(emptyList<String>(), restoredHandle.get<ArrayList<String>>(IncomingImportController.KEY_PENDING))
        assertEquals(true, restoredHandle.get<Boolean>(IncomingImportController.KEY_COMPLETED))
    }

    @Test
    fun restoredQueueResumesReadableSharesInOrder() {
        val handle = SavedStateHandle(mapOf(
            IncomingImportController.KEY_PENDING to arrayListOf("content://share/1", "content://share/2"),
            IncomingImportController.KEY_COMPLETED to false
        ))
        val controller = controller(handle) { uri ->
            if (uri.endsWith("1")) Result.Error(GifImportException(GifImportFailure.SourceMissing, "gone"))
            else Result.Success("content://media/7")
        }

        controller.resumePending()

        assertEquals(
            listOf(
                IncomingImportEvent.Started(1, resumed = true),
                IncomingImportEvent.Failed(1, IncomingImportProblem.SourceMissing, resumed = true),
                IncomingImportEvent.Started(2, resumed = true),
                IncomingImportEvent.Succeeded(2, "content://media/7")
            ),
            events
        )
        assertEquals(emptyList<String>(), handle.get<ArrayList<String>>(IncomingImportController.KEY_PENDING))
    }

    @Test
    fun revokedGrantOnFreshShareAsksToShareAgain() {
        val controller = controller(SavedStateHandle()) { throw SecurityException("revoked") }

        controller.submit("content://share/9", restoring = false)

        val failure = events.last() as IncomingImportEvent.Failed
        assertEquals(IncomingImportProblem.PermissionLost, failure.problem)
        assertEquals(R.string.import_error_permission_revoked, failure.problem.messageRes(failure.resumed))
    }

    @Test
    fun failuresMapToSpecificProblems() {
        assertEquals(IncomingImportProblem.SourceMissing, IncomingImportProblem.from(FileNotFoundException()))
        assertEquals(IncomingImportProblem.SourceUnreadable,
            IncomingImportProblem.from(GifImportException(GifImportFailure.SourceUnreadable, "x", IllegalStateException())))
        assertEquals(IncomingImportProblem.TooLarge,
            IncomingImportProblem.from(GifImportException(GifImportFailure.TooLarge, "x")))
        assertEquals(IncomingImportProblem.Unknown, IncomingImportProblem.from(IllegalStateException("save failed")))
    }

    @Test
    fun unsupportedSchemeIsReportedWithoutCallingTheImporter() {
        var called = false
        val controller = controller(SavedStateHandle()) { called = true; Result.Success("x") }

        controller.submit("file:///sdcard/a.gif", restoring = false)

        assertTrue(!called)
        assertEquals(IncomingImportProblem.Unsupported, (events.last() as IncomingImportEvent.Failed).problem)
    }

    @Test
    fun completedShareIsNotRepeatedAfterProcessDeath() {
        val handle = SavedStateHandle()
        controller(handle) { Result.Success("content://media/1") }.submit("content://share/1", restoring = false)
        events.clear()

        val restored = controller(handle.afterProcessDeath()) { error("must not import again") }
        restored.resumePending()
        restored.submit("content://share/1", restoring = true)

        assertTrue(events.isEmpty())
    }

    @Test
    fun throwingEventSinkStillFinishesQueueAndStartsNextShare() {
        val handle = SavedStateHandle()
        val imported = mutableListOf<String>()
        var failFirstDelivery = true
        val controller = IncomingImportController(
            savedState = handle, encode = { it }, decode = { it },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            isSupported = { true },
            importer = { imported += it; Result.Success("content://media/x") },
            onEvent = {
                if (it is IncomingImportEvent.Succeeded && failFirstDelivery) {
                    failFirstDelivery = false
                    throw IllegalStateException("delivery failed")
                }
                events += it
            },
            newEventId = { ++nextId }
        )

        controller.submit("content://share/1", restoring = false)
        controller.submit("content://share/2", restoring = false)

        assertEquals(listOf("content://share/1", "content://share/2"), imported)
        assertEquals(emptyList<String>(), handle.get<ArrayList<String>>(IncomingImportController.KEY_PENDING))
    }

    @Test
    fun restartAfterImportFinishedBeforeQueueUpdateDoesNotImportAgain() {
        val done = mutableSetOf<String>()
        val completed = object : CompletedImports<String> {
            override fun contains(item: String) = item in done
            override fun add(item: String) { done += item }
            override fun remove(item: String) { done -= item }
        }
        val handle = SavedStateHandle()
        var snapshot: SavedStateHandle? = null
        val first = IncomingImportController(
            savedState = handle, encode = { it }, decode = { it },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            isSupported = { true },
            importer = { Result.Success("content://media/1") },
            // Process "dies" here: the file is saved, but pending=[A], completed=false is what survives.
            onEvent = { if (it is IncomingImportEvent.Succeeded) snapshot = handle.afterProcessDeath() },
            newEventId = { ++nextId },
            completedImports = completed,
            ioDispatcher = Dispatchers.Unconfined
        )
        first.submit("content://share/1", restoring = false)

        val attempts = mutableListOf<String>()
        val restored = IncomingImportController(
            savedState = snapshot!!, encode = { it }, decode = { it },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            isSupported = { true },
            importer = { attempts += it; Result.Success("content://media/2") },
            onEvent = { events += it },
            newEventId = { ++nextId },
            completedImports = completed,
            ioDispatcher = Dispatchers.Unconfined
        )
        restored.resumePending()
        restored.submit("content://share/1", restoring = true)

        assertTrue(attempts.isEmpty())
        assertEquals(emptyList<String>(), snapshot!!.get<ArrayList<String>>(IncomingImportController.KEY_PENDING))
    }

    @Test
    fun freshShareOfRecycledUriClearsStaleCompletedMarker() {
        val done = mutableSetOf("content://share/1")
        val completed = object : CompletedImports<String> {
            override fun contains(item: String) = item in done
            override fun add(item: String) { done += item }
            override fun remove(item: String) { done -= item }
        }
        val gate = CompletableDeferred<Result<Any>>()
        val controller = IncomingImportController(
            savedState = SavedStateHandle(), encode = { it }, decode = { it },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            isSupported = { true },
            importer = { gate.await() },
            onEvent = { events += it },
            newEventId = { ++nextId },
            completedImports = completed,
            ioDispatcher = Dispatchers.Unconfined
        )

        controller.submit("content://share/1", restoring = false)

        // Import still running (process could die now): the old marker must not exist anymore.
        assertTrue("content://share/1" !in done)
    }

    @Test
    fun restoringReplayKeepsCompletedMarker() {
        val done = mutableSetOf("content://share/1")
        val completed = object : CompletedImports<String> {
            override fun contains(item: String) = item in done
            override fun add(item: String) { done += item }
            override fun remove(item: String) { done -= item }
        }
        val controller = IncomingImportController(
            savedState = SavedStateHandle(), encode = { it }, decode = { it },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            isSupported = { true },
            importer = { error("must not import") },
            onEvent = { events += it },
            completedImports = completed,
            ioDispatcher = Dispatchers.Unconfined
        )
        controller.submit("content://share/1", restoring = true)
        assertTrue("content://share/1" in done)
    }
}
