package com.batoh.feature.search

import com.batoh.core.common.Result
import com.batoh.core.domain.model.DownloadStatus
import com.batoh.core.domain.model.Gif
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal fun Gif.downloadKey(): String = "${source.lowercase()}|$id"
enum class GifSaveStage { Pending, Running, Success, Error }
data class GifSaveState(val stage: GifSaveStage, val message: String? = null)

/** Saved means WorkManager succeeded, not merely that a download was enqueued. */
internal class SearchDownloads(
    private val scope: CoroutineScope,
    private val save: suspend (String, String) -> Result<String>,
    private val observe: (String) -> Flow<DownloadStatus>,
    restoredWorkIds: Map<String, String> = emptyMap(),
    private val onWorkQueued: (String, String) -> Unit = { _, _ -> }
) {
    private val _states = MutableStateFlow<Map<String, GifSaveState>>(emptyMap())
    val states = _states.asStateFlow()
    private val jobs = mutableMapOf<String, Job>()

    init { restoredWorkIds.forEach { (key, id) -> start(key) { follow(key, id) } } }

    fun save(gif: Gif) {
        val key = gif.downloadKey()
        if (_states.value[key]?.stage == GifSaveStage.Success) return
        start(key) {
            when (val result = save(gif.originalUrl, "GIF_${gif.source}_${gif.id}")) {
                is Result.Success -> {
                    onWorkQueued(key, result.data)
                    follow(key, result.data)
                }
                is Result.Error -> error(result.message ?: "GIF download failed")
                Result.Loading -> error("GIF download was not started")
            }
        }
    }

    private fun start(key: String, work: suspend () -> Unit) {
        if (jobs[key]?.isCompleted == false) return
        _states.update { it + (key to GifSaveState(GifSaveStage.Pending)) }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try { work() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _states.update { it + (key to GifSaveState(GifSaveStage.Error, e.message ?: "GIF download failed")) }
            }
        }
        jobs[key] = job
        job.start()
    }

    private suspend fun follow(key: String, id: String) {
        observe(id).onEach { status ->
            val state = when (status) {
                DownloadStatus.PENDING, DownloadStatus.UNKNOWN -> GifSaveState(GifSaveStage.Pending)
                DownloadStatus.RUNNING -> GifSaveState(GifSaveStage.Running)
                DownloadStatus.SUCCESS -> GifSaveState(GifSaveStage.Success)
                DownloadStatus.FAILED -> GifSaveState(GifSaveStage.Error, "GIF download failed. Tap to retry.")
            }
            _states.update { it + (key to state) }
        }.first { it == DownloadStatus.SUCCESS || it == DownloadStatus.FAILED }
    }
}
