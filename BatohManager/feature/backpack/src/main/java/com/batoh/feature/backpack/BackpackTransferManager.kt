package com.batoh.feature.backpack

import android.app.Application
import androidx.core.content.ContextCompat
import android.bluetooth.BluetoothDevice
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.batoh.core.conversion.VideoToGifConverter
import com.batoh.core.conversion.SafeGifDecoder
import com.batoh.core.conversion.TestPatternGif
import com.batoh.core.data.bluetooth.BackpackCommands
import com.batoh.core.data.bluetooth.BackpackPayload
import com.batoh.core.data.bluetooth.BackpackFrame
import com.batoh.core.data.bluetooth.BluetoothLeManager
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
    val message: String? = null
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
    private data class UploadRequest(val name: String, val uri: Uri?, val payload: ByteArray? = null, val test: Boolean = false)
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
                        uploadOperation.cancel(CancellationException("Spojení s batohem bylo přerušeno"))
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
                        if (adv == null || adv.syncsTimeOnConnect) requireSuccess(clock, "Nastavení času")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _commandError.value = e.message
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

    private fun recordUpload(outcome: UploadOutcome) {
        val name = _uploadState.value.name ?: localizedString(R.string.backpack_program_name)
        val history = UploadHistory.add(_uploadHistory.value, UploadHistoryEntry(name, System.currentTimeMillis(), outcome))
        _uploadHistory.value = history
        historyPreferences.edit().putString(HISTORY_KEY, UploadHistory.encode(history)).apply()
    }
    fun autoConnectAndUpload() = sendTestImage()
    fun retryUpload() { lastRequest?.let(::startUpload) }

    fun cancelUpload() {
        if (!uploadOperation.isOccupied) return
        userCancelledUpload = true
        val sending = _uploadState.value.stage in setOf(UploadStage.Sending, UploadStage.Finishing)
        uploadOperation.cancel(CancellationException("Nahrávání bylo zrušeno"))
        // No verified protocol abort exists; reset the connection after partial data.
        if (sending) bluetoothManager.disconnect()
    }

    private fun updateUpload(stage: UploadStage, progress: Float = _uploadState.value.progress, message: String? = null) {
        _uploadState.value = _uploadState.value.copy(stage = stage, progress = progress, message = message)
    }

    private fun startUpload(request: UploadRequest) {
        if (uploadOperation.isOccupied || settingsOperation.isOccupied) return
        lastRequest = request
        userCancelledUpload = false
        transferBusy.value = true
        _uploadState.value = UploadState(UploadStage.Preparing, request.name, request.uri?.toString())
        uploadOperation.launch(scope) {
            try {
                val payload = withContext(Dispatchers.IO) {
                    when {
                        request.payload != null -> request.payload
                        request.test -> BackpackPayload.fromGif(TestPatternGif.render())
                        else -> {
                            val uri = requireNotNull(request.uri)
                            val name = runCatching {
                                application.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                                    if (it.moveToFirst()) it.getString(0) else null
                                }
                            }.getOrNull() ?: uri.lastPathSegment ?: "GIF"
                            _uploadState.value = _uploadState.value.copy(name = name)
                            val gif = application.contentResolver.openInputStream(uri)?.use {
                                val output = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val size = it.read(buffer)
                                    if (size < 0) break
                                    require(output.size().toLong() + size <= SafeGifDecoder.MAX_BYTES) { "GIF je větší než 20 MB" }
                                    output.write(buffer, 0, size)
                                }
                                output.toByteArray()
                            }
                                ?: error("GIF nelze načíst")
                            val taskContext = currentCoroutineContext()
                            SafeGifDecoder.validate(gif) { taskContext.ensureActive() }
                            val dimensions = requireNotNull(BackpackPayload.gifSize(gif)) { "Vybraný soubor není GIF" }
                            val converted = if (dimensions == (64 to 64)) gif else converter.convertGifBytes(gif)
                            BackpackPayload.fromGif(converted)
                        }
                    }
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
                check(ready != null) { "Batoh se nepodařilo připojit. Zkus nahrávání znovu." }
                operationMutex.withLock {
                    check(connectionStatus.value.startsWith("Ready")) { "Batoh je odpojený" }
                    updateUpload(UploadStage.Sending)
                    try {
                        val completion = doUpload(payload)
                        val message = when (completion) {
                            UploadCompletion.Uploaded -> localizedString(R.string.backpack_upload_success)
                            UploadCompletion.AlreadyPresent -> localizedString(R.string.backpack_upload_already_present)
                        }
                        updateUpload(UploadStage.Success, 1f, message)
                        recordUpload(if (completion == UploadCompletion.Uploaded) UploadOutcome.Confirmed else UploadOutcome.AlreadyPresent)
                    } catch (e: Exception) {
                        // Keep transaction ownership until the uncertain BLE session is closed.
                        bluetoothManager.disconnect()
                        throw e
                    }
                }
            } catch (e: CancellationException) {
                updateUpload(if (userCancelledUpload) UploadStage.Cancelled else UploadStage.Error,
                    message = localizedString(if (userCancelledUpload) R.string.backpack_upload_cancelled else R.string.backpack_upload_failed))
                recordUpload(if (userCancelledUpload) UploadOutcome.Cancelled else UploadOutcome.Failed)
                throw e
            } catch (e: Exception) {
                updateUpload(UploadStage.Error, message = localizedString(R.string.backpack_upload_failed))
                recordUpload(UploadOutcome.Failed)
            } finally {
                transferBusy.value = false
            }
        }
    }

    fun setBrightness(level: Int) = settings(BackpackCommands.brightness(level), "Nastavení jasu") { it.copy(brightness = level) }
    fun setScreen(on: Boolean) = settings(BackpackCommands.screen(on), "Nastavení displeje") { it.copy(screenOn = on) }
    fun setRotation(index: Int, mirror: Boolean) {
        if (advertisement.value?.supportsRotation != true) return
        settings(BackpackCommands.rotate(index, mirror), "Otočení displeje") { it.copy(rotationIndex = index, mirror = mirror) }
    }
    fun clearPrograms() = settings(BackpackCommands.clearPrograms(), "Smazání programů") { it }
    fun refreshPanelState() = settings(null, "Načtení stavu") { it }

    private fun settings(frame: ByteArray?, label: String, update: (BackpackCommands.State) -> BackpackCommands.State) {
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
                        requireSuccess(response, label)
                        _panelState.value = _panelState.value?.let(update)
                    }
                    refreshState()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _commandError.value = localizedString(R.string.backpack_command_failed, label)
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
        val response = bluetoothManager.sendCommand(BackpackCommands.queryState()) ?: error("Dotaz na stav: žádná odpověď")
        _panelState.value = BackpackCommands.parseState(response) ?: error("Neplatná odpověď na dotaz na stav")
    }
    private fun localizedString(id: Int, vararg args: Any): String =
        ContextCompat.getContextForLanguage(application).getString(id, *args)
    private fun requireSuccess(response: ByteArray?, label: String) {
        requireNotNull(response) { "$label: žádná odpověď" }
        val status = if (response.size >= 7) response[4].toInt() and 0xFF else -1
        check(status == 1 || status == 3) { "$label: zařízení odmítlo příkaz (stav $status)" }
    }
    private companion object {
        const val HISTORY_KEY = "entries"
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
        require(payload.size >= 24) { "Payload je příliš krátký" }
        ensureAuth()?.let { error(it) }
        // iledcolor 1.0.58 sends only Cmd 06 → chunks → end; Cmd 0D/07 are not part of an upload
        // (re_iledcolor/02_gif_resource_upload.md §4).
        val start = bluetoothManager.sendCommand(BackpackPayload.startFrame(payload))
            ?: error("Cmd 06: žádná odpověď")
        when (classifyUploadStart(start)) {
            UploadStartDecision.AlreadyPresent -> return UploadCompletion.AlreadyPresent
            UploadStartDecision.SendChunks -> Unit
            UploadStartDecision.InsufficientSpace -> error("Batoh nemá dostatek volného místa")
            UploadStartDecision.Rejected -> error("Batoh odmítl upload")
        }
        val mtu = bluetoothManager.mtu
        if (BackpackFrame.chunkLength(mtu) < 64) error("MTU $mtu je příliš malé pro upload")
        val packets = BackpackFrame.dataChunks(payload, mtu)
        for ((index, packet) in packets.withIndex()) {
            bluetoothManager.resetAckState()
            if (!bluetoothManager.writeDataSuspend(packet)) error("Zápis paketu $index selhal")
            val ack = withTimeoutOrNull(2000) { bluetoothManager.lastAckIndex.first { it == index } }
            if (ack == null) error("Paket $index: žádná odpověď")
            if (bluetoothManager.lastAckStatus != 1) error("Batoh odmítl paket $index")
            updateUpload(UploadStage.Sending, (index + 1).toFloat() / packets.size)
        }
        updateUpload(UploadStage.Finishing, 1f)
        requireSuccess(
            bluetoothManager.sendCommand(BackpackFrame.end(), charUuid = BluetoothLeManager.CHAR_DATA_UUID),
            "Dokončení uploadu"
        )
        return UploadCompletion.Uploaded
    }
}
