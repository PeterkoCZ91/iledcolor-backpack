package com.batoh.feature.search

import com.batoh.core.common.Result
import com.batoh.core.domain.model.DownloadStatus
import com.batoh.core.domain.model.Gif
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class SearchDownloadsTest {
    private fun gif(source: String = "giphy") = Gif("same-id", "GIF", "preview", "download", width = 64, height = 64, source = source)

    @Test fun enqueueIsPendingAndOnlyWorkerSuccessMarksSaved() = runBlocking {
        val status = MutableStateFlow(DownloadStatus.PENDING)
        var saves = 0
        val downloads = SearchDownloads(this, { _, _ -> saves++; Result.Success("work") }, { status })
        downloads.save(gif())
        yield()
        assertEquals(GifSaveStage.Pending, downloads.states.value[gif().downloadKey()]?.stage)
        downloads.save(gif())
        assertEquals(1, saves)
        status.value = DownloadStatus.RUNNING
        yield()
        assertEquals(GifSaveStage.Running, downloads.states.value[gif().downloadKey()]?.stage)
        status.value = DownloadStatus.SUCCESS
        yield()
        assertEquals(GifSaveStage.Success, downloads.states.value[gif().downloadKey()]?.stage)
        downloads.save(gif())
        assertEquals(1, saves)
    }

    @Test fun failedWorkerAllowsRetryAndSameIdFromDifferentSourceIsIndependent() = runBlocking {
        var saves = 0
        val downloads = SearchDownloads(this,
            { _, _ -> saves++; Result.Success("work-$saves") },
            { id -> flowOf(if (id == "work-1") DownloadStatus.FAILED else DownloadStatus.SUCCESS) })
        downloads.save(gif())
        yield()
        assertEquals(GifSaveStage.Error, downloads.states.value[gif().downloadKey()]?.stage)
        downloads.save(gif())
        yield()
        assertEquals(GifSaveStage.Success, downloads.states.value[gif().downloadKey()]?.stage)
        downloads.save(gif("klipy"))
        yield()
        assertEquals(3, saves)
        assertEquals(2, downloads.states.value.size)
    }

    @Test fun enqueueErrorIsVisibleAndRetryable() = runBlocking {
        var attempts = 0
        val downloads = SearchDownloads(this,
            { _, _ -> if (++attempts == 1) Result.Error(Exception("offline"), "No connection") else Result.Success("work") },
            { flowOf(DownloadStatus.SUCCESS) })
        downloads.save(gif())
        yield()
        assertEquals(GifSaveStage.Error, downloads.states.value[gif().downloadKey()]?.stage)
        downloads.save(gif())
        yield()
        assertEquals(GifSaveStage.Success, downloads.states.value[gif().downloadKey()]?.stage)
    }

    @Test fun restoredWorkIsObservedWithoutEnqueuingAgain() = runBlocking {
        val downloads = SearchDownloads(this,
            { _, _ -> error("Must not enqueue restored work") },
            { id -> assertEquals("restored-id", id); flowOf(DownloadStatus.RUNNING, DownloadStatus.SUCCESS) },
            mapOf(gif().downloadKey() to "restored-id"))
        yield()
        assertEquals(GifSaveStage.Success, downloads.states.value[gif().downloadKey()]?.stage)
    }

    @Test fun sendWaitsForSaveThenEmitsLocalUriOnce() = runBlocking {
        val status = MutableStateFlow(DownloadStatus.PENDING)
        var saves = 0
        val downloads = SearchDownloads(this, { _, _ -> saves++; Result.Success("work") }, { status },
            resultUri = { id -> "content://media/$id" })
        val received = mutableListOf<String>()
        val collector = launch { downloads.sendReady.collect { received += it } }
        downloads.saveAndSend(gif())
        downloads.saveAndSend(gif())
        yield()
        assertEquals(setOf(gif().downloadKey()), downloads.sending.value)
        assertTrue(received.isEmpty())
        status.value = DownloadStatus.SUCCESS
        yield(); yield()
        assertEquals(listOf("content://media/work"), received)
        assertEquals(1, saves)
        assertTrue(downloads.sending.value.isEmpty())
        collector.cancel()
    }

    @Test fun alreadySavedGifIsSentWithoutSavingAgain() = runBlocking {
        var saves = 0
        val downloads = SearchDownloads(this, { _, _ -> saves++; Result.Success("work") },
            { flowOf(DownloadStatus.SUCCESS) }, resultUri = { "content://media/1" })
        val received = mutableListOf<String>()
        val collector = launch { downloads.sendReady.collect { received += it } }
        downloads.save(gif())
        yield()
        downloads.saveAndSend(gif())
        yield(); yield()
        assertEquals(listOf("content://media/1"), received)
        assertEquals(1, saves)
        collector.cancel()
    }

    @Test fun failedSaveCancelsSendAndUnresolvableUriIsAnError() = runBlocking {
        val failed = SearchDownloads(this, { _, _ -> Result.Success("work") }, { flowOf(DownloadStatus.FAILED) },
            resultUri = { "content://media/1" })
        failed.saveAndSend(gif())
        yield(); yield()
        assertTrue(failed.sending.value.isEmpty())
        assertEquals(GifSaveStage.Error, failed.states.value[gif().downloadKey()]?.stage)

        val noUri = SearchDownloads(this, { _, _ -> Result.Success("work") }, { flowOf(DownloadStatus.SUCCESS) },
            resultUri = { null })
        noUri.saveAndSend(gif())
        yield(); yield()
        assertTrue(noUri.sending.value.isEmpty())
        assertEquals(GifSaveStage.Error, noUri.states.value[gif().downloadKey()]?.stage)
    }

    @Test fun enqueueErrorClearsSendingAndPlainSaveNeverSends() = runBlocking {
        val downloads = SearchDownloads(this, { _, _ -> Result.Error(Exception("offline"), "No connection") },
            { flowOf(DownloadStatus.SUCCESS) }, resultUri = { "content://media/1" })
        downloads.saveAndSend(gif())
        yield()
        assertTrue(downloads.sending.value.isEmpty())

        val plain = SearchDownloads(this, { _, _ -> Result.Success("work") }, { flowOf(DownloadStatus.SUCCESS) },
            resultUri = { "content://media/1" })
        val received = mutableListOf<String>()
        val collector = launch { plain.sendReady.collect { received += it } }
        plain.save(gif())
        yield(); yield()
        assertTrue(received.isEmpty())
        collector.cancel()
    }
}
