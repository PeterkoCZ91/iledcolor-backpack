package com.batoh.feature.backpack

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batoh.core.common.Result
import com.batoh.core.conversion.TextBannerLayout
import com.batoh.core.conversion.TextBannerOptions
import com.batoh.core.conversion.TextBannerPlan
import com.batoh.core.conversion.TextBannerRenderer
import com.batoh.core.data.bluetooth.BackpackPayload
import com.batoh.core.domain.usecase.SaveGifBytesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TextBannerStage { Empty, Rendering, Ready, Error }

data class TextBannerUiState(
    val options: TextBannerOptions = TextBannerOptions(text = ""),
    val stage: TextBannerStage = TextBannerStage.Empty,
    val previewBytes: ByteArray? = null,
    val plan: TextBannerPlan? = null,
    val saving: Boolean = false,
    val savedUri: String? = null,
    /** String resource id of the last error (resolved in the UI so the app language applies). */
    val errorRes: Int? = null,
    /** True once this screen started an upload; the shared upload state is shown only then. */
    val uploadRequested: Boolean = false
)

@HiltViewModel
class TextBannerViewModel @Inject constructor(
    private val saveGifBytes: SaveGifBytesUseCase,
    private val transfers: BackpackTransferManager
) : ViewModel() {
    private val _state = MutableStateFlow(TextBannerUiState())
    val state = _state.asStateFlow()
    val uploadState = transfers.uploadState
    val busy = transfers.busy
    private var renderJob: Job? = null
    private var saveJob: Job? = null

    fun update(options: TextBannerOptions) {
        val limited = options.copy(text = options.text.take(TextBannerLayout.MAX_TEXT_LENGTH))
        if (_state.value.saving || limited == _state.value.options) return
        _state.value = _state.value.copy(options = limited)
        startRender()
    }

    fun retry() = startRender(debounce = false)

    private fun startRender(debounce: Boolean = true) {
        renderJob?.cancel()
        val options = _state.value.options
        if (options.text.isBlank()) {
            _state.value = _state.value.copy(stage = TextBannerStage.Empty, previewBytes = null, plan = null,
                savedUri = null, errorRes = null)
            return
        }
        _state.value = _state.value.copy(stage = TextBannerStage.Rendering, savedUri = null, errorRes = null)
        renderJob = viewModelScope.launch {
            try {
                if (debounce) delay(RENDER_DEBOUNCE_MS)
                val result = withContext(Dispatchers.Default) {
                    val context = currentCoroutineContext()
                    TextBannerRenderer.render(options) { context.ensureActive() }
                }
                ensureActive()
                _state.value = _state.value.copy(stage = TextBannerStage.Ready, previewBytes = result.gifBytes, plan = result.plan)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("TextBanner", "Rendering failed", e)
                _state.value = _state.value.copy(stage = TextBannerStage.Error, previewBytes = null, plan = null,
                    errorRes = R.string.text_banner_error_render)
            }
        }
    }

    /** Saves the rendered GIF to the library through the same repository path as the GIF editor. */
    fun save() {
        val current = _state.value
        if (current.stage != TextBannerStage.Ready || current.saving || current.savedUri != null || saveJob?.isCompleted == false) return
        val bytes = current.previewBytes ?: return
        _state.value = current.copy(saving = true, errorRes = null)
        saveJob = viewModelScope.launch {
            try {
                when (val saved = saveGifBytes(bytes, "batoh_text")) {
                    is Result.Success -> _state.value = _state.value.copy(savedUri = saved.data.toString())
                    is Result.Error -> throw saved.exception
                    is Result.Loading -> error("")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("TextBanner", "Saving failed", e)
                _state.value = _state.value.copy(errorRes = R.string.text_banner_error_save)
            } finally {
                _state.value = _state.value.copy(saving = false)
            }
        }
    }

    /** Wraps the GIF into a programme payload and hands it to the shared transfer manager. */
    fun sendToBackpack() {
        val current = _state.value
        if (current.stage != TextBannerStage.Ready) return
        val bytes = current.previewBytes ?: return
        try {
            val payload = BackpackPayload.fromGif(bytes)
            _state.value = current.copy(uploadRequested = true, errorRes = null)
            // History shows the typed text instead of a generic "Program" label
            transfers.uploadPayload(payload, name = "„${current.options.text.trim().take(24)}“")
        } catch (e: Exception) {
            android.util.Log.w("TextBanner", "Building the payload failed", e)
            _state.value = current.copy(errorRes = R.string.text_banner_error_payload)
        }
    }

    fun cancelUpload() = transfers.cancelUpload()

    private companion object {
        const val RENDER_DEBOUNCE_MS = 250L
    }
}
