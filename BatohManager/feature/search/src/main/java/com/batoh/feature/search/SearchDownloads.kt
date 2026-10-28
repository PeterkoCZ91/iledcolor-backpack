package com.batoh.feature.search

import com.batoh.core.common.Result
import com.batoh.core.domain.model.DownloadStatus
import com.batoh.core.domain.model.Gif
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

internal fun Gif.downloadKey(): String = "${source.lowercase()}|$id"
enum class GifSaveStage { Pending, Running, Success, Error }
data class GifSaveState(val stage: GifSaveStage)

/** Saved means WorkManager succeeded, not merely that a download was enqueued. */
internal class SearchDownloads(
    private val scope: CoroutineScope,
    private val save: suspend (String, String) -> Result<String>,
    private val observe: (String) -> Flow<DownloadStatus>,
    restoredWorkIds: Map<String, String> = emptyMap(),
    private val onWorkQueued: (String, String) -> Unit = { _, _ -> },
    /** Local collection URI of a finished download (work id -> URI), null when it cannot be resolved. */
    private val resultUri: suspend (String) -> String? = { null }
) {
    private val _states = MutableStateFlow<Map<String, GifSaveState>>(emptyMap())
    val states = _states.asStateFlow()
    private val jobs = mutableMapOf<String, Job>()
    private val workIds = mutableMapOf<String, String>()
    private val _sending = MutableStateFlow<Set<String>>(emptySet())
    /** Keys whose "send to panel" was requested and is still waiting for the save to finish. */
    val sending = _sending.asStateFlow()
    private val _sendReady = Channel<String>(Channel.UNLIMITED)
    /** Local URIs of GIFs that are saved and ready to be sent to a panel (each requested send emits once). */
    val sendReady: Flow<String> = _sendReady.receiveAsFlow()

    init { restoredWorkIds.forEach { (key, id) -> workIds[key] = id; start(key) { follow(key, id) } } }

    /** Saves the GIF unless it is already saved, then emits its local URI on [sendReady]. */
    fun saveAndSend(gif: Gif) {
        val key = gif.downloadKey()
        if (key in _sending.value) return
        _sending.update { it + key }
        val workId = workIds[key]
        if (_states.value[key]?.stage == GifSaveStage.Success && workId != null) {
            scope.launch { finishSend(key, workId) }
        } else {
            save(gif)
        }
    }

    private suspend fun finishSend(key: String, workId: String) {
        val uri = try { resultUri(workId) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
        _sending.update { it - key }
        if (uri != null) _sendReady.trySend(uri)
        else _states.update { it + (key to GifSaveState(GifSaveStage.Error)) }
    }

    fun save(gif: Gif) {
        val key = gif.downloadKey()
        if (_states.value[key]?.stage == GifSaveStage.Success) return
        start(key) {
            when (val result = save(gif.originalUrl, "GIF_${gif.source}_${gif.id}")) {
                is Result.Success -> {
                    workIds[key] = result.data
                    onWorkQueued(key, result.data)
                    follow(key, result.data)
                }
                is Result.Error -> throw result.exception
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
                _sending.update { it - key }
                _states.update { it + (key to GifSaveState(GifSaveStage.Error)) }
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
                DownloadStatus.FAILED -> GifSaveState(GifSaveStage.Error)
            }
            _states.update { it + (key to state) }
        }.first { it == DownloadStatus.SUCCESS || it == DownloadStatus.FAILED }
        .let { terminal ->
            if (key !in _sending.value) return@let
            if (terminal == DownloadStatus.SUCCESS) finishSend(key, id) else _sending.update { it - key }
        }
    }
}
