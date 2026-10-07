package com.batoh.feature.convert

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batoh.core.common.Result
import com.batoh.core.conversion.VideoToGifConverter
import com.batoh.core.domain.usecase.SaveGifBytesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ConvertUiState {
    object Idle : ConvertUiState
    data class Downloading(val progress: Int) : ConvertUiState
    data class Converting(val progress: Int) : ConvertUiState
    object Cancelling : ConvertUiState
    data class Preview(val gifUri: Uri) : ConvertUiState
    data class Error(val error: ConvertError) : ConvertUiState
}

@HiltViewModel
class ConvertViewModel @Inject constructor(
    private val converter: VideoToGifConverter,
    private val saveGifBytesUseCase: SaveGifBytesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ConvertUiState>(ConvertUiState.Idle)
    val uiState: StateFlow<ConvertUiState> = _uiState

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url
    private var conversionJob: Job? = null

    fun onUrlChange(value: String) {
        _url.value = value
        _uiState.value = _uiState.value.afterUrlEdit()
    }

    fun onConvertClick() {
        // Re-entrancy guard — the IME "Go" action can fire while a conversion is
        // already running (the button is hidden, the keyboard action is not)
        val state = _uiState.value
        if (conversionJob?.isCompleted == false || state is ConvertUiState.Cancelling) return

        val inputUrl = _url.value.trim()
        if (inputUrl.isBlank()) {
            _uiState.value = ConvertUiState.Error(ConvertError.EmptyUrl)
            return
        }
        if (!isValidVideoUrl(inputUrl)) {
            _uiState.value = ConvertUiState.Error(ConvertError.InvalidUrl)
            return
        }

        conversionJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                _uiState.value = ConvertUiState.Downloading(0)

                val gifBytes = converter.convertFromUrl(inputUrl) { progress ->
                    when {
                        progress <= 50 -> _uiState.value =
                            ConvertUiState.Downloading(progress * 2)
                        else -> _uiState.value =
                            ConvertUiState.Converting((progress - 50) * 2)
                    }
                }

                _uiState.value = ConvertUiState.Converting(100)

                val title = "batoh_${System.currentTimeMillis()}"
                when (val result = saveGifBytesUseCase(gifBytes, title)) {
                    is Result.Success -> _uiState.value = ConvertUiState.Preview(result.data)
                    is Result.Error -> _uiState.value =
                        ConvertUiState.Error(ConvertError.SaveFailed)
                    // Loading from a one-shot use case is unexpected — surface it instead of
                    // silently staying at Converting(100) forever (frozen spinner)
                    is Result.Loading -> _uiState.value =
                        ConvertUiState.Error(ConvertError.SaveFailed)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                // Not an Exception: without this the process would die on a huge video.
                logFailure(e)
                _uiState.value = ConvertUiState.Error(classifyConvertError(e))
            } catch (e: Exception) {
                logFailure(e)
                _uiState.value = ConvertUiState.Error(classifyConvertError(e))
            }
        }
        conversionJob?.start()
    }

    private fun logFailure(e: Throwable) {
        // android.util.Log is not available in plain JVM tests
        runCatching { android.util.Log.w("ConvertVM", "Video to GIF failed", e) }
    }

    fun cancelConversion() {
        val job = conversionJob ?: return
        if (job.isCompleted || _uiState.value is ConvertUiState.Cancelling) return
        _uiState.value = ConvertUiState.Cancelling
        job.cancel()
        viewModelScope.launch {
            job.join()
            _uiState.value = ConvertUiState.Idle
        }
    }

    fun onReset() {
        if (conversionJob?.isCompleted == false) return
        _uiState.value = ConvertUiState.Idle
        _url.value = ""
    }
}
