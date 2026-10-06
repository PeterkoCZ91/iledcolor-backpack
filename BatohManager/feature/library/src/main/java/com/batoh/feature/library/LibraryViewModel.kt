package com.batoh.feature.library

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import com.batoh.core.common.Result
import com.batoh.core.domain.model.Gif
import com.batoh.core.domain.usecase.DeleteGifUseCase
import com.batoh.core.domain.usecase.GetLocalGifsUseCase
import com.batoh.core.domain.usecase.ImportGifUseCase
import com.batoh.core.domain.usecase.InspectGifUseCase
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

sealed interface LibraryUiState {
    object Loading : LibraryUiState
    data class Success(val gifs: List<Gif>) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
    object Empty : LibraryUiState
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    application: Application,
    getLocalGifsUseCase: GetLocalGifsUseCase,
    private val deleteGifUseCase: DeleteGifUseCase,
    private val importGifUseCase: ImportGifUseCase,
    private val inspectGifUseCase: InspectGifUseCase
) : AndroidViewModel(application) {
    private val _previewDiagnoses = MutableStateFlow<Map<String, PreviewDiagnosis>>(emptyMap())
    /** Keyed by [Gif.id]; only tiles whose preview failed have an entry. */
    val previewDiagnoses = _previewDiagnoses.asStateFlow()
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage = _actionMessage.asStateFlow()
    private val _importing = MutableStateFlow(false)
    val importing = _importing.asStateFlow()
    private val _deleteConsent = MutableStateFlow<IntentSender?>(null)
    val deleteConsent = _deleteConsent.asStateFlow()
    private var pendingDelete: Gif? = null
    fun showMessage(message: String) { _actionMessage.value = message }

    // Bumped by retry() to re-subscribe to the library source after a load error.
    private val reloadTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LibraryUiState> = reloadTrigger
        .flatMapLatest { getLocalGifsUseCase() }
        .map { result ->
            when (result) {
                is Result.Loading -> LibraryUiState.Loading
                is Result.Success -> {
                    if (result.data.isEmpty()) LibraryUiState.Empty else LibraryUiState.Success(result.data)
                }
                is Result.Error -> LibraryUiState.Error(localizedString(R.string.library_load_failed))
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LibraryUiState.Loading
        )

    /** Recovery action for [LibraryUiState.Error]: reload the local library. */
    fun retry() { reloadTrigger.value++ }

    fun deleteGif(gif: Gif) {
        if (pendingDelete != null) return
        viewModelScope.launch {
            when (val result = deleteGifUseCase(gif)) {
                is Result.Success -> _actionMessage.value = localizedString(R.string.library_delete_success)
                is Result.Error -> {
                    val exception = result.exception
                    try {
                        val sender = when {
                            Build.VERSION.SDK_INT >= 30 && exception is SecurityException ->
                                MediaStore.createDeleteRequest(getApplication<Application>().contentResolver,
                                    listOf(Uri.parse(gif.originalUrl))).intentSender
                            Build.VERSION.SDK_INT == 29 && exception is android.app.RecoverableSecurityException ->
                                exception.userAction.actionIntent.intentSender
                            else -> null
                        }
                        if (sender != null) {
                            pendingDelete = gif
                            _deleteConsent.value = sender
                        } else _actionMessage.value = localizedString(R.string.library_delete_failed)
                    } catch (e: Exception) {
                        _actionMessage.value = localizedString(R.string.library_delete_failed)
                    }
                }
                Result.Loading -> _actionMessage.value = localizedString(R.string.library_delete_incomplete)
            }
        }
    }

    /** Called when Coil cannot show a tile; reads the file to explain why and gate edit/send. */
    fun onPreviewFailed(gif: Gif) {
        if (_previewDiagnoses.value.containsKey(gif.id)) return
        _previewDiagnoses.value = _previewDiagnoses.value + (gif.id to PreviewDiagnosis.Checking)
        viewModelScope.launch {
            val diagnosis = try {
                inspectGifUseCase(gif).toDiagnosis()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                PreviewDiagnosis.Broken(com.batoh.core.domain.model.GifFileProblem.UNREADABLE)
            }
            if (_previewDiagnoses.value[gif.id] == PreviewDiagnosis.Checking) {
                _previewDiagnoses.value = _previewDiagnoses.value + (gif.id to diagnosis)
            }
        }
    }

    fun onPreviewLoaded(gif: Gif) {
        if (_previewDiagnoses.value.containsKey(gif.id)) _previewDiagnoses.value = _previewDiagnoses.value - gif.id
    }

    fun deleteConsentLaunched() { _deleteConsent.value = null }
    fun deleteConsentResult(approved: Boolean) {
        val gif = pendingDelete
        pendingDelete = null
        if (!approved) _actionMessage.value = localizedString(R.string.library_delete_cancelled)
        else if (Build.VERSION.SDK_INT == 29 && gif != null) deleteGif(gif)
        else _actionMessage.value = localizedString(R.string.library_delete_success)
    }

    fun importGif(uri: Uri) {
        if (_importing.value) return
        _importing.value = true
        _actionMessage.value = localizedString(R.string.library_importing_message)
        viewModelScope.launch {
            try {
                _actionMessage.value = when (val result = importGifUseCase(uri)) {
                    is Result.Success -> localizedString(R.string.library_import_success)
                    is Result.Error -> localizedString(R.string.library_import_failed)
                    Result.Loading -> localizedString(R.string.library_import_incomplete)
                }
            } finally { _importing.value = false }
        }
    }

    private fun localizedString(id: Int): String =
        ContextCompat.getContextForLanguage(getApplication<Application>()).getString(id)
}
