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

enum class ChainStage { Idle, Rendering, Ready, Error, Cancelled }

data class GifChainUiState(
    val items: List<ChainItem> = emptyList(),
    val scaleMode: GifScaleMode = GifScaleMode.Fit,
    val pauseMs: Int = 0,
    /** Null = zachovat původní prodlevy snímků. */
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
        get() = stage == ChainStage.Ready && previewBytes != null && status?.allowsOutput == true && !saving
}

@HiltViewModel
class GifChainViewModel @Inject constructor(
    application: Application,
    private val saveGifBytes: SaveGifBytesUseCase
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(GifChainUiState())
    val state = _state.asStateFlow()
    private val _sendEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val sendEvents = _sendEvents.asSharedFlow()

    private var loaded = false
    private var nextId = 0L
    private var renderJob: Job? = null
    private var saveJob: Job? = null
    private val sources = ConcurrentHashMap<String, ByteArray>()

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
    fun setScaleMode(mode: GifScaleMode) = edit { it.copy(scaleMode = mode) }
    fun setPause(ms: Int) = edit { it.copy(pauseMs = GifChainPlan.clampPause(ms)) }
    fun setSpeedOverride(ms: Int?) = edit { it.copy(speedOverrideMs = ms?.let(GifChainPlan::clampSpeed)) }
    fun retry() = schedule()

    fun cancel() {
        renderJob?.cancel()
        if (_state.value.stage == ChainStage.Rendering) {
            _state.value = _state.value.copy(stage = ChainStage.Cancelled, previewBytes = null, status = null, error = null)
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

    /** Spustí překódování s odstupem; novější změna starší běh zruší. */
    private fun schedule() {
        renderJob?.cancel()
        val current = _state.value
        if (current.saving) return
        if (!GifChainPlan.canRender(current.items.size)) {
            _state.value = current.copy(stage = ChainStage.Idle, previewBytes = null, info = null,
                status = null, error = null, savedUri = null)
            return
        }
        val items = current.items
        val options = GifConcatOptions(
            scaleMode = current.scaleMode,
            pauseBetweenMs = current.pauseMs,
            frameDelayOverrideMs = current.speedOverrideMs
        )
        _state.value = current.copy(stage = ChainStage.Rendering, previewBytes = null, info = null,
            status = null, error = null, savedUri = null)
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

    /** Uloží výsledek do sbírky; s [sendToBackpack] pak vyvolá odeslání uloženého GIFu. */
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
