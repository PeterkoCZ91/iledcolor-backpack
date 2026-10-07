package com.batoh.feature.backpack

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.batoh.core.common.Result
import com.batoh.core.conversion.GifConcatOptions
import com.batoh.core.conversion.GifConcatProcessor
import com.batoh.core.conversion.GifInfo
import com.batoh.core.conversion.GifScaleMode
import com.batoh.core.conversion.SafeGifDecoder
import com.batoh.core.domain.usecase.SaveGifBytesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the screen produces: one merged GIF, or one separate programme per GIF sent as a sequence. */
enum class ChainMode { Merge, Sequence }

enum class ChainStage { Idle, Rendering, Ready, Error, Cancelled }

data class GifChainUiState(
    val items: List<ChainItem> = emptyList(),
    val mode: ChainMode = ChainMode.Merge,
    val sequenceStatus: SequenceStatus? = null,
    val scaleMode: GifScaleMode = GifScaleMode.Fit,
    val pauseMs: Int = 0,
    /** Null = keep the original frame delays. */
    val speedOverrideMs: Int? = null,
    val stage: ChainStage = ChainStage.Idle,
    val previewBytes: ByteArray? = null,
    val info: GifInfo? = null,
    val status: ChainStatus? = null,
    val error: GifChainError? = null,
    val saving: Boolean = false,
    val savedUri: String? = null
) {
    val canSave: Boolean
        get() = mode == ChainMode.Merge && stage == ChainStage.Ready && previewBytes != null &&
            status?.allowsOutput == true && !saving

    val canSendSequence: Boolean
        get() = mode == ChainMode.Sequence && stage == ChainStage.Ready && sequenceStatus?.allowsOutput == true
}

@HiltViewModel
class GifChainViewModel @Inject constructor(
    application: Application,
    private val saveGifBytes: SaveGifBytesUseCase,
    private val transfers: BackpackTransferManager
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(GifChainUiState())
    val state = _state.asStateFlow()
    private val _sendEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val sendEvents = _sendEvents.asSharedFlow()
    private val _sequenceEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emitted once the sequence is staged for upload; the screen then opens the backpack screen. */
    val sequenceEvents = _sequenceEvents.asSharedFlow()

    private var loaded = false
    private var nextId = 0L
    private var renderJob: Job? = null
    private var saveJob: Job? = null
    private val sources = ConcurrentHashMap<String, ByteArray>()
    /** Programmes already scaled to 64x64, so reordering the list does not convert the GIFs again. */
    private val prepared = ConcurrentHashMap<String, PreparedProgramme>()
    @Volatile private var preparedSequence: List<PreparedProgramme>? = null

    fun load(uriArg: String?) {
        if (loaded) return
        loaded = true
        val items = GifChainPlan.parseUris(uriArg).map { ChainItem(nextId++, it) }
        _state.value = _state.value.copy(items = items)
        schedule()
    }

    fun moveUp(index: Int) = edit { it.copy(items = GifChainPlan.moveUp(it.items, index)) }
    fun moveDown(index: Int) = edit { it.copy(items = GifChainPlan.moveDown(it.items, index)) }
    fun remove(index: Int) = edit { it.copy(items = GifChainPlan.remove(it.items, index)) }
    fun setMode(mode: ChainMode) = edit { it.copy(mode = mode) }
    fun setScaleMode(mode: GifScaleMode) = edit { it.copy(scaleMode = mode) }
    fun setPause(ms: Int) = edit { it.copy(pauseMs = GifChainPlan.clampPause(ms)) }
    fun setSpeedOverride(ms: Int?) = edit { it.copy(speedOverrideMs = ms?.let(GifChainPlan::clampSpeed)) }
    fun retry() = schedule()

    fun cancel() {
        renderJob?.cancel()
        if (_state.value.stage == ChainStage.Rendering) {
            _state.value = _state.value.copy(stage = ChainStage.Cancelled, previewBytes = null, status = null,
                sequenceStatus = null, error = null)
        }
    }

    private inline fun edit(change: (GifChainUiState) -> GifChainUiState) {
        val current = _state.value
        if (current.saving) return
        val next = change(current)
        if (next == current) return
        _state.value = next
        schedule()
    }

    /** Starts re-encoding after a debounce; a newer change cancels the older run. */
    private fun schedule() {
        renderJob?.cancel()
        val current = _state.value
        if (current.saving) return
        preparedSequence = null
        if (!GifChainPlan.canRender(current.items.size)) {
            _state.value = current.copy(stage = ChainStage.Idle, previewBytes = null, info = null,
                status = null, sequenceStatus = null, error = null, savedUri = null)
            return
        }
        val items = current.items
        if (current.mode == ChainMode.Sequence) {
            scheduleSequence(current, items)
            return
        }
        val options = GifConcatOptions(
            scaleMode = current.scaleMode,
            pauseBetweenMs = current.pauseMs,
            frameDelayOverrideMs = current.speedOverrideMs
        )
        _state.value = current.copy(stage = ChainStage.Rendering, previewBytes = null, info = null,
            status = null, sequenceStatus = null, error = null, savedUri = null)
        renderJob = viewModelScope.launch {
            try {
                delay(DEBOUNCE_MS)
                val result = withContext(Dispatchers.IO) {
                    val context = currentCoroutineContext()
                    val bytes = items.mapIndexed { index, item -> readSource(index, item.uri) }
                    context.ensureActive()
                    GifConcatProcessor.concat(
                        bytes.map { GifConcatProcessor.Source(it) }, options, { context.ensureActive() }
                    )
                }
                ensureActive()
                val status = GifChainPlan.status(result.info.frameCount, result.gifBytes.size.toLong())
                _state.value = _state.value.copy(
                    stage = ChainStage.Ready, previewBytes = result.gifBytes, info = result.info, status = status
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(stage = ChainStage.Error, error = GifChainPlan.errorFor(e))
            }
        }
    }

    /** Scales every GIF to a 64x64 programme (the single-upload path) and estimates the whole sequence. */
    private fun scheduleSequence(current: GifChainUiState, items: List<ChainItem>) {
        _state.value = current.copy(stage = ChainStage.Rendering, previewBytes = null, info = null,
            status = null, sequenceStatus = null, error = null, savedUri = null)
        renderJob = viewModelScope.launch {
            try {
                delay(DEBOUNCE_MS)
                val programmes = withContext(Dispatchers.IO) {
                    val context = currentCoroutineContext()
                    GifSequencePlan.prepareAll(items.map { it.uri }) { index, uri ->
                        context.ensureActive()
                        prepared[uri] ?: try {
                            transfers.prepareProgramme(readSource(index, uri)).also { prepared[uri] = it }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            throw SequenceItemException(index, e)
                        }
                    }
                }
                ensureActive()
                preparedSequence = programmes
                _state.value = _state.value.copy(
                    stage = ChainStage.Ready, sequenceStatus = GifSequencePlan.assess(programmes)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = if (e is SequenceItemException) GifSequencePlan.errorFor(e.cause ?: e, e.index)
                else GifChainPlan.errorFor(e)
                _state.value = _state.value.copy(stage = ChainStage.Error, error = error)
            }
        }
    }

    /** Wraps a failure of one sequence item so the error can name its position; cancellation is never wrapped. */
    private class SequenceItemException(val index: Int, cause: Throwable) : Exception("Sequence item $index failed", cause)

    /** Stages the prepared programmes in playing order and asks the screen to open the backpack screen. */
    fun sendSequence() {
        val current = _state.value
        val programmes = preparedSequence
        if (!current.canSendSequence || programmes == null) return
        transfers.stageSequence(programmes.map { it.payload })
        _sequenceEvents.tryEmit(Unit)
    }

    private suspend fun readSource(index: Int, uri: String): ByteArray {
        sources[uri]?.let { return it }
        val context = currentCoroutineContext()
        val bytes = try {
            getApplication<Application>().contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    context.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size().toLong() + count > SafeGifDecoder.MAX_BYTES) {
                        throw ChainSourceException(index, tooLarge = true)
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: throw ChainSourceException(index)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ChainSourceException) {
            throw e
        } catch (e: Exception) {
            throw ChainSourceException(index, cause = e)
        }
        sources[uri] = bytes
        val total = sources.values.sumOf { it.size.toLong() }
        if (total > GifChainPlan.MAX_TOTAL_SOURCE_BYTES) {
            sources.remove(uri)
            throw ChainSourceException(index, tooLarge = true)
        }
        return bytes
    }

    /** Saves the result to the collection; with [sendToBackpack] it then triggers sending the saved GIF. */
    fun save(sendToBackpack: Boolean) {
        val current = _state.value
        if (!current.canSave || saveJob?.isCompleted == false) return
        current.savedUri?.let { uri ->
            if (sendToBackpack) _sendEvents.tryEmit(uri)
            return
        }
        val bytes = current.previewBytes ?: return
        _state.value = current.copy(saving = true, error = null)
        saveJob = viewModelScope.launch {
            try {
                when (val saved = saveGifBytes(bytes, SAVE_TITLE)) {
                    is Result.Success -> {
                        val uri = saved.data.toString()
                        _state.value = _state.value.copy(savedUri = uri)
                        if (sendToBackpack) _sendEvents.tryEmit(uri)
                    }
                    is Result.Error -> _state.value = _state.value.copy(error = GifChainError.SaveFailed)
                    is Result.Loading -> _state.value = _state.value.copy(error = GifChainError.SaveFailed)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = GifChainError.SaveFailed)
            } finally {
                _state.value = _state.value.copy(saving = false)
            }
        }
    }

    fun dismissError() {
        if (_state.value.error == GifChainError.SaveFailed) _state.value = _state.value.copy(error = null)
    }

    private companion object {
        const val DEBOUNCE_MS = 400L
        const val SAVE_TITLE = "batoh_spojeny"
    }
}
