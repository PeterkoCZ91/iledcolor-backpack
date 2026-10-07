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
}
