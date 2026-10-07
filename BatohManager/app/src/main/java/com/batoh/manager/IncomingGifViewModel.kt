package com.batoh.manager

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batoh.core.domain.usecase.ImportGifUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class IncomingGifState(val event: Long = 0, val message: String? = null, val copiedUri: String? = null)

@HiltViewModel
class IncomingGifViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val importGifUseCase: ImportGifUseCase,
    savedState: SavedStateHandle
) : ViewModel() {
    private val _state = MutableStateFlow(IncomingGifState())
    val state = _state.asStateFlow()
    private val controller = IncomingImportController<Uri>(
        savedState = savedState,
        encode = Uri::toString,
        decode = Uri::parse,
        scope = viewModelScope,
        isSupported = { it.scheme == ContentResolver.SCHEME_CONTENT },
        importer = { importGifUseCase(it) },
        onEvent = ::render,
        completedImports = PrefsCompletedImports(
            appContext.getSharedPreferences("incoming_imports", Context.MODE_PRIVATE),
            Uri::toString
        )
    )

    init {
        // After process death the URI grant may be gone; the controller retries and reports
        // a clear "share again" message instead of silently dropping the share.
        controller.resumePending()
    }

    fun navigationHandled(event: Long, copiedUri: String?) {
        if (_state.value.event == event && _state.value.copiedUri == copiedUri)
            _state.value = _state.value.copy(event = 0, copiedUri = null)
    }

    fun dismissMessage() { _state.value = _state.value.copy(message = null) }
    fun reportError(message: String) {
        _state.value = IncomingGifState(System.nanoTime(), message)
    }

    fun importShared(uri: Uri, restoring: Boolean) = controller.submit(uri, restoring)

    private fun render(event: IncomingImportEvent) {
        _state.value = when (event) {
            is IncomingImportEvent.Started -> IncomingGifState(
                event.id,
                message(if (event.resumed) R.string.import_resuming else R.string.incoming_importing)
            )
            is IncomingImportEvent.Succeeded ->
                IncomingGifState(event.id, message(R.string.incoming_import_success), event.copiedUri)
            is IncomingImportEvent.Failed ->
                IncomingGifState(event.id, message(event.problem.messageRes(event.resumed)))
        }
    }

    private fun message(id: Int): String = ContextCompat.getContextForLanguage(appContext).getString(id)
}
