package com.batoh.manager

import android.net.Uri
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batoh.core.common.Result
import com.batoh.core.domain.usecase.ImportGifUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IncomingGifState(val event: Long = 0, val message: String? = null, val copiedUri: String? = null)

@HiltViewModel
class IncomingGifViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val importGifUseCase: ImportGifUseCase,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val _state = MutableStateFlow(IncomingGifState())
    val state = _state.asStateFlow()
    private val incomingImports = IncomingImportQueue<Uri>()

    fun navigationHandled(event: Long, copiedUri: String?) {
        if (_state.value.event == event && _state.value.copiedUri == copiedUri)
            _state.value = _state.value.copy(event = 0, copiedUri = null)
    }

    fun dismissMessage() { _state.value = _state.value.copy(message = null) }
    fun reportError(message: String) {
        _state.value = IncomingGifState(System.nanoTime(), message)
    }

    fun importShared(uri: Uri, restoring: Boolean) {
        when (val admission = incomingImports.submit(
            item = uri,
            restoring = restoring,
            previousImportCompleted = savedState.get<Boolean>("share_completed") == true
        )) {
            is IncomingImportQueue.Admission.Start -> startImport(admission.item)
            IncomingImportQueue.Admission.Queued,
            IncomingImportQueue.Admission.Ignored -> Unit
        }
    }

    private fun startImport(uri: Uri) {
        savedState["share_completed"] = false
        val event = System.nanoTime()
        _state.value = IncomingGifState(event, message(R.string.incoming_importing))
        viewModelScope.launch {
            try {
                _state.value = when (val result = importGifUseCase(uri)) {
                    is Result.Success -> IncomingGifState(event, message(R.string.incoming_import_success), result.data.toString())
                    is Result.Error -> IncomingGifState(event, message(R.string.incoming_import_failed))
                    Result.Loading -> IncomingGifState(event, message(R.string.incoming_import_incomplete))
                }
                savedState["share_completed"] = true
            } finally {
                incomingImports.finish()?.let(::startImport)
            }
        }
    }

    private fun message(id: Int): String = ContextCompat.getContextForLanguage(appContext).getString(id)
}
