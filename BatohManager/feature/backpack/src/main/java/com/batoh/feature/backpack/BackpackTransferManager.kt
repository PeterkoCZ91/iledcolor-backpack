package com.batoh.feature.backpack

import android.app.Application
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import android.bluetooth.BluetoothDevice
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.batoh.core.conversion.VideoToGifConverter
import com.batoh.core.conversion.SafeGifDecoder
import com.batoh.core.conversion.EffectTestGif
import com.batoh.core.conversion.TestPatternGif
import com.batoh.core.data.bluetooth.BackpackCommands
import com.batoh.core.data.bluetooth.BackpackInfoFormatter
import com.batoh.core.data.bluetooth.BackpackPasswordStatus
import com.batoh.core.data.bluetooth.BackpackPayload
import com.batoh.core.data.bluetooth.BackpackPlaylist
import com.batoh.core.data.bluetooth.BackpackFrame
import com.batoh.core.data.bluetooth.BluetoothLeManager
import com.batoh.core.data.bluetooth.UploadFailure
import com.batoh.core.data.bluetooth.UploadHistory
import com.batoh.core.data.bluetooth.UploadHistoryEntry
import com.batoh.core.data.bluetooth.UploadOutcome
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
    val isSequence: Boolean = false
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
    private var userCancelledUpload = false
    /** Set when the link dropped while chunks were being sent, so the cancellation reads as a lost connection. */
    @Volatile private var connectionLostDuringUpload = false
    private data class UploadRequest(val name: String, val uri: Uri?, val payload: ByteArray? = null, val test: Boolean = false, val effect: Int? = null, val playlist: Boolean = false, val sequence: List<ByteArray>? = null)
    /** A user-built programme sequence waiting for the backpack screen to grant permissions and start it. */
    private var stagedSequence: StagedSequence? = null
    private class StagedSequence(val payloads: List<ByteArray>, val stagedAtMs: Long)
    private val initialized = MutableStateFlow(false)
    private val settingsBusy = MutableStateFlow(false)
    private val transferBusy = MutableStateFlow(false)
    val busy = combine(settingsBusy, transferBusy) { settings, transfer -> settings || transfer }
        .stateIn(scope, SharingStarted.Eagerly, false)
    val connectionStatus = bluetoothManager.connectionStatus
    val scannedDevices = bluetoothManager.scannedDevices
    val bleLog = bluetoothManager.bleLog
    private val _uploadState = MutableStateFlow(UploadState())
    val uploadState = _uploadState.asStateFlow()
    val uploadProgress = uploadState.map { if (it.stage in activeStages) it.progress else -1f }
        .stateIn(scope, SharingStarted.Eagerly, -1f)
    val uploadError = uploadState.map { if (it.stage == UploadStage.Error) it.message else null }
        .stateIn(scope, SharingStarted.Eagerly, null)
    private val _commandError = MutableStateFlow<String?>(null)
    val commandError = _commandError.asStateFlow()
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
                        connectionLostDuringUpload = true
                        uploadOperation.cancel(CancellationException("Backpack connection lost"))
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
    fun connect(device: BluetoothDevice) {
        if (uploadOperation.isOccupied || settingsBusy.value) return
        bluetoothManager.connect(device)
    }
    fun connectToTarget() {
        if (uploadOperation.isOccupied || settingsBusy.value) return
        bluetoothManager.connectToTarget()
    }
    fun disconnect() {
        cancelUpload()
        settingsOperation.cancel()
        bluetoothManager.disconnect()
    }

    fun uploadGif(uri: Uri) = startUpload(UploadRequest("GIF", uri))
    /** Uploads a ready programme payload (e.g. `BackpackPayload.fromGif(gif, speed =, light =, effect =)`). */
    fun uploadPayload(payload: ByteArray, name: String? = null) =
        startUpload(UploadRequest(name ?: localizedString(R.string.backpack_program_name), null, payload.copyOf()))
    fun sendTestImage() = startUpload(UploadRequest(localizedString(R.string.backpack_test_image_name), null, test = true))
    /** Experimental: one upload of the asymmetric [EffectTestGif] with programme item effect code [effect]. */
    fun sendEffectTest(effect: Int) =
        startUpload(UploadRequest(localizedString(R.string.backpack_effect_test_name, effect), null, test = true, effect = effect))

    /** Experimental: announces two [EffectTestGif] programmes (digits 1 and 2) as a playlist via Cmd 0x03 / 0x08. */
    fun sendPlaylistTest() =
        startUpload(UploadRequest(localizedString(R.string.backpack_playlist_test_name), null, playlist = true))

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
        val staged = stagedSequence ?: return false
        stagedSequence = null
        if (System.currentTimeMillis() - staged.stagedAtMs > STAGED_SEQUENCE_TTL_MS) return false
        startUpload(UploadRequest(quantityString(R.plurals.backpack_sequence_name, staged.payloads.size), null,
            sequence = staged.payloads))
        return true
    }

    /** Plays built-in firmware programme [id] (1-based, ≤ [builtInCount]) via a type-5 item; nothing is stored. */
    fun playBuiltIn(id: Int) {
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

    private fun recordUpload(outcome: UploadOutcome, failure: UploadFailure? = null) {
        val name = _uploadState.value.name ?: localizedString(R.string.backpack_program_name)
        val history = UploadHistory.add(_uploadHistory.value, UploadHistoryEntry(name, System.currentTimeMillis(), outcome, failure))
        _uploadHistory.value = history
        historyPreferences.edit().putString(HISTORY_KEY, UploadHistory.encode(history)).apply()
    }
    fun autoConnectAndUpload() = sendTestImage()
    fun retryUpload() { lastRequest?.let(::startUpload) }

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
        _uploadState.value = _uploadState.value.copy(stage = stage, progress = progress, message = message, failure = failure)
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
    internal suspend fun prepareProgramme(gif: ByteArray): PreparedProgramme {
        val dimensions = BackpackPayload.gifSize(gif) ?: throw UploadFailureException(UploadFailure.NotAGif)
        var frames = 0
        val converted = try {
            val taskContext = currentCoroutineContext()
            frames = SafeGifDecoder.validate(gif) { taskContext.ensureActive() }.frameCount
            if (dimensions == (64 to 64)) gif else converter.convertGifBytes(gif)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw UploadFailureException(UploadFailure.GifInvalid, e)
        }
        return PreparedProgramme(buildPayload { BackpackPayload.fromGif(converted) }, frames)
    }

    /** Reads, validates and (if needed) converts the GIF at [uri] into a programme payload. */
    private suspend fun gifPayload(uri: Uri): ByteArray {
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
        return prepareProgramme(gif).payload
    }

    private inline fun buildPayload(block: () -> ByteArray): ByteArray = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw UploadFailureException(UploadFailure.PayloadInvalid, e)
    }

    private fun startUpload(request: UploadRequest) {
        if (uploadOperation.isOccupied || settingsOperation.isOccupied) return
        lastRequest = request
        userCancelledUpload = false
        connectionLostDuringUpload = false
        transferBusy.value = true
        _uploadState.value = UploadState(UploadStage.Preparing, request.name, request.uri?.toString(), isSequence = request.sequence != null)
        uploadOperation.launch(scope) {
            try {
                val payloads = withContext(Dispatchers.IO) {
                    request.sequence?.let { return@withContext it }
                    if (request.playlist) return@withContext listOf(1, 2).map { digit ->
                        buildPayload { BackpackPayload.fromGif(EffectTestGif.render(digit)) }
                    }
                    listOf(when {
                        request.payload != null -> request.payload
                        request.effect != null -> buildPayload { BackpackPayload.fromGif(EffectTestGif.render(request.effect), effect = request.effect) }
                        request.test -> buildPayload { BackpackPayload.fromGif(TestPatternGif.render()) }
                        else -> gifPayload(requireNotNull(request.uri))
                    })
                }
                ensureActive()
                updateUpload(UploadStage.Connecting)
                val status = connectionStatus.value
                if (!status.startsWith("Ready") && !status.startsWith("Connecting") && !status.startsWith("Connected")) {
                    bluetoothManager.connectToTarget()
                }
                val ready = withTimeoutOrNull(20000) {
                    combine(connectionStatus, initialized) { state, init -> state.startsWith("Ready") && init }.first { it }
                }
                if (ready == null) throw UploadFailureException(UploadFailure.NotConnected)
                operationMutex.withLock {
                    if (!connectionStatus.value.startsWith("Ready")) throw UploadFailureException(UploadFailure.ConnectionLost)
                    updateUpload(UploadStage.Sending)
                    try {
                        val completion = if (request.playlist || request.sequence != null) doPlaylist(payloads) else doUpload(payloads.single())
                        val message = when (completion) {
                            // A sequence shows its success text once, as the stage line; no extra message.
                            UploadCompletion.Uploaded -> if (request.sequence != null) null else localizedString(R.string.backpack_upload_success)
                            UploadCompletion.AlreadyPresent -> localizedString(R.string.backpack_upload_already_present)
                        }
                        updateUpload(UploadStage.Success, 1f, message)
                        recordUpload(if (completion == UploadCompletion.Uploaded) UploadOutcome.Confirmed else UploadOutcome.AlreadyPresent)
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

    fun setBrightness(level: Int) = settings(BackpackCommands.brightness(level), R.string.backpack_command_brightness) { it.copy(brightness = level) }
    fun setScreen(on: Boolean) = settings(BackpackCommands.screen(on), R.string.backpack_command_screen) { it.copy(screenOn = on) }
    fun setRotation(index: Int, mirror: Boolean) {
        if (advertisement.value?.supportsRotation != true) return
        settings(BackpackCommands.rotate(index, mirror), R.string.backpack_command_rotate) { it.copy(rotationIndex = index, mirror = mirror) }
    }
    fun clearPrograms() = settings(BackpackCommands.clearPrograms(), R.string.backpack_command_clear) { it }
    fun refreshPanelState() = settings(null, R.string.backpack_command_refresh) { it }

    @Volatile private var lastRcspResponse: ByteArray? = null

    /**
     * Read-only diagnostics: dumps the stored advertisement, the Cmd 0x10 / 0x0D answers and the
     * already received RCSP answer to the BLE log. Sends only the two query commands (no auth, no writes).
     */
    fun dumpBackpackInfo() {
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
        sendDataAndEnd(payload)
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
    private suspend fun sendDataAndEnd(payload: ByteArray, itemIndex: Int = 0, itemCount: Int = 1) {
        val mtu = bluetoothManager.mtu
        if (BackpackFrame.chunkLength(mtu) < 64) throw UploadFailureException(UploadFailure.MtuTooSmall(mtu))
        val packets = BackpackFrame.dataChunks(payload, mtu)
        for ((index, packet) in packets.withIndex()) {
            bluetoothManager.resetAckState()
            val written = bluetoothManager.writeDataSuspend(packet)
            val acked = written && withTimeoutOrNull(2000) { bluetoothManager.lastAckIndex.first { it == index } } != null
            chunkFailure(index, packets.size, written, acked, bluetoothManager.lastAckStatus)
                ?.let { throw UploadFailureException(it) }
            updateUpload(UploadStage.Sending, (itemIndex + (index + 1).toFloat() / packets.size) / itemCount)
        }
        updateUpload(UploadStage.Finishing, (itemIndex + 1).toFloat() / itemCount)
        endFailure(bluetoothManager.sendCommand(BackpackFrame.end(), charUuid = BluetoothLeManager.CHAR_DATA_UUID))
            ?.let { throw UploadFailureException(it) }
    }
}
