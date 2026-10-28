package com.batoh.feature.backpack

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batoh.core.common.Result
import com.batoh.core.conversion.TextBannerDirection
import com.batoh.core.conversion.TextBannerLayout
import com.batoh.core.conversion.TextBannerOptions
import com.batoh.core.conversion.TextBannerPlan
import com.batoh.core.conversion.TextBannerRenderer
import com.batoh.core.conversion.TextBannerSize
import com.batoh.core.conversion.TextBannerSpeed
import com.batoh.core.conversion.TextLoopRenderer
import com.batoh.core.common.LastSendTarget
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
import kotlinx.coroutines.flow.StateFlow
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
    val uploadRequested: Boolean = false,
    val loopEnabled: Boolean = false,
    val loopTexts: List<String> = List(3) { "" },
    val sendingLoop: Boolean = false,
    /** Panel size the banner is rendered (and saved) at: the connected panel, else the last one, else 64x64. */
    val panelWidth: Int = 64,
    val panelHeight: Int = 64,
    /** Size picked while composing (a saved panel or typed in); null = automatic (connected panel, else last, else 64x64). */
    val manualSize: Pair<Int, Int>? = null,
    /** The "other size" fields are open, even before a valid size has been typed. */
    val customSizeOpen: Boolean = false
)

@HiltViewModel
class TextBannerViewModel @Inject constructor(
    private val saveGifBytes: SaveGifBytesUseCase,
    private val transfers: BackpackTransferManager,
    private val application: Application,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    // Typed inputs survive process death; generated GIF bytes are never persisted, only re-rendered.
    private val _state = MutableStateFlow(TextBannerUiState(
        options = TextBannerSavedOptions.read { savedStateHandle[it] },
        loopEnabled = savedStateHandle["text_loop_enabled"] ?: false,
        loopTexts = List(3) { savedStateHandle["text_loop_$it"] ?: "" },
        manualSize = TextBannerPanelSize.restore(savedStateHandle[MANUAL_W], savedStateHandle[MANUAL_H]),
        customSizeOpen = savedStateHandle[CUSTOM_OPEN] ?: false
    ))
    val state = _state.asStateFlow()
    val uploadState = transfers.uploadState
    val busy = transfers.busy
    val connectedFamily = transfers.connectedFamily
    val connectedPanel = transfers.connectedPanel
    val myPanels = transfers.myPanels
    private val _chooser = MutableStateFlow<SendChooser>(SendChooser.Hidden)
    val chooser = _chooser.asStateFlow()
    private var resolveJob: Job? = null
    private var sendJob: Job? = null
    private var pendingPreparedId: Long? = null
    private val preparedPreferences = application.getSharedPreferences("prepared_texts", android.content.Context.MODE_PRIVATE)
    private val _preparedTexts = MutableStateFlow(PreparedTexts.decode(preparedPreferences.getString(PREPARED_KEY, null)))
    val preparedTexts: StateFlow<List<PreparedText>> = _preparedTexts.asStateFlow()
    private var renderJob: Job? = null
    private var saveJob: Job? = null

    init {
        // Also with blank text: sets the preview's aspect ratio for the panel it will be sent to.
        startRender(debounce = false)
        // A panel of another size connecting while the screen is open re-renders the banner for it.
        viewModelScope.launch {
            connectedPanel.collect { spec ->
                if (spec != null && _state.value.manualSize == null && !_state.value.saving && (spec.width to spec.height) != (_state.value.panelWidth to _state.value.panelHeight)) {
                    startRender(debounce = false)
                }
            }
        }
    }

    fun update(options: TextBannerOptions) {
        val limited = options.copy(text = options.text.take(TextBannerLayout.MAX_TEXT_LENGTH))
        if (_state.value.saving || limited == _state.value.options) return
        _state.value = _state.value.copy(options = limited)
        TextBannerSavedOptions.write(limited) { key, value -> savedStateHandle[key] = value }
        startRender()
    }

    fun retry() = startRender(debounce = false)

    /** Picks the size to compose for ([size] null = automatic). Invalid sizes are ignored. */
    fun setManualSize(size: Pair<Int, Int>?, custom: Boolean = false) {
        if (_state.value.saving) return
        if (size != null && !TextBannerPanelSize.isValid(size.first, size.second)) return
        _state.value = _state.value.copy(manualSize = size, customSizeOpen = custom)
        savedStateHandle[MANUAL_W] = size?.first ?: 0
        savedStateHandle[MANUAL_H] = size?.second ?: 0
        savedStateHandle[CUSTOM_OPEN] = custom
        startRender(debounce = false)
    }

    /** Opens or closes the "other size" fields without changing the size in use. */
    fun setCustomSizeOpen(open: Boolean) {
        _state.value = _state.value.copy(customSizeOpen = open)
        savedStateHandle[CUSTOM_OPEN] = open
    }

    fun setLoopEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(loopEnabled = enabled)
        savedStateHandle["text_loop_enabled"] = enabled
    }

    fun updateLoopText(index: Int, text: String) {
        if (index !in 0..2) return
        val limited = text.replace('\n', ' ').take(TextBannerLayout.MAX_TEXT_LENGTH)
        _state.value = _state.value.copy(loopTexts = _state.value.loopTexts.toMutableList().also { it[index] = limited })
        savedStateHandle["text_loop_$index"] = limited
    }

    /** Manually picked size, else the connected panel, else the last one, else 64x64. */
    private fun composeSize(): Pair<Int, Int> = _state.value.manualSize ?: transfers.targetPanelSize()

    private fun startRender(debounce: Boolean = true) {
        renderJob?.cancel()
        val options = _state.value.options
        val (width, height) = composeSize()
        _state.value = _state.value.copy(panelWidth = width, panelHeight = height)
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
                    TextBannerRenderer.render(options, width, height) { context.ensureActive() }
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
                when (val saved = saveGifBytes(bytes, "gif_text")) {
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

    /** Uses the same panel candidates and send sheet as a GIF send from the Panel screen. */
    fun requestSend(preparedId: Long? = null) {
        if (transfers.busy.value || sendJob?.isActive == true || _state.value.sendingLoop) return
        if (preparedId == null && (_state.value.stage != TextBannerStage.Ready ||
                (_state.value.loopEnabled && _state.value.loopTexts.any { it.isBlank() }))) return
        if (preparedId != null && _preparedTexts.value.none { it.id == preparedId }) return
        pendingPreparedId = preparedId
        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            _chooser.value = SendChooser.Scanning(Uri.EMPTY)
            try {
                val address = transfers.connectedAddress()?.takeIf { transfers.hasLink() }
                val connected = address?.let {
                    ConnectedPanel(it, transfers.nearbyPanels.value.firstOrNull { panel -> panel.address == it }?.name
                        ?: transfers.deviceName.value, transfers.connectedPanel.value)
                }
                val mine = transfers.myPanels.value
                val advertising = if (SendTargetPlan.needsScan(address, mine)) transfers.scanMyPanels() else emptyList()
                when (val decision = SendTargetPlan.decide(SendTargetPlan.candidates(connected, mine, advertising))) {
                    is SendDecision.Single -> {
                        _chooser.value = SendChooser.Hidden
                        sendToTarget(decision.target)
                    }
                    is SendDecision.Choose -> _chooser.value = SendChooser.Choosing(Uri.EMPTY, decision.targets,
                        decision.offerAll, LastSendTarget.read(application)?.address)
                }
            } finally {
                if (_chooser.value is SendChooser.Scanning) _chooser.value = SendChooser.Hidden
            }
        }
    }

    fun chooseTarget(target: SendTarget) {
        if (_chooser.value !is SendChooser.Choosing) return
        _chooser.value = SendChooser.Hidden
        sendToTarget(target)
    }

    fun chooseAllMyPanels() {
        if (_chooser.value !is SendChooser.Choosing) return
        _chooser.value = SendChooser.Hidden
        val id = pendingPreparedId
        if (id == null) sendToMyPanels() else sendPreparedToMyPanels(id)
    }

    fun retryChooser() {
        if (_chooser.value is SendChooser.Choosing) requestSend(pendingPreparedId)
    }

    fun dismissChooser() {
        resolveJob?.cancel()
        _chooser.value = SendChooser.Hidden
    }

    private fun sendToTarget(target: SendTarget) {
        LastSendTarget.remember(application, target.address, target.name)
        val preparedId = pendingPreparedId
        val current = _state.value
        val options = if (preparedId == null) current.options
            else _preparedTexts.value.firstOrNull { it.id == preparedId }?.options ?: return
        // A panel of unknown type has no size of its own: use the one chosen while composing.
        val (width, height) = target.spec?.let { it.width to it.height } ?: composeSize()
        val renderingLoop = preparedId == null && current.loopEnabled
        if (renderingLoop) _state.value = _state.value.copy(sendingLoop = true, errorRes = null)
        sendJob = viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.Default) {
                    val context = currentCoroutineContext()
                    if (renderingLoop) {
                        TextLoopRenderer.render(listOf(options.text) + current.loopTexts, width, height,
                            options.textColor, options.backgroundColor, options.bold) { context.ensureActive() }
                    } else {
                        TextBannerRenderer.render(options, width, height) { context.ensureActive() }.gifBytes
                    }
                }
                transfers.uploadGifBytesToPanel(bytes, name = "„${options.text.trim().take(24)}“", address = target.address)
                _state.value = _state.value.copy(uploadRequested = true, errorRes = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("TextBanner", "Sending text failed", e)
                _state.value = _state.value.copy(errorRes = R.string.text_banner_error_payload)
            } finally {
                if (renderingLoop) _state.value = _state.value.copy(sendingLoop = false)
            }
        }
    }

    /** Sends the banner to all of my panels in range; the manager renders it at each panel's own size. */
    fun sendToMyPanels() {
        val current = _state.value
        if (current.stage != TextBannerStage.Ready || current.options.text.isBlank()) return
        val name = "„${current.options.text.trim().take(24)}“"
        if (current.loopEnabled) {
            if (current.loopTexts.any { it.isBlank() }) return
            transfers.uploadTextLoopToMyPanels(listOf(current.options.text) + current.loopTexts,
                current.options.textColor, current.options.backgroundColor, current.options.bold, name)
        } else transfers.uploadBannerToMyPanels(current.options, name)
        _state.value = current.copy(uploadRequested = true, errorRes = null)
    }

    /** Like [sendToMyPanels] for a prepared text. */
    fun sendPreparedToMyPanels(id: Long) {
        val item = _preparedTexts.value.firstOrNull { it.id == id } ?: return
        transfers.uploadBannerToMyPanels(item.options, name = "„${item.options.text.trim().take(24)}“")
        _state.value = _state.value.copy(uploadRequested = true, errorRes = null)
    }

    fun cancelUpload() = transfers.cancelUpload()

    /** Adds the current options to the prepared list (blank text and duplicates are ignored). */
    fun savePrepared() {
        val options = _state.value.options
        // Ids come from the clock; bump past any existing one so two quick saves never collide.
        val id = maxOf(System.currentTimeMillis(), (_preparedTexts.value.maxOfOrNull { it.id } ?: 0L) + 1)
        persistPrepared(PreparedTexts.add(_preparedTexts.value, PreparedText(id, options)))
    }

    fun deletePrepared(id: Long) = persistPrepared(PreparedTexts.remove(_preparedTexts.value, id))

    /** Undo of [deletePrepared]: puts [item] back at [index] (clamped to the list). */
    fun restorePrepared(item: PreparedText, index: Int) {
        val list = _preparedTexts.value
        if (list.any { it.id == item.id }) return
        persistPrepared(list.toMutableList().apply { add(index.coerceIn(0, size), item) }.take(PreparedTexts.MAX))
    }

    /** Renders the saved banner at the connected panel's size and uploads it. */
    fun sendPrepared(id: Long) {
        val item = _preparedTexts.value.firstOrNull { it.id == id } ?: return
        val (width, height) = transfers.targetPanelSize()
        _state.value = _state.value.copy(errorRes = null)
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    val context = currentCoroutineContext()
                    TextBannerRenderer.render(item.options, width, height) { context.ensureActive() }
                }
                transfers.uploadGifBytes(result.gifBytes, name = "„${item.options.text.trim().take(24)}“")
                _state.value = _state.value.copy(uploadRequested = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("TextBanner", "Sending a prepared text failed", e)
                _state.value = _state.value.copy(errorRes = R.string.text_banner_error_payload)
            }
        }
    }

    private fun persistPrepared(list: List<PreparedText>) {
        _preparedTexts.value = list
        preparedPreferences.edit().putString(PREPARED_KEY, PreparedTexts.encode(list)).apply()
    }

    private companion object {
        const val RENDER_DEBOUNCE_MS = 250L
        const val PREPARED_KEY = "items"
        const val MANUAL_W = "text_banner_manual_w"
        const val MANUAL_H = "text_banner_manual_h"
        const val CUSTOM_OPEN = "text_banner_custom_open"
    }
}

/** Maps [TextBannerOptions] to primitives for SavedStateHandle; pure so it is unit-testable. */
internal object TextBannerSavedOptions {
    private const val TEXT = "text_banner_text"
    private const val TEXT_COLOR = "text_banner_text_color"
    private const val BACKGROUND_COLOR = "text_banner_background_color"
    private const val SPEED = "text_banner_speed"
    private const val SIZE = "text_banner_size"
    private const val BOLD = "text_banner_bold"
    private const val DIRECTION = "text_banner_direction"

    fun write(options: TextBannerOptions, put: (String, Any) -> Unit) {
        put(TEXT, options.text)
        put(TEXT_COLOR, options.textColor)
        put(BACKGROUND_COLOR, options.backgroundColor)
        put(SPEED, options.speed.name)
        put(SIZE, options.size.name)
        put(BOLD, options.bold)
        put(DIRECTION, options.direction.name)
    }

    /** Missing or stale values fall back to the defaults. */
    fun read(get: (String) -> Any?): TextBannerOptions {
        val defaults = TextBannerOptions(text = "")
        return TextBannerOptions(
            text = (get(TEXT) as? String).orEmpty().take(TextBannerLayout.MAX_TEXT_LENGTH),
            textColor = get(TEXT_COLOR) as? Int ?: defaults.textColor,
            backgroundColor = get(BACKGROUND_COLOR) as? Int ?: defaults.backgroundColor,
            speed = TextBannerSpeed.entries.firstOrNull { it.name == get(SPEED) } ?: defaults.speed,
            size = TextBannerSize.entries.firstOrNull { it.name == get(SIZE) } ?: defaults.size,
            bold = get(BOLD) as? Boolean ?: defaults.bold,
            direction = TextBannerDirection.entries.firstOrNull { it.name == get(DIRECTION) } ?: defaults.direction
        )
    }
}
