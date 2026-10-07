package com.batoh.feature.backpack

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.batoh.core.common.Result
import com.batoh.core.conversion.GifEditOptions
import com.batoh.core.conversion.GifEditorProcessor
import com.batoh.core.conversion.GifInfo
import com.batoh.core.conversion.GifScaleMode
import com.batoh.core.conversion.SafeGifDecoder
import com.batoh.core.domain.usecase.SaveGifBytesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class GifEditorStage { Loading, Rendering, Ready, Error, Cancelled }

data class GifEditorUiState(
    val stage: GifEditorStage = GifEditorStage.Loading,
    val options: GifEditOptions = GifEditOptions(scaleMode = GifScaleMode.Fit),
    val previewBytes: ByteArray? = null,
    val info: GifInfo? = null,
    val saving: Boolean = false,
    val savedUri: String? = null,
    val error: String? = null,
    /** Experimental programme bytes for the upload payload; defaults keep today's behaviour. */
    val playback: EditorPlaybackOptions = EditorPlaybackOptions()
)

@HiltViewModel
class GifEditorViewModel @Inject constructor(
    application: Application,
    private val saveGifBytes: SaveGifBytesUseCase,
    private val transfers: BackpackTransferManager
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(GifEditorUiState())
    val state = _state.asStateFlow()
    private val _sendEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val sendEvents = _sendEvents.asSharedFlow()
    private var inputUri: String? = null
    private var source: ByteArray? = null
    private var conversionJob: Job? = null
    private var saveJob: Job? = null

    fun load(uri: String) {
        if (inputUri == uri) return
        inputUri = uri
        source = null
        startConversion()
    }

    fun edit(options: GifEditOptions) {
        if (_state.value.saving || options == _state.value.options) return
        _state.value = _state.value.copy(options = options)
        startConversion()
    }

    fun retry() = startConversion()

    /** Payload-only setting: does not re-render the GIF or invalidate a saved copy. */
    fun setPlayback(playback: EditorPlaybackOptions) {
        if (_state.value.saving || playback == _state.value.playback) return
        _state.value = _state.value.copy(playback = playback)
    }

    fun cancel() {
        conversionJob?.cancel()
        _state.value = _state.value.copy(stage = GifEditorStage.Cancelled, previewBytes = null, error = null)
    }

    private fun startConversion() {
        if (_state.value.saving) return
        conversionJob?.cancel()
        val uri = inputUri ?: return
        val options = _state.value.options
        _state.value = _state.value.copy(
            stage = if (source == null) GifEditorStage.Loading else GifEditorStage.Rendering,
            previewBytes = null, savedUri = null, error = null
        )
        conversionJob = viewModelScope.launch {
            try {
                var loadedSource: ByteArray? = null
                val edited = withContext(Dispatchers.IO) {
                    val context = currentCoroutineContext()
                    val bytes = source ?: getApplication<Application>().contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            context.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size().toLong() + count <= SafeGifDecoder.MAX_BYTES) { "GIF is larger than 20 MB" }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    } ?: error("GIF cannot be loaded. Try importing it again.")
                    context.ensureActive()
                    loadedSource = bytes
                    GifEditorProcessor.transform(bytes, options) { context.ensureActive() }
                }
                ensureActive()
                // Publish from Main only; a cancelled older render cannot replace a new source.
                source = loadedSource
                _state.value = _state.value.copy(stage = GifEditorStage.Ready, previewBytes = edited.gifBytes, info = edited.info)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("GifEditor", "Loading the GIF failed", e)
                _state.value = _state.value.copy(stage = GifEditorStage.Error, error = localizedString(R.string.editor_load_failed))
            }
        }
    }

    /**
     * Default playback: navigate with the saved URI exactly as before (Backpack screen builds the
     * payload with manufacturer defaults). Changed playback: build the payload here with
     * speed/light and start the upload, then open the Backpack screen to show its progress; that
     * screen's own URI upload is ignored while this upload owns the transfer.
     */
    private fun dispatchSend(uri: String, gifBytes: ByteArray?) {
        val playback = _state.value.playback
        if (playback.isDefault) {
            _sendEvents.tryEmit(uri)
            return
        }
        val app = getApplication<Application>()
        if (!hasBluetoothPermissions(app)) {
            _state.value = _state.value.copy(error = localizedString(R.string.editor_playback_needs_bluetooth))
            return
        }
        val payload = try {
            playback.buildPayload(requireNotNull(gifBytes))
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = localizedString(R.string.editor_playback_payload_failed))
            return
        }
        val before = transfers.uploadState.value
        transfers.uploadPayload(payload)
        if (transfers.uploadState.value === before) {
            _state.value = _state.value.copy(error = localizedString(R.string.editor_playback_busy))
            return
        }
        _sendEvents.tryEmit(uri)
    }

    private fun hasBluetoothPermissions(app: Application): Boolean {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            listOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return permissions.all {
            androidx.core.content.ContextCompat.checkSelfPermission(app, it) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun localizedString(id: Int): String =
        androidx.core.content.ContextCompat.getContextForLanguage(getApplication<Application>()).getString(id)

    fun saveCopy(sendToBackpack: Boolean = false) {
        val current = _state.value
        if (current.stage != GifEditorStage.Ready || current.saving || saveJob?.isCompleted == false) return
        current.savedUri?.let { uri ->
            if (sendToBackpack) dispatchSend(uri, current.previewBytes)
            return
        }
        val bytes = current.previewBytes ?: return
        _state.value = current.copy(saving = true, error = null)
        saveJob = viewModelScope.launch {
            try {
                when (val saved = saveGifBytes(bytes, "batoh_upraveny")) {
                    is Result.Success -> {
                        val uri = saved.data.toString()
                        _state.value = _state.value.copy(saving = false, savedUri = uri)
                        if (sendToBackpack) dispatchSend(uri, bytes)
                    }
                    is Result.Error -> throw saved.exception
                    is Result.Loading -> error("Saving did not finish")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("GifEditor", "Saving the edited copy failed", e)
                _state.value = _state.value.copy(error = localizedString(R.string.editor_save_failed))
            } finally {
                _state.value = _state.value.copy(saving = false)
            }
        }
    }
}
