package com.batoh.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import com.batoh.core.common.Result
import com.batoh.core.conversion.VideoToGifConverter
import com.batoh.core.domain.model.DownloadStatus
import com.batoh.core.domain.usecase.ObserveDownloadUseCase
import com.batoh.core.domain.usecase.SaveGifBytesUseCase
import com.batoh.core.domain.usecase.SaveGifUseCase
import com.batoh.core.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DetailUiState {
    object Idle : DetailUiState
    object Saving : DetailUiState
    object Saved : DetailUiState
    data class Converting(val progress: Int) : DetailUiState
    object ConvertedAndSaved : DetailUiState
    data class Error(val message: String) : DetailUiState
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val saveGifUseCase: SaveGifUseCase,
    private val observeDownloadUseCase: ObserveDownloadUseCase,
    private val videoToGifConverter: VideoToGifConverter,
    private val saveGifBytesUseCase: SaveGifBytesUseCase
) : ViewModel() {

    private val _gifUrl: String = checkNotNull(savedStateHandle[Screen.Detail.ARG_GIF_URL])
    val gifUrl: String = _gifUrl

    private val _rawMp4Url: String? = savedStateHandle[Screen.Detail.ARG_MP4_URL]
    val mp4Url: String = _rawMp4Url ?: ""

    val isLocal: Boolean = gifUrl.startsWith("content") || gifUrl.startsWith("file")

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Idle)
    val uiState: StateFlow<DetailUiState> = _uiState

    /** Save the original GIF to library (existing flow via WorkManager). */
    fun onSaveClick() {
        if (_uiState.value is DetailUiState.Saving) return
        viewModelScope.launch {
            _uiState.value = DetailUiState.Saving
            val result = saveGifUseCase(gifUrl, "GIF_${System.currentTimeMillis()}")
            when (result) {
                is Result.Success -> {
                    // WorkManager's flow is hot and never completes — collect only until the
                    // first terminal status, otherwise this coroutine leaks and a second
                    // onSaveClick would run two observers racing over _uiState
                    observeDownloadUseCase(result.data)
                        .transformWhile { status ->
                            emit(status)
                            status == DownloadStatus.PENDING || status == DownloadStatus.RUNNING
                        }
                        .collect { status ->
                            _uiState.value = when (status) {
                                DownloadStatus.PENDING, DownloadStatus.RUNNING -> DetailUiState.Saving
                                DownloadStatus.SUCCESS -> DetailUiState.Saved
                                DownloadStatus.FAILED -> DetailUiState.Error(message(R.string.detail_download_failed))
                                DownloadStatus.UNKNOWN -> DetailUiState.Error(message(R.string.detail_unknown_status))
                            }
                        }
                }
                is Result.Error -> _uiState.value = DetailUiState.Error(message(R.string.detail_save_failed))
                is Result.Loading -> _uiState.value = DetailUiState.Saving
            }
        }
    }

    /** Download the Giphy MP4, convert to 64×64 GIF and save to library. */
    fun onConvertForBackpackClick() {
        if (mp4Url.isBlank()) return
        if (_uiState.value is DetailUiState.Converting) return

        viewModelScope.launch {
            try {
                _uiState.value = DetailUiState.Converting(0)

                val gifBytes = videoToGifConverter.convertFromUrl(mp4Url) { progress ->
                    _uiState.value = DetailUiState.Converting(progress)
                }

                val title = "batoh_${System.currentTimeMillis()}"
                when (val result = saveGifBytesUseCase(gifBytes, title)) {
                    is Result.Success -> _uiState.value = DetailUiState.ConvertedAndSaved
                    is Result.Error -> _uiState.value =
                        DetailUiState.Error(message(R.string.detail_save_failed))
                    // Loading from a one-shot use case = never resolves — surface as error
                    // instead of freezing at Converting(100)
                    is Result.Loading -> _uiState.value =
                        DetailUiState.Error(message(R.string.detail_save_incomplete))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = DetailUiState.Error(message(R.string.detail_conversion_failed))
            }
        }
    }

    private fun message(id: Int): String = ContextCompat.getContextForLanguage(appContext).getString(id)
}
