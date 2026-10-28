package com.batoh.feature.backpack

import android.app.Application
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.batoh.core.common.LastPanelSize
import com.batoh.core.conversion.VideoToGifConverter
import com.batoh.core.conversion.SafeGifDecoder
import com.batoh.core.conversion.EffectTestGif
import com.batoh.core.conversion.TestPatternGif
import com.batoh.core.conversion.TextBannerOptions
import com.batoh.core.conversion.TextBannerRenderer
import com.batoh.core.data.bluetooth.BackpackCommands
import com.batoh.core.data.bluetooth.BackpackInfoFormatter
import com.batoh.core.data.bluetooth.BackpackPasswordStatus
import com.batoh.core.data.bluetooth.BackpackPayload
import com.batoh.core.data.bluetooth.BackpackPlaylist
import com.batoh.core.data.bluetooth.BackpackFrame
import com.batoh.core.data.bluetooth.BluetoothLeManager
import com.batoh.core.data.bluetooth.MyPanel
import com.batoh.core.data.bluetooth.NearbyPanel
import com.batoh.core.data.bluetooth.PanelFamily
import com.batoh.core.data.bluetooth.PanelSpec
import com.batoh.core.data.bluetooth.ipixel.IPixelProtocol
import com.batoh.core.data.bluetooth.UploadFailure
import com.batoh.core.data.bluetooth.UploadHistory
import com.batoh.core.data.bluetooth.UploadHistoryEntry
import com.batoh.core.data.bluetooth.UploadOutcome
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

enum class UploadStage { Idle, Preparing, Connecting, Sending, Finishing, Success, Error, Cancelled }

data class UploadState(
    val stage: UploadStage = UploadStage.Idle,
    val name: String? = null,
    val uri: String? = null,
    val progress: Float = 0f,
    val message: String? = null,
    /** Typed reason when [stage] is [UploadStage.Error] or [UploadStage.Cancelled]; [message] is its localised title. */
    val failure: UploadFailure? = null,
    /** True for a multi-programme sequence upload (changes the success wording). */
    val isSequence: Boolean = false,
    /** Set for a "send to all my panels" run: current panel and the results so far. */
    val multi: MultiPanelProgress? = null
)

/** Stage update of the running upload; the multi-panel block survives only inside a "send to all" run, never into another upload. */
internal fun UploadState.withStage(
    stage: UploadStage, progress: Float, message: String?, failure: UploadFailure?, multiRunActive: Boolean
): UploadState = copy(stage = stage, progress = progress, message = message, failure = failure,
    multi = if (multiRunActive) multi else null)

/** Last iPixel settings set in this app session; the panel cannot report them. Clock fields are the last shown face. */
data class IPixelSettings(
    val brightness: Int = 100,
    val quarterTurns: Int = 0,
    val screenOn: Boolean = true,
    val clockStyle: Int = 0,
    val use24h: Boolean = true,
    val showDate: Boolean = true
)

/** A single owner of panel transactions; navigation never cancels a transfer. */
@Singleton
class BackpackTransferManager @Inject constructor(
    private val application: Application,
    private val bluetoothManager: BluetoothLeManager,
    private val converter: VideoToGifConverter
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val operationMutex = Mutex()
    private val uploadOperation = SingleOperationOwner()
    private val settingsOperation = SingleOperationOwner()
    private var lastRequest: UploadRequest? = null
    private var lastMultiContent: MultiPanelContent? = null
    /** True while a "send to all my panels" run owns [uploadOperation]. */
    @Volatile private var multiRunActive = false
    /** The current panel's transfer in such a run; a lost link cancels only this, not the whole run. */
    @Volatile private var panelJob: Job? = null
    private var userCancelledUpload = false
    /** Set when the link dropped while chunks were being sent, so the cancellation reads as a lost connection. */
    @Volatile private var connectionLostDuringUpload = false
    /** [gif] (with its editor [playback]) is a ready GIF from [uploadGifBytes]; [payload] is its backpack form when built. */
    private data class UploadRequest(val name: String, val uri: Uri?, val payload: ByteArray? = null, val test: Boolean = false, val effect: Int? = null, val playlist: Boolean = false, val sequence: List<ByteArray>? = null,
        val gif: ByteArray? = null, val playback: EditorPlaybackOptions? = null,
        /** Panel (picked in GifPack, or already connected) this upload must go to; null = whatever the link/auto-connect gives. */
        val panelAddress: String? = null)
    /** A user-built programme sequence waiting for the backpack screen to grant permissions and start it. */
    private var stagedSequence: StagedSequence? = null
    private class StagedSequence(val payloads: List<ByteArray>, val stagedAtMs: Long)
    private val initialized = MutableStateFlow(false)
    private val settingsBusy = MutableStateFlow(false)
    private val transferBusy = MutableStateFlow(false)
    val busy = combine(settingsBusy, transferBusy) { settings, transfer -> settings || transfer }
        .stateIn(scope, SharingStarted.Eagerly, false)
    val connectionStatus = bluetoothManager.connectionStatus
    val bluetoothEnabled = bluetoothManager.bluetoothEnabled
    val scannedDevices = bluetoothManager.scannedDevices
    val nearbyPanels = bluetoothManager.nearbyPanels
    val connectedPanel = bluetoothManager.connectedPanel
    /** Family of the current link, known before [connectedPanel] for an iPixel panel of unknown type. */
    val connectedFamily: Flow<PanelFamily?> = combine(bluetoothManager.connectionStatus, bluetoothManager.connectedPanel) { _, spec ->
        spec?.family ?: bluetoothManager.currentFamily
    }.distinctUntilChanged()
    val bleLog = bluetoothManager.bleLog
    /** Ignored while an upload or the connect setup (backpack auth, iPixel info query) is running. */
    fun pickPanel(address: String) {
        if (uploadOperation.isOccupied || settingsBusy.value) return
        bluetoothManager.pickPanel(address)
    }
    /** Panels the user picked in GifPack; the only possible targets of "send to all my panels". */
    val myPanels = bluetoothManager.myPanels
    fun unpickPanel(address: String) = bluetoothManager.unpickPanel(address)
    fun connectedAddress(): String? = bluetoothManager.connectedAddress
    private val _uploadState = MutableStateFlow(UploadState())
    val uploadState = _uploadState.asStateFlow()
    val uploadProgress = uploadState.map { if (it.stage in activeStages) it.progress else -1f }
        .stateIn(scope, SharingStarted.Eagerly, -1f)
    val uploadError = uploadState.map { if (it.stage == UploadStage.Error) it.message else null }
        .stateIn(scope, SharingStarted.Eagerly, null)
    private val _commandError = MutableStateFlow<String?>(null)
    val commandError = _commandError.asStateFlow()
    /**
     * How many data chunks of a single-programme upload may be in flight before an ACK (1 = classic stop-and-wait).
     * 2 is the default: about 1.8x faster and clean on hardware; larger windows stay a diagnostics experiment
     * (4 once aborted on a repeated ACK). Sequences and playlists always use stop-and-wait.
     */
    val uploadWindow = MutableStateFlow(2)

    private val _ipixelSettings = MutableStateFlow(IPixelSettings())
    val ipixelSettings = _ipixelSettings.asStateFlow()

    private val _panelState = MutableStateFlow<BackpackCommands.State?>(null)
    val panelState = _panelState.asStateFlow()
    /** Advertised capabilities; rotation controls exist only when the firmware reports funCode 0x0100. */
    val advertisement = bluetoothManager.advertisement
    val deviceName = bluetoothManager.deviceName
    /** Built-in programme count from Cmd 0D after connecting; null = unknown/not answered. */
    private val _builtInCount = MutableStateFlow<Int?>(null)
    val builtInCount = _builtInCount.asStateFlow()
    private val historyPreferences = application.getSharedPreferences("backpack_upload_history", android.content.Context.MODE_PRIVATE)
    private val _uploadHistory = MutableStateFlow(UploadHistory.decode(historyPreferences.getString(HISTORY_KEY, null)))
    /** Last uploads, newest first; persisted so the user can tell confirmed uploads from failures later. */
    val uploadHistory = _uploadHistory.asStateFlow()

    init {
        scope.launch {
            connectionStatus.map { it.startsWith("Ready") }.distinctUntilChanged().collectLatest { ready ->
                initialized.value = false
                _panelState.value = null
                _builtInCount.value = null
                if (!ready) {
                    settingsOperation.cancel()
                    if (_uploadState.value.stage in setOf(UploadStage.Sending, UploadStage.Finishing)) {
                        if (multiRunActive) {
                            panelJob?.cancel(CancellationException("Panel connection lost"))
                        } else {
                            connectionLostDuringUpload = true
                            uploadOperation.cancel(CancellationException("Backpack connection lost"))
                        }
                    }
                } else if (isIPixel()) operationMutex.withLock {
                    // iPixel: only the info command (no auth, built-ins or state query); it also sets the clock.
                    settingsBusy.value = true
                    try {
                        _commandError.value = null
                        queryIPixelType()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logCommandError(e)
                    } finally {
                        initialized.value = connectionStatus.value.startsWith("Ready")
                        settingsBusy.value = false
                    }
                } else operationMutex.withLock {
                    settingsBusy.value = true
                    try {
                        _commandError.value = null
                        ensureAuth()?.let { error(it) }
                        // Manufacturer (BleManager.onOtaAvailable) sets the clock only when funCode has
                        // bit 0x0001; this backpack advertises 0x0044, so it is skipped there.
                        val adv = advertisement.value
                        val clock = if (adv == null || adv.syncsTimeOnConnect) {
                            bluetoothManager.sendCommand(BackpackCommands.setTime())
                        } else {
                            val note = "Time sync skipped: funCode=0x%04X has no time support (0x0001)".format(adv.funCode)
                            Log.i("BackpackBLE", note)
                            bluetoothManager.addBleLog(note)
                            null
                        }
                        queryBuiltInCount()
                        refreshState()
                        if (adv == null || adv.syncsTimeOnConnect) requireSuccess(clock, "Setting time")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logCommandError(e)
                        _commandError.value = localizedString(R.string.backpack_setup_failed)
                    } finally {
                        initialized.value = connectionStatus.value.startsWith("Ready")
                        settingsBusy.value = false
                    }
                }
            }
        }
    }

    fun startScan() = bluetoothManager.startScan()
    fun stopScan() = bluetoothManager.stopScan()
    fun connectToTarget() {
        if (uploadOperation.isOccupied || settingsBusy.value) return
        bluetoothManager.connectToTarget()
    }
    fun disconnect() {
        cancelUpload()
        settingsOperation.cancel()
        bluetoothManager.disconnect()
    }

    /**
     * Uploads the GIF at [uri] to exactly [address]: the connected panel as it is, or, for another panel, only if the user
     * picked it in GifPack (the link is then switched to it). Never auto-connects to any other panel.
     */
    fun uploadGifToPanel(uri: Uri, address: String) = startUpload(UploadRequest("GIF", uri, panelAddress = address))

    /** Quick scan for the picked panels that advertise now (also lists them in [nearbyPanels]); never connects. */
    suspend fun scanMyPanels(): List<NearbyPanel> {
        val mine = bluetoothManager.myPanels.value.map { it.address }.toSet()
        if (mine.isEmpty()) return emptyList()
        bluetoothManager.stopScan()
        return bluetoothManager.scanForPanels(mine)
    }

    /** True while a link to a panel is open or being opened. */
    fun hasLink(): Boolean = bluetoothManager.connectedAddress != null && connectionStatus.value.let {
        it.startsWith("Ready") || it.startsWith("Connecting") || it.startsWith("Connected")
    }
    /**
     * Uploads a ready GIF (editor output, text banner). A backpack gets exactly
     * `playback?.buildPayload(gif) ?: BackpackPayload.fromGif(gif)` (a 64x64 GIF's payload is built here and
     * may throw; other sizes are scaled to 64x64 once Ready); an iPixel panel gets the GIF at its panel
     * size and [playback] is ignored.
     */
    fun uploadGifBytes(gif: ByteArray, name: String?, playback: EditorPlaybackOptions? = null) {
        val copy = gif.copyOf()
        val payload = PanelUploadPrep.eagerProgramme(copy, isIPixel(), playback)
        startUpload(UploadRequest(name ?: localizedString(R.string.backpack_program_name), null, payload, gif = copy, playback = playback))
    }
    /** Backpack: the 64x64 test pattern programme; iPixel: the test pattern at the panel size. */
    fun sendTestImage() = startUpload(UploadRequest(localizedString(R.string.backpack_test_image_name), null, test = true))
    /** Experimental: one upload of the asymmetric [EffectTestGif] with programme item effect code [effect]. */
    fun sendEffectTest(effect: Int) {
        if (isIPixel()) return
        startUpload(UploadRequest(localizedString(R.string.backpack_effect_test_name, effect), null, test = true, effect = effect))
    }

    /** Experimental: announces two [EffectTestGif] programmes (digits 1 and 2) as a playlist via Cmd 0x03 / 0x08. */
    fun sendPlaylistTest() {
        if (isIPixel()) return
        startUpload(UploadRequest(localizedString(R.string.backpack_playlist_test_name), null, playlist = true))
    }

    /**
     * Stages a user-built sequence (one prepared payload per programme, in playing order). The upload starts
     * through [startStagedSequence] once the backpack screen has its Bluetooth permissions.
     */
    fun stageSequence(payloads: List<ByteArray>) {
        require(payloads.size in 2..GifSequencePlan.MAX_PROGRAMMES) { "Invalid sequence length: ${payloads.size}" }
        stagedSequence = StagedSequence(payloads.map { it.copyOf() }, System.currentTimeMillis())
    }

    /** Starts the staged sequence, if there is a fresh one; returns whether an upload was started. */
    fun startStagedSequence(): Boolean {
        if (isIPixel()) return false
        val staged = stagedSequence ?: return false
        stagedSequence = null
        if (System.currentTimeMillis() - staged.stagedAtMs > STAGED_SEQUENCE_TTL_MS) return false
        startUpload(UploadRequest(quantityString(R.plurals.backpack_sequence_name, staged.payloads.size), null,
            sequence = staged.payloads))
        return true
    }

    /** Plays built-in firmware programme [id] (1-based, ≤ [builtInCount]) via a type-5 item; nothing is stored. */
    fun playBuiltIn(id: Int) {
        if (isIPixel()) return
        val count = _builtInCount.value ?: return
        if (id !in 1..count) return
        val adv = advertisement.value
        val payload = BackpackPayload.builtIn(id, adv?.width?.takeIf { it > 0 } ?: 64, adv?.height?.takeIf { it > 0 } ?: 64)
        startUpload(UploadRequest(localizedString(R.string.backpack_builtin_name, id), null, payload))
    }

    fun clearUploadHistory() {
        _uploadHistory.value = emptyList()
        historyPreferences.edit().remove(HISTORY_KEY).apply()
    }

    private fun recordUpload(
        outcome: UploadOutcome,
        failure: UploadFailure? = null,
        name: String = _uploadState.value.name ?: localizedString(R.string.backpack_program_name)
    ) {
        val history = UploadHistory.add(_uploadHistory.value, UploadHistoryEntry(name, System.currentTimeMillis(), outcome, failure))
        _uploadHistory.value = history
        historyPreferences.edit().putString(HISTORY_KEY, UploadHistory.encode(history)).apply()
    }
    fun autoConnectAndUpload() = sendTestImage()
    fun retryUpload() {
        lastMultiContent?.let { startMultiUpload(it); return }
        lastRequest?.let(::startUpload)
    }

    fun cancelUpload() {
        if (!uploadOperation.isOccupied) return
        userCancelledUpload = true
        val sending = _uploadState.value.stage in setOf(UploadStage.Sending, UploadStage.Finishing)
        uploadOperation.cancel(CancellationException("Upload cancelled by the user"))
        // No verified protocol abort exists; reset the connection after partial data.
        if (sending) bluetoothManager.disconnect()
    }

    private fun updateUpload(
        stage: UploadStage,
        progress: Float = _uploadState.value.progress,
        message: String? = null,
        failure: UploadFailure? = null
    ) {
        _uploadState.value = _uploadState.value.withStage(stage, progress, message, failure, multiRunActive)
    }

    /** Ends the upload with [failure]: UI state, BLE log line and history entry. */
    private fun failUpload(failure: UploadFailure, cause: Throwable?) {
        val note = "Upload failed: ${failure.code}" + (cause?.let { " (${it.javaClass.simpleName}: ${it.message})" } ?: "")
        Log.w("BackpackBLE", note, cause)
        bluetoothManager.addBleLog(note)
        val text = failure.text()
        val cancelled = failure == UploadFailure.Cancelled
        updateUpload(if (cancelled) UploadStage.Cancelled else UploadStage.Error,
            message = localizedString(text.title, *text.args.toTypedArray()), failure = failure)
        recordUpload(if (cancelled) UploadOutcome.Cancelled else UploadOutcome.Failed, failure)
    }

    /**
     * Validates [gif] and scales it to 64x64 exactly like a single upload, then wraps it into a programme
     * payload. Throws [UploadFailureException] with a typed failure.
     */
    internal suspend fun prepareProgramme(gif: ByteArray): PreparedProgramme =
        PanelUploadPrep.programme(gif) { converter.convertGifBytes(it) }

    /** What [spec]'s family receives for [gif]: the backpack programme payload or the GIF at the iPixel panel size. */
    internal suspend fun prepareForPanel(gif: ByteArray, spec: PanelSpec): ByteArray =
        PanelUploadPrep.prepare(gif, spec) { converter.convertGifBytes(it) }

    /** Reads the GIF at [uri] (size-limited) and shows its file name. */
    private suspend fun readGif(uri: Uri): ByteArray {
        val name = runCatching {
            application.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "GIF"
        _uploadState.value = _uploadState.value.copy(name = name)
        val gif = (try {
            application.contentResolver.openInputStream(uri)?.use {
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val size = it.read(buffer)
                    if (size < 0) break
                    if (output.size().toLong() + size > SafeGifDecoder.MAX_BYTES) throw UploadFailureException(UploadFailure.GifTooLarge)
                    output.write(buffer, 0, size)
                }
                output.toByteArray()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: UploadFailureException) {
            throw e
        } catch (e: Exception) {
            throw UploadFailureException(UploadFailure.GifUnreadable, e)
        }) ?: throw UploadFailureException(UploadFailure.GifUnreadable)
        return gif
    }

    private inline fun buildPayload(block: () -> ByteArray): ByteArray = PanelUploadPrep.payload(block)

    /** Backpack programme payload of a test image, ready GIF or picked GIF ([source] read from the request's URI). */
    private suspend fun backpackPayload(request: UploadRequest, source: ByteArray?): ByteArray = when {
        request.test -> buildPayload { BackpackPayload.fromGif(TestPatternGif.render()) }
        request.gif != null -> PanelUploadPrep.readyGifProgramme(request.gif, request.playback) { converter.convertGifBytes(it) }
        else -> prepareProgramme(requireNotNull(source)).payload
    }

    /** The GIF an iPixel panel of [spec] receives; backpack-only requests (programme payloads, effects, playlists) fail. */
    private suspend fun ipixelFile(request: UploadRequest, source: ByteArray?, spec: PanelSpec): ByteArray = when {
        request.test && request.effect == null -> TestPatternGif.render(spec.width, spec.height)
        source != null -> prepareForPanel(source, spec)
        else -> throw UploadFailureException(UploadFailure.Unknown, IllegalStateException("Backpack-only upload on an iPixel panel"))
    }

    /** Size to render editor output and text banners at: the connected panel, else the last one, else 64x64. */
    fun targetPanelSize(): Pair<Int, Int> =
        bluetoothManager.connectedPanel.value?.let { it.width to it.height } ?: LastPanelSize.read(application)

    private fun isIPixel(): Boolean = bluetoothManager.currentFamily == PanelFamily.IPixel

    private fun startUpload(request: UploadRequest) {
        if (uploadOperation.isOccupied || settingsOperation.isOccupied) return
        lastRequest = request
        lastMultiContent = null
        userCancelledUpload = false
        connectionLostDuringUpload = false
        transferBusy.value = true
        _uploadState.value = UploadState(UploadStage.Preparing, request.name, request.uri?.toString(), isSequence = request.sequence != null)
        uploadOperation.launch(scope) {
            try {
                // The GIF an iPixel panel would get; its size is known only once that panel is Ready,
                // so on an iPixel link the backpack payload is not built here.
                var source: ByteArray? = request.gif
                val ipixelLink = isIPixel()
                val payloads = withContext(Dispatchers.IO) {
                    request.sequence?.let { return@withContext it }
                    if (request.playlist) return@withContext listOf(1, 2).map { digit ->
                        buildPayload { BackpackPayload.fromGif(EffectTestGif.render(digit)) }
                    }
                    when {
                        request.payload != null -> listOf(request.payload)
                        request.effect != null -> listOf(buildPayload { BackpackPayload.fromGif(EffectTestGif.render(request.effect), effect = request.effect) })
                        else -> {
                            if (!request.test && request.gif == null) source = readGif(requireNotNull(request.uri))
                            // A ready GIF without an eager payload waits for Ready: its target may be an iPixel panel.
                            if (ipixelLink || request.gif != null || request.panelAddress != null) emptyList() else listOf(backpackPayload(request, source))
                        }
                    }
                }
                ensureActive()
                updateUpload(UploadStage.Connecting)
                val status = connectionStatus.value
                val wanted = request.panelAddress
                if (wanted != null) {
                    // Only this panel: keep its link, else close another link and connect to it (picked panels only).
                    if (!hasLink() || bluetoothManager.connectedAddress != wanted) {
                        if (bluetoothManager.connectedAddress != null) closePanelLink()
                        if (!bluetoothManager.connectToPicked(wanted)) throw UploadFailureException(UploadFailure.NotConnected)
                    }
                } else if (!status.startsWith("Ready") && !status.startsWith("Connecting") && !status.startsWith("Connected")) {
                    bluetoothManager.connectToTarget()
                }
                val ready = withTimeoutOrNull(20000) {
                    combine(connectionStatus, initialized) { state, init -> state.startsWith("Ready") && init }.first { it }
                }
                if (ready == null || (wanted != null && bluetoothManager.connectedAddress != wanted)) throw UploadFailureException(UploadFailure.NotConnected)
                val completion = uploadToReadyPanel(request, source, payloads)
                val message = when (completion) {
                    // A sequence shows its success text once, as the stage line; no extra message.
                    UploadCompletion.Uploaded -> if (request.sequence != null) null else localizedString(R.string.backpack_upload_success)
                    UploadCompletion.AlreadyPresent -> localizedString(R.string.backpack_upload_already_present)
                }
                updateUpload(UploadStage.Success, 1f, message)
                recordUpload(if (completion == UploadCompletion.Uploaded) UploadOutcome.Confirmed else UploadOutcome.AlreadyPresent)
            } catch (e: CancellationException) {
                failUpload(when {
                    userCancelledUpload -> UploadFailure.Cancelled
                    connectionLostDuringUpload -> UploadFailure.ConnectionLost
                    else -> UploadFailure.Unknown
                }, e)
                throw e
            } catch (e: Exception) {
                failUpload((e as? UploadFailureException)?.failure ?: UploadFailure.Unknown, e)
            } finally {
                transferBusy.value = false
            }
        }
    }

    /**
     * Under the operation lock on a Ready link: builds what the connected panel's family receives for [request]
     * ([source] is the read or ready GIF, [payloads] prebuilt backpack payloads) and sends it. A failed transfer
     * closes the uncertain link before the failure is rethrown.
     */
    private suspend fun uploadToReadyPanel(request: UploadRequest, source: ByteArray?, payloads: List<ByteArray>): UploadCompletion =
        operationMutex.withLock {
            if (!connectionStatus.value.startsWith("Ready")) throw UploadFailureException(UploadFailure.ConnectionLost)
            // Decided by the connected family only; the stored advertisement may still be a backpack's.
            val ipixel = isIPixel()
            val ipixelGif = if (ipixel) {
                val spec = bluetoothManager.connectedPanel.value
                    ?: throw UploadFailureException(UploadFailure.Unknown, IllegalStateException("iPixel panel of unknown type"))
                withContext(Dispatchers.Default) { ipixelFile(request, source, spec) }
            } else null
            val backpackPayloads = if (ipixel) emptyList() else payloads.ifEmpty {
                listOf(withContext(Dispatchers.IO) { backpackPayload(request, source) })
            }
            updateUpload(UploadStage.Sending)
            try {
                when {
                    ipixelGif != null -> doIPixelUpload(ipixelGif)
                    request.playlist || request.sequence != null -> doPlaylist(backpackPayloads)
                    else -> doUpload(backpackPayloads.single())
                }
            } catch (e: Exception) {
                // A missing answer after the link already dropped is reported as a lost connection.
                val linkGone = !connectionStatus.value.startsWith("Ready")
                // Keep transaction ownership until the uncertain BLE session is closed.
                bluetoothManager.disconnect()
                throw if (linkGone && e is UploadFailureException && e.failure.isMissingAnswer()) {
                    UploadFailureException(UploadFailure.ConnectionLost, e)
                } else e
            }
        }

    /** Sends the GIF at [uri] to every picked panel in range, each converted for its own family and size. */
    fun uploadGifToMyPanels(uri: Uri) =
        startMultiUpload(MultiPanelContent.Library(uri.lastPathSegment ?: "GIF", uri))

    /** Like [uploadGifBytes] for all my panels in range: a backpack gets [playback], an iPixel panel the GIF at its size. */
    fun uploadGifBytesToMyPanels(gif: ByteArray, name: String?, playback: EditorPlaybackOptions? = null) =
        startMultiUpload(MultiPanelContent.ReadyGif(name ?: localizedString(R.string.backpack_program_name), gif.copyOf(), playback))

    /** Renders the text banner [options] separately at each of my panels' sizes and sends it there. */
    fun uploadBannerToMyPanels(options: TextBannerOptions, name: String) =
        startMultiUpload(MultiPanelContent.Banner(name, options))

    /**
     * "Send to all my panels": a quick scan for the picked panels, then one at a time connect, wait for Ready and
     * initialisation, upload through [uploadToReadyPanel], record history, disconnect. A failing panel does not stop
     * the run; cancelling stops it after the current panel. The panel connected before comes last and stays
     * connected; otherwise the previously connected panel is reconnected at the end.
     */
    private fun startMultiUpload(content: MultiPanelContent) {
        if (uploadOperation.isOccupied || settingsOperation.isOccupied) return
        lastRequest = null
        lastMultiContent = content
        if (bluetoothManager.myPanels.value.isEmpty()) {
            // No picked panel at all: nothing is tried, so no history entry.
            _uploadState.value = UploadState(UploadStage.Error, content.name, message = localizedString(R.string.multi_send_no_panels))
            return
        }
        userCancelledUpload = false
        connectionLostDuringUpload = false
        transferBusy.value = true
        multiRunActive = true
        _uploadState.value = UploadState(UploadStage.Preparing, content.name, (content as? MultiPanelContent.Library)?.uri?.toString(),
            multi = MultiPanelProgress(0, 0, null, emptyList()))
        uploadOperation.launch(scope) {
            val previous = bluetoothManager.connectedAddress
            val lastBefore = bluetoothManager.lastPanelAddress()
            // Each panel's Ready also stores its size as the last panel's; restored with the address.
            val (widthBefore, heightBefore) = LastPanelSize.read(application)
            val results = mutableListOf<PanelSendResult>()
            var targets = emptyList<MyPanel>()
            // A library GIF's file name is known once it has been read.
            val historyName = { _uploadState.value.name ?: content.name }
            fun progress(stage: UploadStage, index: Int, panel: MyPanel?) {
                _uploadState.value = _uploadState.value.copy(stage = stage, progress = 0f, message = null, failure = null,
                    multi = MultiPanelProgress(index, targets.size, panel?.let(::panelLabel), results.toList()))
            }
            try {
                val source = (content as? MultiPanelContent.Library)?.let { withContext(Dispatchers.IO) { readGif(it.uri) } }
                val mine = bluetoothManager.myPanels.value
                bluetoothManager.stopScan()
                val advertising = bluetoothManager.scanForPanels(mine.map { it.address }.toSet())
                val plan = MultiPanelSend.plan(mine, advertising, previous)
                plan.notInRange.forEach { results += PanelSendResult(it, PanelSendOutcome.NotInRange) }
                targets = plan.targets
                for ((index, panel) in targets.withIndex()) {
                    progress(UploadStage.Connecting, index + 1, panel)
                    val outcome = sendToPanel(content, source, panel)
                    results += PanelSendResult(panel, outcome)
                    recordPanelResult(historyName(), panel, outcome)
                    val keepConnected = index == targets.lastIndex && panel.address == previous
                    progress(UploadStage.Connecting, index + 1, panel)
                    if (!keepConnected) closePanelLink()
                }
                val summary = MultiPanelSend.summary(results)
                _uploadState.value = _uploadState.value.copy(
                    stage = if (summary.allSucceeded) UploadStage.Success else UploadStage.Error, progress = 1f,
                    message = localizedString(R.string.multi_send_summary, summary.succeeded, summary.total), failure = null,
                    multi = MultiPanelProgress(targets.size, targets.size, null, results.toList()))
            } catch (e: CancellationException) {
                // Cancelled by the user: the current panel's transfer was cancelled, the rest are not contacted.
                val done = results.map { it.panel.address }.toSet()
                val pending = targets.filter { it.address !in done }
                pending.firstOrNull()?.let {
                    results += PanelSendResult(it, PanelSendOutcome.Failed(UploadFailure.Cancelled))
                    recordPanelResult(historyName(), it, PanelSendOutcome.Failed(UploadFailure.Cancelled))
                }
                pending.drop(1).forEach { results += PanelSendResult(it, PanelSendOutcome.Skipped) }
                val summary = MultiPanelSend.summary(results)
                _uploadState.value = _uploadState.value.copy(stage = UploadStage.Cancelled,
                    // Cancelled during the scan: no panel was planned, so there is nothing to sum up.
                    message = if (summary.total == 0) null else localizedString(R.string.multi_send_summary, summary.succeeded, summary.total),
                    failure = UploadFailure.Cancelled, multi = MultiPanelProgress(targets.size, targets.size, null, results.toList()))
                throw e
            } catch (e: Exception) {
                failUpload((e as? UploadFailureException)?.failure ?: UploadFailure.Unknown, e)
            } finally {
                multiRunActive = false
                panelJob = null
                // A cancel while a panel's link was still opening or initialising (stage Connecting) leaves it open,
                // or a GATT retry pending: close any link of the run that is not the panel connected before it.
                if (MultiPanelSend.closeLinkAfterRun(bluetoothManager.connectedAddress, previous, connectionStatus.value)) {
                    bluetoothManager.disconnect()
                }
                bluetoothManager.restoreLastPanel(lastBefore)
                LastPanelSize.write(application, widthBefore, heightBefore)
                if (previous != null && bluetoothManager.connectedAddress != previous) bluetoothManager.connectToTarget()
                transferBusy.value = false
            }
        }
    }

    /**
     * One panel of a run: connect (only to this picked address), wait for Ready and initialisation, render or convert
     * the content for this panel and upload it. Never throws except when the whole run is cancelled.
     */
    private suspend fun sendToPanel(content: MultiPanelContent, source: ByteArray?, panel: MyPanel): PanelSendOutcome = coroutineScope {
        val job = async {
            try {
                val status = connectionStatus.value
                val linked = bluetoothManager.connectedAddress == panel.address &&
                    (status.startsWith("Ready") || status.startsWith("Connecting") || status.startsWith("Connected"))
                if (!linked) {
                    if (bluetoothManager.connectedAddress != null) closePanelLink()
                    if (!bluetoothManager.connectToPicked(panel.address)) throw UploadFailureException(UploadFailure.NotConnected)
                }
                val ready = withTimeoutOrNull(20000) {
                    combine(connectionStatus, initialized) { state, init -> state.startsWith("Ready") && init }.first { it }
                }
                // Only the picked panel of this turn may receive anything.
                if (ready == null || bluetoothManager.connectedAddress != panel.address) throw UploadFailureException(UploadFailure.NotConnected)
                val (request, gif) = when (content) {
                    is MultiPanelContent.Library -> UploadRequest(content.name, content.uri) to requireNotNull(source)
                    is MultiPanelContent.ReadyGif -> UploadRequest(content.name, null, gif = content.gif, playback = content.playback) to content.gif
                    is MultiPanelContent.Banner -> {
                        val spec = bluetoothManager.connectedPanel.value
                            ?: throw UploadFailureException(UploadFailure.Unknown, IllegalStateException("iPixel panel of unknown type"))
                        val banner = withContext(Dispatchers.Default) {
                            val taskContext = currentCoroutineContext()
                            TextBannerRenderer.render(content.options, spec.width, spec.height) { taskContext.ensureActive() }.gifBytes
                        }
                        UploadRequest(content.name, null, gif = banner) to banner
                    }
                }
                when (uploadToReadyPanel(request, gif, emptyList())) {
                    UploadCompletion.Uploaded -> PanelSendOutcome.Sent
                    UploadCompletion.AlreadyPresent -> PanelSendOutcome.AlreadyPresent
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = (e as? UploadFailureException)?.failure ?: UploadFailure.Unknown
                val note = "Panel ${panel.name ?: "?"}: upload failed: ${failure.code}" + " (${e.javaClass.simpleName}: ${e.message})"
                Log.w("BackpackBLE", note, e)
                bluetoothManager.addBleLog(note)
                PanelSendOutcome.Failed(failure)
            }
        }
        panelJob = job
        try {
            job.await()
        } catch (e: CancellationException) {
            // Only this panel's job was cancelled (its link dropped); a cancelled run rethrows here.
            ensureActive()
            bluetoothManager.addBleLog("Panel ${panel.name ?: "?"}: connection lost")
            PanelSendOutcome.Failed(UploadFailure.ConnectionLost)
        } finally {
            panelJob = null
        }
    }

    /** Disconnects and waits (≤ 1 s) until the Ready handling has reset, so the next panel is initialised afresh. */
    private suspend fun closePanelLink() {
        bluetoothManager.disconnect()
        withTimeoutOrNull(1000) { initialized.first { !it } }
    }

    private fun recordPanelResult(contentName: String, panel: MyPanel, outcome: PanelSendOutcome) {
        val name = "$contentName → ${panelLabel(panel)}"
        when (outcome) {
            PanelSendOutcome.Sent -> recordUpload(UploadOutcome.Confirmed, name = name)
            PanelSendOutcome.AlreadyPresent -> recordUpload(UploadOutcome.AlreadyPresent, name = name)
            is PanelSendOutcome.Failed -> recordUpload(
                if (outcome.failure == UploadFailure.Cancelled) UploadOutcome.Cancelled else UploadOutcome.Failed, outcome.failure, name)
            PanelSendOutcome.NotInRange, PanelSendOutcome.Skipped -> Unit
        }
    }

    private fun panelLabel(panel: MyPanel): String = panel.name ?: localizedString(R.string.backpack_unknown_device)

    fun setBrightness(level: Int) = settings(BackpackCommands.brightness(level), R.string.backpack_command_brightness) { it.copy(brightness = level) }
    fun setScreen(on: Boolean) = settings(BackpackCommands.screen(on), R.string.backpack_command_screen) { it.copy(screenOn = on) }
    fun setRotation(index: Int, mirror: Boolean) {
        if (advertisement.value?.supportsRotation != true) return
        settings(BackpackCommands.rotate(index, mirror), R.string.backpack_command_rotate) { it.copy(rotationIndex = index, mirror = mirror) }
    }
    fun clearPrograms() = settings(BackpackCommands.clearPrograms(), R.string.backpack_command_clear) { it }
    fun refreshPanelState() = settings(null, R.string.backpack_command_refresh) { it }

    fun ipixelBrightness(level: Int) =
        ipixelSetting(IPixelProtocol.brightnessCommand(level), 0x04) { it.copy(brightness = level) }
    fun ipixelRotation(quarterTurns: Int) =
        ipixelSetting(IPixelProtocol.rotationCommand(quarterTurns), 0x06) { it.copy(quarterTurns = quarterTurns) }
    fun ipixelScreen(on: Boolean) = ipixelSetting(IPixelProtocol.screenCommand(on), 0x07) { it.copy(screenOn = on) }
    fun ipixelClock(style: Int, use24h: Boolean, showDate: Boolean) =
        ipixelSetting(IPixelProtocol.clockCommand(style, use24h, showDate, java.time.LocalDate.now()), 0x06) {
            it.copy(clockStyle = style, use24h = use24h, showDate = showDate)
        }

    @Volatile private var lastRcspResponse: ByteArray? = null

    /**
     * Read-only diagnostics: dumps the stored advertisement, the Cmd 0x10 / 0x0D answers and the
     * already received RCSP answer to the BLE log. Sends only the two query commands (no auth, no writes).
     */
    fun dumpBackpackInfo() {
        if (isIPixel()) return
        if (!connectionStatus.value.startsWith("Ready") || !initialized.value || uploadOperation.isOccupied || settingsOperation.isOccupied) return
        settingsBusy.value = true
        settingsOperation.launch(scope) {
            try {
                operationMutex.withLock {
                    fun emit(lines: List<String>) = lines.forEach { bluetoothManager.addBleLog(it) }
                    bluetoothManager.addBleLog("--- Backpack info ---")
                    emit(BackpackInfoFormatter.formatAdvertisement(bluetoothManager.storedScanRecord()))
                    emit(BackpackInfoFormatter.formatState(
                        bluetoothManager.sendCommand(BackpackCommands.queryState())))
                    emit(BackpackInfoFormatter.formatBuiltIn(
                        bluetoothManager.sendCommand(BackpackCommands.queryBuiltInCount(), timeoutMs = 1500L)))
                    emit(BackpackInfoFormatter.formatRcsp(lastRcspResponse))
                    bluetoothManager.addBleLog("--- end ---")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                bluetoothManager.addBleLog("Backpack info failed: ${e.javaClass.simpleName}")
            } finally {
                settingsBusy.value = false
            }
        }
    }

    /**
     * Read-only diagnostics: sends one Cmd 0x0F status query (six zero bytes, never a password
     * or a Cmd 0x0E write) and logs the raw reply plus its decoded meaning.
     */
    fun queryPasswordStatus() {
        if (isIPixel()) return
        if (!connectionStatus.value.startsWith("Ready") || !initialized.value || uploadOperation.isOccupied || settingsOperation.isOccupied) return
        settingsBusy.value = true
        settingsOperation.launch(scope) {
            try {
                operationMutex.withLock {
                    bluetoothManager.addBleLog("--- Password status ---")
                    BackpackPasswordStatus.format(
                        bluetoothManager.sendCommand(BackpackPasswordStatus.queryFrame())
                    ).forEach { bluetoothManager.addBleLog(it) }
                    bluetoothManager.addBleLog("--- end ---")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                bluetoothManager.addBleLog("Password status failed: ${e.javaClass.simpleName}")
            } finally {
                settingsBusy.value = false
            }
        }
    }

    private fun settings(frame: ByteArray?, @StringRes label: Int, update: (BackpackCommands.State) -> BackpackCommands.State) {
        if (isIPixel()) return
        if (!connectionStatus.value.startsWith("Ready") || !initialized.value || uploadOperation.isOccupied || settingsOperation.isOccupied) return
        settingsBusy.value = true
        settingsOperation.launch(scope) {
            try {
                operationMutex.withLock {
                    _commandError.value = null
                    ensureAuth()?.let { error(it) }
                    if (frame != null) {
                        val response = bluetoothManager.sendCommand(frame)
                        // An unanswered user command could arrive late and acknowledge its retry.
                        // Close this uncertain session while still holding transaction ownership.
                        if (response == null) bluetoothManager.disconnect()
                        requireSuccess(response, localizedString(label))
                        _panelState.value = _panelState.value?.let(update)
                    }
                    refreshState()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logCommandError(e)
                _commandError.value = localizedString(R.string.backpack_command_failed, localizedString(label))
            } finally {
                settingsBusy.value = false
            }
        }
    }

    /** One iPixel setting under the same single-operation rule as [settings]; OK answer `05 00 id xx 01` within 2 s. */
    private fun ipixelSetting(frame: ByteArray, commandId: Int, update: (IPixelSettings) -> IPixelSettings) {
        if (!isIPixel()) return
        if (!connectionStatus.value.startsWith("Ready") || !initialized.value || uploadOperation.isOccupied || settingsOperation.isOccupied) return
        settingsBusy.value = true
        settingsOperation.launch(scope) {
            try {
                operationMutex.withLock {
                    _commandError.value = null
                    val acked = coroutineScope {
                        // Listen before writing so a fast answer cannot be missed.
                        val ack = async(start = CoroutineStart.UNDISPATCHED) {
                            withTimeoutOrNull(2000) { bluetoothManager.ipixelNotifications.first { IPixelProtocol.isSettingAck(it, commandId) } }
                        }
                        if (bluetoothManager.writeIPixel(frame)) ack.await() != null else false.also { ack.cancel() }
                    }
                    check(acked) { "iPixel setting 0x%02X: no OK answer".format(commandId) }
                    _ipixelSettings.value = update(_ipixelSettings.value)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logCommandError(e)
                _commandError.value = localizedString(R.string.ipixel_setting_failed)
            } finally {
                settingsBusy.value = false
            }
        }
    }

    /** iPixel info command: sets the clock and reads the type byte (≤ 2 s); a missing answer is only logged. */
    private suspend fun queryIPixelType() = coroutineScope {
        val address = bluetoothManager.connectedAddress
        // Listen before writing so a fast reply cannot be missed.
        val reply = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(2000) { bluetoothManager.ipixelNotifications.mapNotNull(IPixelProtocol::parseDeviceInfo).first() }
        }
        val now = java.util.Calendar.getInstance()
        val written = bluetoothManager.writeIPixel(IPixelProtocol.deviceInfoCommand(
            now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE), now.get(java.util.Calendar.SECOND)))
        val type = if (written) reply.await() else null.also { reply.cancel() }
        val note = when {
            !written -> "iPixel info: write failed"
            type == null -> "iPixel info: no answer"
            else -> "iPixel info: type=0x%02X".format(type)
        }
        Log.i("BackpackBLE", note)
        bluetoothManager.addBleLog(note)
        if (type != null && address != null) bluetoothManager.updateIPixelType(address, type)
    }

    /** Cmd 0D → built-in programme count (7 B or 8 B answer). Optional: a missing answer only hides the feature. */
    private suspend fun queryBuiltInCount() {
        val response = bluetoothManager.sendCommand(BackpackCommands.queryBuiltInCount(), timeoutMs = 1500L)
        val count = response?.let(BackpackCommands::parseBuiltInCount)
        _builtInCount.value = count
        val note = if (count == null) "Built-in programmes: no valid Cmd 0D answer" else "Built-in programmes: $count"
        Log.i("BackpackBLE", note)
        bluetoothManager.addBleLog(note)
    }

    private suspend fun refreshState() {
        val response = bluetoothManager.sendCommand(BackpackCommands.queryState()) ?: error("State query: no response")
        _panelState.value = BackpackCommands.parseState(response) ?: error("Invalid response to state query")
    }
    private fun quantityString(id: Int, quantity: Int): String =
        ContextCompat.getContextForLanguage(application).resources.getQuantityString(id, quantity, quantity)
    private fun localizedString(id: Int, vararg args: Any): String =
        ContextCompat.getContextForLanguage(application).getString(id, *args)
    /** The localized UI text hides the technical cause; keep it in the diagnostics log. */
    private fun logCommandError(e: Exception) {
        val note = "Command failed: ${e.javaClass.simpleName}: ${e.message}"
        Log.w("BackpackBLE", note, e)
        bluetoothManager.addBleLog(note)
    }
    private fun requireSuccess(response: ByteArray?, label: String) {
        requireNotNull(response) { "$label: no response" }
        val status = if (response.size >= 7) response[4].toInt() and 0xFF else -1
        check(status == 1 || status == 3) { "$label: device rejected the command (status $status)" }
    }
    /** What a "send to all my panels" run sends; each panel gets it rendered or converted for itself. */
    private sealed interface MultiPanelContent {
        val name: String
        /** A library GIF, read once; converted per panel like a single send. */
        class Library(override val name: String, val uri: Uri) : MultiPanelContent
        /** A ready GIF (editor output) with its backpack [playback]. */
        class ReadyGif(override val name: String, val gif: ByteArray, val playback: EditorPlaybackOptions?) : MultiPanelContent
        /** A text banner, rendered at each panel's own size. */
        class Banner(override val name: String, val options: TextBannerOptions) : MultiPanelContent
    }

    private companion object {
        const val HISTORY_KEY = "entries"
        /** A staged sequence not started within this time is dropped (e.g. permissions were never granted). */
        const val STAGED_SEQUENCE_TTL_MS = 120_000L
        val activeStages = setOf(UploadStage.Preparing, UploadStage.Connecting, UploadStage.Sending, UploadStage.Finishing)
    }
    private suspend fun ensureAuth(): String? {
        // JieLi RCSP auth handshake on AE01/AE02, once per connection.
        if (bluetoothManager.authCompleted) {
            Log.d("BackpackBLE", "Auth already completed this session — skipping")
        } else {
            Log.d("BackpackBLE", "Starting JieLi auth handshake...")
            bluetoothManager.resetAuthState()

            // Step 1: APP → AE01: [0x00 + 16 random bytes]
            val authInit = com.batoh.core.data.bluetooth.JieLiAuth.getRandomAuthData()
            Log.d("BackpackBLE", "Auth Step1: sending random challenge [${authInit.joinToString("") { "%02X".format(it) }}]")
            val step2response = bluetoothManager.writeAuthSuspend(authInit)
            if (step2response == null || step2response.size < 17) {
                Log.e("BackpackBLE", "Auth Step2 FAILED: no response or too short (${step2response?.size})")
                return "Auth step 2: no/short response (${step2response?.size ?: 0}B)"
            }
            Log.d("BackpackBLE", "Auth Step2: device proof [${step2response.joinToString("") { "%02X".format(it) }}]")

            // Step 3: Verify device proof (optional — we trust the device)
            val expectedProof = com.batoh.core.data.bluetooth.JieLiAuth.getEncryptedAuthData(authInit)
            val proofMatch = step2response.contentEquals(expectedProof)
            Log.d("BackpackBLE", "Auth Step3: device proof ${if (proofMatch) "MATCHES" else "MISMATCH (continuing anyway)"}")

            // Step 4: APP → AE01: [0x02, 'p','a','s','s'] (password)
            val authPassword = byteArrayOf(0x02, 0x70, 0x61, 0x73, 0x73) // "pass"
            Log.d("BackpackBLE", "Auth Step4: sending password")
            val step5response = bluetoothManager.writeAuthSuspend(authPassword)
            if (step5response == null || step5response.size < 17) {
                Log.e("BackpackBLE", "Auth Step5 FAILED: no response or too short (${step5response?.size})")
                return "Auth step 5: no/short response (${step5response?.size ?: 0}B)"
            }
            Log.d("BackpackBLE", "Auth Step5: device challenge [${step5response.joinToString("") { "%02X".format(it) }}]")

            // Step 6: APP → AE01: encrypted response to device challenge
            val authResponse = com.batoh.core.data.bluetooth.JieLiAuth.getEncryptedAuthData(step5response)
            Log.d("BackpackBLE", "Auth Step6: sending encrypted response [${authResponse.joinToString("") { "%02X".format(it) }}]")
            val step7response = bluetoothManager.writeAuthSuspend(authResponse)
            if (step7response != null) {
                val hex = step7response.joinToString("") { "%02X".format(it) }
                Log.d("BackpackBLE", "Auth Step7: final response [$hex]")
                // Expected: [0x02, 'p','a','s','s'] = auth OK
                if (step7response.size >= 5 && step7response[0] == 0x02.toByte()) {
                    Log.d("BackpackBLE", "AUTH SUCCESS!")
                } else {
                    Log.w("BackpackBLE", "Auth response unexpected — proceeding anyway")
                }
            } else {
                Log.w("BackpackBLE", "Auth Step7: no final response — proceeding anyway")
            }
            delay(200)

            // RCSP device info request (as manufacturer does after auth)
            Log.d("BackpackBLE", "Sending RCSP device info request...")
            val rcspDevInfo = byteArrayOf(
                0xFE.toByte(), 0xDC.toByte(), 0xBA.toByte(), 0xC0.toByte(),
                0x03, 0x00, 0x06, 0x00,
                0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
                0x00, 0xEF.toByte()
            )
            val devInfoResp = bluetoothManager.writeAuthSuspend(rcspDevInfo, timeoutMs = 2000)
            lastRcspResponse = devInfoResp
            if (devInfoResp != null) {
                Log.d("BackpackBLE", "RCSP device info [${devInfoResp.size}B]: ${devInfoResp.joinToString("") { "%02X".format(it) }}")
            } else {
                Log.w("BackpackBLE", "RCSP device info: no response — proceeding")
            }
            bluetoothManager.markAuthCompleted()
            delay(200)
        }
        return null
    }

    /** Upload the entire programme payload, with its original file ID and length. */
    private suspend fun doUpload(payload: ByteArray): UploadCompletion {
        if (payload.size < 24) throw UploadFailureException(UploadFailure.PayloadInvalid)
        ensureAuth()?.let { throw UploadFailureException(UploadFailure.AuthFailed, IllegalStateException(it)) }
        // iledcolor 1.0.58 sends only Cmd 06 → chunks → end; Cmd 0D/07 are not part of an upload
        // (re_iledcolor/02_gif_resource_upload.md §4).
        val start = bluetoothManager.sendCommand(BackpackPayload.startFrame(payload))
        uploadStartFailure(start)?.let { throw UploadFailureException(it) }
        if (start != null && classifyUploadStart(start) == UploadStartDecision.AlreadyPresent) return UploadCompletion.AlreadyPresent
        sendDataAndEnd(payload, window = uploadWindow.value)
        return UploadCompletion.Uploaded
    }

    /** Sends [gif] to slot 0 of the connected iPixel panel, window by window (see [IPixelUploader]). */
    private suspend fun doIPixelUpload(gif: ByteArray): UploadCompletion {
        val startedAt = SystemClock.elapsedRealtime()
        IPixelUploader(bluetoothManager::writeIPixel, bluetoothManager.ipixelNotifications)
            .upload(gif, isGif = true) { updateUpload(UploadStage.Sending, it) }
        val summary = "iPixel upload: ${gif.size} B in ${SystemClock.elapsedRealtime() - startedAt} ms"
        Log.i("BackpackBLE", summary)
        bluetoothManager.addBleLog(summary)
        return UploadCompletion.Uploaded
    }

    /**
     * Experimental playlist: Cmd 0x03 announces each programme (ACK 1 = send data, 3 = already stored,
     * anything else aborts), then Cmd 0x08 closes the list. No Cmd 0x02 or other writing command is sent.
     */
    private suspend fun doPlaylist(payloads: List<ByteArray>): UploadCompletion {
        if (payloads.any { it.size < 24 }) throw UploadFailureException(UploadFailure.PayloadInvalid)
        ensureAuth()?.let { throw UploadFailureException(UploadFailure.AuthFailed, IllegalStateException(it)) }
        for ((index, payload) in payloads.withIndex()) {
            val ack = bluetoothManager.sendCommand(BackpackPlaylist.itemFrame(index, payloads.size, payload))
                ?: throw UploadFailureException(UploadFailure.HeaderTimeout)
            when (val status = ackStatus(ack)) {
                1 -> sendDataAndEnd(payload, index, payloads.size)
                3 -> bluetoothManager.addBleLog("Playlist item ${index + 1}: already stored, data skipped")
                2 -> throw UploadFailureException(UploadFailure.InsufficientSpace)
                else -> throw UploadFailureException(UploadFailure.HeaderRejected(status))
            }
        }
        val done = bluetoothManager.sendCommand(BackpackPlaylist.endFrame())
            ?: throw UploadFailureException(UploadFailure.EndTimeout)
        val status = ackStatus(done)
        if (status != 1) throw UploadFailureException(UploadFailure.EndRejected(status))
        return UploadCompletion.Uploaded
    }

    /** Sends all data chunks of [payload] on A952 followed by the `54 01` end frame, waiting for ACK/echo. */
    private suspend fun sendDataAndEnd(payload: ByteArray, itemIndex: Int = 0, itemCount: Int = 1, window: Int = 1) {
        val mtu = bluetoothManager.mtu
        if (BackpackFrame.chunkLength(mtu) < 64) throw UploadFailureException(UploadFailure.MtuTooSmall(mtu))
        val packets = BackpackFrame.dataChunks(payload, mtu)
        val startedAt = SystemClock.elapsedRealtime()
        if (window > 1) sendChunksPipelined(packets, window, itemIndex, itemCount) else
        for ((index, packet) in packets.withIndex()) {
            bluetoothManager.resetAckState()
            val written = bluetoothManager.writeDataSuspend(packet)
            val acked = written && withTimeoutOrNull(2000) { bluetoothManager.lastAckIndex.first { it == index } } != null
            chunkFailure(index, packets.size, written, acked, bluetoothManager.lastAckStatus)
                ?.let { throw UploadFailureException(it) }
            updateUpload(UploadStage.Sending, (itemIndex + (index + 1).toFloat() / packets.size) / itemCount)
        }
        val summary = "Chunks: ${packets.size} in ${SystemClock.elapsedRealtime() - startedAt} ms (window $window)"
        Log.i("BackpackBLE", summary)
        bluetoothManager.addBleLog(summary)
        updateUpload(UploadStage.Finishing, (itemIndex + 1).toFloat() / itemCount)
        endFailure(bluetoothManager.sendCommand(BackpackFrame.end(), charUuid = BluetoothLeManager.CHAR_DATA_UUID))
            ?.let { throw UploadFailureException(it) }
    }

    /**
     * Sends chunks with up to [window] unacknowledged. ACKs must arrive in order with status 1 (a gap or any other status aborts, a repeat is ignored), and 2 s without a new ACK is a stall. Any failure closes the link so
     * that late ACKs of in-flight chunks cannot leak into the next transfer.
     */
    private suspend fun sendChunksPipelined(packets: List<ByteArray>, window: Int, itemIndex: Int, itemCount: Int) = coroutineScope {
        val total = packets.size
        val acked = java.util.concurrent.atomic.AtomicInteger(0)
        val lastAckAt = java.util.concurrent.atomic.AtomicLong(SystemClock.elapsedRealtime())
        val rejected = java.util.concurrent.atomic.AtomicReference<UploadFailure?>(null)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            bluetoothManager.ackEvents.collect { (index, status) ->
                val expected = acked.get()
                when {
                    status != 1 || index > expected -> rejected.compareAndSet(null, UploadFailure.ChunkRejected(index, total, status))
                    // The firmware sometimes repeats an ACK; a repeat of an already confirmed chunk is harmless.
                    index < expected -> Log.w("BackpackBLE", "Duplicate ACK for chunk $index")
                    else -> {
                        acked.incrementAndGet()
                        lastAckAt.set(SystemClock.elapsedRealtime())
                    }
                }
            }
        }
        // Suspends until [done] holds; fails on a rejected ACK or when no ACK arrived for 2 s.
        suspend fun await(done: () -> Boolean) {
            while (!done()) {
                rejected.get()?.let { throw UploadFailureException(it) }
                if (SystemClock.elapsedRealtime() - lastAckAt.get() > 2000) throw UploadFailureException(UploadFailure.ChunkTimeout(acked.get(), total))
                delay(2)
            }
            rejected.get()?.let { throw UploadFailureException(it) }
        }
        try {
            for ((index, packet) in packets.withIndex()) {
                await { index - acked.get() < window }
                if (!bluetoothManager.writeDataSuspend(packet)) throw UploadFailureException(UploadFailure.ChunkWriteFailed(index, total))
                updateUpload(UploadStage.Sending, (itemIndex + acked.get().toFloat() / total) / itemCount)
            }
            await { acked.get() >= total }
        } catch (e: Exception) {
            if (e !is CancellationException) bluetoothManager.disconnect()
            throw e
        } finally {
            collector.cancel()
        }
    }
}
