package com.batoh.feature.backpack

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.batoh.core.ui.navigation.Screen
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.batoh.core.common.Result
import com.batoh.core.domain.usecase.GetLocalGifsUseCase
import com.batoh.core.data.bluetooth.BackpackAdvertisement
import com.batoh.core.data.bluetooth.MyPanel
import com.batoh.core.data.bluetooth.NearbyPanel
import com.batoh.core.data.bluetooth.PanelFamily
import com.batoh.core.data.bluetooth.PanelSpec
import com.batoh.core.data.bluetooth.UploadFailure
import com.batoh.core.data.bluetooth.UploadHistoryEntry
import com.batoh.core.data.bluetooth.UploadOutcome
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackpackViewModel @Inject constructor(
    application: Application,
    private val transfers: BackpackTransferManager,
    getLocalGifs: GetLocalGifsUseCase,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    val connectionStatus = transfers.connectionStatus
    val bluetoothEnabled = transfers.bluetoothEnabled
    val uploadWindow = transfers.uploadWindow
    val nearbyPanels = transfers.nearbyPanels
    val connectedPanel = transfers.connectedPanel
    val connectedFamily = transfers.connectedFamily
    val bleLog = transfers.bleLog
    val libraryGifs = getLocalGifs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Result.Loading)
    val uploadState = transfers.uploadState
    val uploadProgress = transfers.uploadProgress
    val uploadError = transfers.uploadError
    val commandError = transfers.commandError
    val busy = transfers.busy
    val panelState = transfers.panelState
    val ipixelSettings = transfers.ipixelSettings
    val advertisement = transfers.advertisement
    val deviceName = transfers.deviceName
    val builtInCount = transfers.builtInCount
    val uploadHistory = transfers.uploadHistory
    val myPanels = transfers.myPanels
    private var pendingGifUri = savedStateHandle.get<String>(Screen.Backpack.ARG_GIF_URI)
    private val pendingToMyPanels = savedStateHandle.get<Boolean>(Screen.Backpack.ARG_MY_PANELS) == true

    fun onPermissionsGranted(): Boolean {
        if (transfers.startStagedSequence()) return true
        val uri = pendingGifUri ?: return false
        pendingGifUri = null
        savedStateHandle.remove<String>(Screen.Backpack.ARG_GIF_URI)
        if (pendingToMyPanels) transfers.uploadGifToMyPanels(Uri.parse(uri)) else requestSend(Uri.parse(uri))
        return true
    }

    private val _chooser = MutableStateFlow<SendChooser>(SendChooser.Hidden)
    /** The "Where to send?" step; Hidden unless a send that starts with a GIF has more or fewer than one candidate. */
    val chooser = _chooser.asStateFlow()
    private val _sendNotice = MutableStateFlow<SendTarget?>(null)
    /** The panel a send just went to without asking ("Sending to ..."); cleared by [consumeSendNotice]. */
    val sendNotice = _sendNotice.asStateFlow()
    private var resolveJob: Job? = null

    private fun connectedNow(): ConnectedPanel? {
        val address = transfers.connectedAddress()?.takeIf { transfers.hasLink() } ?: return null
        val name = transfers.nearbyPanels.value.firstOrNull { it.address == address }?.name ?: transfers.deviceName.value
        return ConnectedPanel(address, name, transfers.connectedPanel.value)
    }

    /**
     * Starts a send of the GIF at [uri] to one panel: with exactly one candidate (the connected panel, or a single panel
     * of mine in range) it is sent at once, otherwise the chooser opens. The size is always the panel's own.
     */
    fun requestSend(uri: Uri) {
        if (transfers.busy.value) return
        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            _chooser.value = SendChooser.Scanning(uri)
            try {
                val mine = transfers.myPanels.value
                val advertising = if (SendTargetPlan.needsScan(connectedNow()?.address, mine)) transfers.scanMyPanels() else emptyList()
                when (val decision = SendTargetPlan.decide(SendTargetPlan.candidates(connectedNow(), mine, advertising))) {
                    is SendDecision.Single -> {
                        _chooser.value = SendChooser.Hidden
                        sendTo(uri, decision.target)
                    }
                    is SendDecision.Choose -> _chooser.value = SendChooser.Choosing(uri, decision.targets, decision.offerAll)
                }
            } finally {
                if (_chooser.value is SendChooser.Scanning) _chooser.value = SendChooser.Hidden
            }
        }
    }

    private fun sendTo(uri: Uri, target: SendTarget) {
        _sendNotice.value = target
        transfers.uploadGifToPanel(uri, target.address)
    }

    fun chooseTarget(target: SendTarget) {
        val uri = (_chooser.value as? SendChooser.Choosing)?.uri ?: return
        _chooser.value = SendChooser.Hidden
        sendTo(uri, target)
    }

    fun chooseAllMyPanels() {
        val uri = (_chooser.value as? SendChooser.Choosing)?.uri ?: return
        _chooser.value = SendChooser.Hidden
        transfers.uploadGifToMyPanels(uri)
    }

    fun retryChooser() {
        val uri = (_chooser.value as? SendChooser.Choosing)?.uri ?: return
        requestSend(uri)
    }

    fun dismissChooser() {
        resolveJob?.cancel()
        _chooser.value = SendChooser.Hidden
    }

    fun consumeSendNotice() { _sendNotice.value = null }
    fun startScan() = transfers.startScan()
    fun stopScan() = transfers.stopScan()
    fun pickPanel(address: String) = transfers.pickPanel(address)
    fun unpickPanel(address: String) = transfers.unpickPanel(address)
    fun connectedAddress(): String? = transfers.connectedAddress()
    /** Drops the link and lists the panels in range again, so another one can be picked. */
    fun switchPanel() {
        transfers.disconnect()
        transfers.startScan()
    }
    fun connectToTarget() = transfers.connectToTarget()
    fun disconnect() = transfers.disconnect()
    fun sendTestImage() = transfers.sendTestImage()
    fun dumpBackpackInfo() = transfers.dumpBackpackInfo()
    fun queryPasswordStatus() = transfers.queryPasswordStatus()
    fun setUploadWindow(window: Int) { transfers.uploadWindow.value = window }
    fun sendEffectTest(effect: Int) = transfers.sendEffectTest(effect)
    fun sendPlaylistTest() = transfers.sendPlaylistTest()
    fun autoConnectAndUpload() = transfers.autoConnectAndUpload()
    fun setBrightness(level: Int) = transfers.setBrightness(level)
    fun setScreen(on: Boolean) = transfers.setScreen(on)
    fun setRotation(index: Int, mirror: Boolean) = transfers.setRotation(index, mirror)
    fun clearPrograms() = transfers.clearPrograms()
    fun ipixelBrightness(level: Int) = transfers.ipixelBrightness(level)
    fun ipixelRotation(quarterTurns: Int) = transfers.ipixelRotation(quarterTurns)
    fun ipixelScreen(on: Boolean) = transfers.ipixelScreen(on)
    fun ipixelClock(style: Int, use24h: Boolean, showDate: Boolean) = transfers.ipixelClock(style, use24h, showDate)
    fun refreshPanelState() = transfers.refreshPanelState()
    fun cancelUpload() = transfers.cancelUpload()
    fun retryUpload() = transfers.retryUpload()
    fun playBuiltIn(id: Int) = transfers.playBuiltIn(id)
    fun clearUploadHistory() = transfers.clearUploadHistory()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackpackScreen(
    onBack: () -> Unit,
    autoTest: Boolean = false,
    viewModel: BackpackViewModel = hiltViewModel()
) {
    val status by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val bluetoothEnabled by viewModel.bluetoothEnabled.collectAsStateWithLifecycle()
    val uploadWindow by viewModel.uploadWindow.collectAsStateWithLifecycle()
    val nearbyPanels by viewModel.nearbyPanels.collectAsStateWithLifecycle()
    val connectedPanel by viewModel.connectedPanel.collectAsStateWithLifecycle()
    val connectedFamily by viewModel.connectedFamily.collectAsStateWithLifecycle(initialValue = null)
    val bleLog by viewModel.bleLog.collectAsStateWithLifecycle()
    val upload by viewModel.uploadState.collectAsStateWithLifecycle()
    val commandError by viewModel.commandError.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val panelState by viewModel.panelState.collectAsStateWithLifecycle()
    val ipixelSettings by viewModel.ipixelSettings.collectAsStateWithLifecycle()
    val advertisement by viewModel.advertisement.collectAsStateWithLifecycle()
    val deviceName by viewModel.deviceName.collectAsStateWithLifecycle()
    val builtInCount by viewModel.builtInCount.collectAsStateWithLifecycle()
    val uploadHistory by viewModel.uploadHistory.collectAsStateWithLifecycle()
    val myPanels by viewModel.myPanels.collectAsStateWithLifecycle()
    val chooser by viewModel.chooser.collectAsStateWithLifecycle()
    val sendNotice by viewModel.sendNotice.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val connectedAddress = remember(status) { viewModel.connectedAddress() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var permissionsGranted by remember { mutableStateOf(false) }
    var showLibraryPicker by remember { mutableStateOf(false) }
    var showSourceSheet by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var showBleLog by remember { mutableStateOf(false) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var brightness by remember { mutableFloatStateOf(5f) }
    val activeUpload = upload.stage in setOf(
        UploadStage.Preparing, UploadStage.Connecting, UploadStage.Sending, UploadStage.Finishing
    )
    val isReady = status.startsWith("Ready")
    val isConnecting = status.startsWith("Connecting") || status.startsWith("Connected")
    val isScanning = status.startsWith("Scanning")
    val iPixel = connectedFamily == PanelFamily.IPixel
    // An iPixel panel whose type is not known yet cannot be rendered for, so nothing is sent to it.
    val sendBlocked = iPixel && connectedPanel == null
    val controlsEnabled = isReady && !busy && !activeUpload
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
    }
    fun checkPermissions(): Boolean {
        permissionsGranted = permissions.all {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        return permissionsGranted
    }
    // Set once a request came back denied without a rationale: the system dialog will not show again.
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        var activity: android.content.Context? = context
        while (activity is android.content.ContextWrapper && activity !is android.app.Activity) activity = activity.baseContext
        val host = activity as? android.app.Activity
        permanentlyDenied = !checkPermissions() && host != null && permissions.any {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
                !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(host, it)
        }
        if (permissionsGranted) viewModel.onPermissionsGranted()
    }
    // System picker for a GIF from the gallery or any other document provider; the panel's own size is used.
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.requestSend(uri)
    }
    val sendingTo = sendNotice?.let { target ->
        val name = target.name ?: stringResource(R.string.backpack_unknown_device)
        target.spec?.let { stringResource(R.string.send_target_sending_sized, name, stringResource(R.string.panel_size, it.width, it.height)) }
            ?: stringResource(R.string.send_target_sending, name)
    }
    LaunchedEffect(sendingTo) {
        if (sendingTo != null) {
            snackbarHost.showSnackbar(sendingTo)
            viewModel.consumeSendNotice()
        }
    }
    (chooser as? SendChooser.Choosing)?.let { choosing ->
        SendTargetSheet(choosing, onPick = viewModel::chooseTarget, onAll = viewModel::chooseAllMyPanels,
            onRetry = viewModel::retryChooser, onDismiss = viewModel::dismissChooser)
    }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val wasGranted = permissionsGranted
                if (checkPermissions() && !wasGranted) viewModel.onPermissionsGranted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        if (!checkPermissions()) launcher.launch(permissions.toTypedArray())
        else viewModel.onPermissionsGranted()
    }
    LaunchedEffect(autoTest, permissionsGranted) {
        if (autoTest && permissionsGranted) viewModel.autoConnectAndUpload()
    }
    LaunchedEffect(panelState?.brightness) {
        panelState?.let { brightness = it.brightness.toFloat().coerceIn(1f, 10f) }
    }
    LaunchedEffect(activeUpload) { if (activeUpload) { showLibraryPicker = false; showSourceSheet = false } }
    if (showSourceSheet && !activeUpload) {
        SendSourceSheet(
            onPhone = { showSourceSheet = false; galleryLauncher.launch(arrayOf("image/gif")) },
            onCollection = { showSourceSheet = false; showLibraryPicker = true },
            onDismiss = { showSourceSheet = false }
        )
    }
    if (showLibraryPicker) {
        val libraryGifs by viewModel.libraryGifs.collectAsStateWithLifecycle()
        BackpackGifPicker(
            gifs = libraryGifs,
            onDismiss = { showLibraryPicker = false },
            onSelect = { gif ->
                showLibraryPicker = false
                viewModel.requestSend(Uri.parse(gif.originalUrl))
            }
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.backpack_clear_title)) },
            text = { Text(stringResource(R.string.backpack_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearPrograms()
                }, enabled = controlsEnabled) {
                    Text(stringResource(R.string.backpack_clear_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.panel_title)) }, navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
            })
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                when {
                    isReady -> stringResource(R.string.backpack_connected)
                    isConnecting -> stringResource(R.string.backpack_connecting)
                    isScanning -> stringResource(R.string.backpack_searching)
                    else -> stringResource(R.string.backpack_not_connected)
                }, style = MaterialTheme.typography.titleMedium
            )
            if (iPixel) IPixelDeviceCard(deviceName, connectedPanel)
            else BackpackDeviceCard(deviceName, advertisement)
            if (!permissionsGranted) {
                Text(stringResource(R.string.backpack_bluetooth_permission), color = MaterialTheme.colorScheme.error)
                val openSettings = {
                    context.startActivity(android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    ))
                }
                if (permanentlyDenied) {
                    Text(stringResource(R.string.backpack_permission_blocked))
                    Button(onClick = openSettings) { Text(stringResource(R.string.backpack_open_permission_settings)) }
                } else {
                    Button(onClick = { launcher.launch(permissions.toTypedArray()) }) { Text(stringResource(R.string.backpack_allow_bluetooth)) }
                    TextButton(onClick = openSettings) { Text(stringResource(R.string.backpack_open_permission_settings)) }
                }
            }
            if (permissionsGranted && !bluetoothEnabled) {
                Text(stringResource(R.string.backpack_bluetooth_off), color = MaterialTheme.colorScheme.error)
                Button(onClick = {
                    context.startActivity(android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }) { Text(stringResource(R.string.backpack_turn_on_bluetooth)) }
            }
            val sendEnabled = permissionsGranted && !busy && !activeUpload && !sendBlocked && chooser == SendChooser.Hidden
            val sendLabel = stringResource(R.string.send_action_a11y)
            Button(
                onClick = { showSourceSheet = true },
                enabled = sendEnabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { contentDescription = sendLabel }
            ) { Text(stringResource(R.string.send_action), style = MaterialTheme.typography.titleMedium) }
            if (chooser is SendChooser.Scanning) SendScanningRow(onCancel = viewModel::dismissChooser)
            if (sendBlocked && isReady) {
                Text(stringResource(R.string.panel_send_unknown_type), color = MaterialTheme.colorScheme.error)
            }
            if (upload.stage != UploadStage.Idle) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            upload.uri?.let { uri ->
                                coil.compose.AsyncImage(
                                    model = coil.request.ImageRequest.Builder(context).data(uri).size(128).build(),
                                    contentDescription = stringResource(R.string.backpack_selected_gif),
                                    modifier = Modifier.size(72.dp),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(upload.name ?: stringResource(R.string.backpack_selected_image), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    when (upload.stage) {
                                        UploadStage.Idle -> ""
                                        UploadStage.Preparing -> stringResource(R.string.backpack_upload_preparing)
                                        UploadStage.Connecting -> stringResource(R.string.backpack_connecting)
                                        UploadStage.Sending -> stringResource(R.string.backpack_upload_progress, (upload.progress.coerceIn(0f, 1f) * 100).toInt())
                                        UploadStage.Finishing -> stringResource(R.string.backpack_upload_finishing)
                                        UploadStage.Success -> stringResource(
                                            if (upload.isSequence) R.string.backpack_sequence_success else R.string.backpack_upload_success)
                                        UploadStage.Error -> stringResource(R.string.backpack_upload_failed)
                                        UploadStage.Cancelled -> stringResource(R.string.backpack_upload_cancelled)
                                    },
                                    color = if (upload.stage == UploadStage.Error) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        upload.multi?.let { MultiPanelStatus(upload.stage, it) }
                        val failure = upload.failure
                        if (failure != null && upload.multi == null) {
                            UploadFailureDetails(failure)
                        } else {
                            upload.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                        if (activeUpload) {
                            if (upload.stage == UploadStage.Sending) {
                                LinearProgressIndicator(progress = upload.progress.coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
                            } else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            OutlinedButton(onClick = viewModel::cancelUpload, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.backpack_cancel_upload))
                            }
                        } else {
                            OutlinedButton(onClick = viewModel::retryUpload,
                                enabled = permissionsGranted && !busy && !sendBlocked, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(if (upload.stage == UploadStage.Success) R.string.backpack_upload_again else R.string.common_retry))
                            }
                        }
                    }
                }
            }
            if (!isReady && !isConnecting) {
                OutlinedButton(onClick = viewModel::connectToTarget,
                    enabled = permissionsGranted && !activeUpload, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.backpack_connect))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = viewModel::startScan, enabled = permissionsGranted && !activeUpload) {
                        Text(stringResource(if (isScanning) R.string.backpack_scanning else R.string.backpack_find_device))
                    }
                    if (isScanning) TextButton(onClick = viewModel::stopScan) { Text(stringResource(R.string.backpack_stop_scan)) }
                }
                if (nearbyPanels.isNotEmpty()) {
                    Text(stringResource(R.string.panel_nearby_title), style = MaterialTheme.typography.titleMedium)
                    // Every tap goes through pickPanel: an iPixel panel is only connected after the user picked it here.
                    nearbyPanels.sortedByDescending { it.rssi }.forEach { panel ->
                        NearbyPanelRow(panel, enabled = permissionsGranted && !activeUpload,
                            onPick = { viewModel.pickPanel(panel.address) })
                    }
                }
            }
            Text(stringResource(R.string.my_panels_title), style = MaterialTheme.typography.titleMedium)
            if (myPanels.isEmpty()) {
                Text(stringResource(R.string.my_panels_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            myPanels.forEach { panel ->
                val inRange = panel.address == connectedAddress || nearbyPanels.any { it.address == panel.address }
                MyPanelRow(panel, inRange, removeEnabled = !activeUpload, onRemove = { viewModel.unpickPanel(panel.address) })
            }
            if (isReady && !iPixel) {
                Divider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.backpack_display), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = viewModel::refreshPanelState, enabled = controlsEnabled) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.backpack_refresh_display))
                    }
                }
                commandError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text(if (panelState == null) stringResource(R.string.backpack_brightness_unknown)
                    else stringResource(R.string.backpack_brightness, kotlin.math.round(brightness).toInt()))
                Slider(value = brightness, onValueChange = { brightness = it }, valueRange = 1f..10f,
                    steps = 8, enabled = controlsEnabled && panelState != null,
                    onValueChangeFinished = { viewModel.setBrightness(kotlin.math.round(brightness).toInt()) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(when {
                        panelState == null -> R.string.backpack_screen_unknown
                        panelState?.screenOn == true -> R.string.backpack_screen_on
                        else -> R.string.backpack_screen_off
                    }), Modifier.weight(1f))
                    Switch(checked = panelState?.screenOn ?: false, onCheckedChange = viewModel::setScreen,
                        enabled = controlsEnabled && panelState != null)
                }
                // Firmware advertises rotation via funCode 0x0100; this backpack (0x0044) does not.
                if (advertisement?.supportsRotation == true) {
                    Text(stringResource(R.string.backpack_rotation), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("0°", "90°", "180°", "270°").forEachIndexed { index, label ->
                            FilterChip(
                                selected = panelState?.rotationIndex == index,
                                onClick = { viewModel.setRotation(index, panelState?.mirror ?: false) },
                                label = { Text(label) },
                                enabled = controlsEnabled && panelState != null
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.backpack_mirror), Modifier.weight(1f))
                        Switch(checked = panelState?.mirror ?: false,
                            onCheckedChange = { viewModel.setRotation(panelState?.rotationIndex ?: 0, it) },
                            enabled = controlsEnabled && panelState != null)
                    }
                }
                // Cmd 0D reports the firmware's built-in programmes; this backpack answers 0, so it stays hidden.
                val count = builtInCount ?: 0
                if (count > 0) {
                    Divider()
                    BuiltInProgramsSection(count, controlsEnabled, viewModel::playBuiltIn)
                }
            }
            if (isReady && iPixel) {
                Divider()
                IPixelSettingsSection(ipixelSettings, commandError, controlsEnabled, viewModel)
            }
            if (isReady || isConnecting) {
                TextButton(onClick = viewModel::disconnect, enabled = !activeUpload) { Text(stringResource(R.string.backpack_disconnect)) }
            }
            if (isReady) {
                TextButton(onClick = viewModel::switchPanel, enabled = !activeUpload) { Text(stringResource(R.string.panel_switch)) }
            }
            Divider()
            UploadHistorySection(uploadHistory, onClear = viewModel::clearUploadHistory)
            Divider()
            // Developer tools (diagnostics, upload experiments, tests) live only under Advanced.
            TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (showAdvanced) R.string.backpack_hide_advanced else R.string.backpack_show_advanced))
            }
            if (showAdvanced) {
            if (isReady && !iPixel) {
                OutlinedButton(onClick = { confirmClear = true }, enabled = controlsEnabled,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.backpack_clear_confirm), color = MaterialTheme.colorScheme.error)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showBleLog = !showBleLog }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (showBleLog) R.string.backpack_hide_diagnostics else R.string.backpack_show_diagnostics))
                }
                if (showBleLog) TextButton(onClick = {
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(bleLog.joinToString("\n")))
                }) { Text(stringResource(R.string.common_copy)) }
            }
            if (showBleLog) {
                Text(stringResource(R.string.backpack_connection_status, status), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = viewModel::sendTestImage, enabled = controlsEnabled,
                        modifier = Modifier.weight(1f)) { Text(stringResource(R.string.backpack_test_upload)) }
                    OutlinedButton(onClick = viewModel::dumpBackpackInfo, enabled = controlsEnabled,
                        modifier = Modifier.weight(1f)) { Text(stringResource(R.string.backpack_info_action)) }
                }
                if (!iPixel) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = viewModel::queryPasswordStatus, enabled = controlsEnabled,
                            modifier = Modifier.weight(1f)) { Text(stringResource(R.string.backpack_password_status_action)) }
                        OutlinedButton(onClick = viewModel::sendPlaylistTest, enabled = controlsEnabled,
                            modifier = Modifier.weight(1f)) { Text(stringResource(R.string.backpack_playlist_test_action)) }
                    }
                }
                Text(stringResource(R.string.backpack_window_experiment), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(1, 2, 4, 8).forEach { w ->
                        FilterChip(selected = uploadWindow == w, onClick = { viewModel.setUploadWindow(w) },
                            label = { Text(w.toString()) }, enabled = controlsEnabled)
                    }
                }
                if (!iPixel) {
                    Text(stringResource(R.string.backpack_effect_test_title), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(0, 1, 2, 3, 5, 6, 7).forEach { code ->
                            OutlinedButton(onClick = { viewModel.sendEffectTest(code) }, enabled = controlsEnabled,
                                contentPadding = PaddingValues(0.dp), modifier = Modifier.weight(1f)) { Text(code.toString()) }
                        }
                    }
                }
                Card(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                    val scrollState = rememberScrollState()
                    LaunchedEffect(bleLog.size) {
                        if (scrollState.value >= scrollState.maxValue - 150) scrollState.animateScrollTo(scrollState.maxValue)
                    }
                    Text(text = if (bleLog.isEmpty()) stringResource(R.string.backpack_no_diagnostics) else bleLog.joinToString("\n"),
                        modifier = Modifier.padding(8.dp).verticalScroll(scrollState),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
            }
        }
    }
}

/** Compact capabilities card: everything the backpack tells about itself lives in its advertisement. */
@Composable
private fun BackpackDeviceCard(name: String?, adv: BackpackAdvertisement?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name ?: stringResource(R.string.backpack_device_title), style = MaterialTheme.typography.titleSmall)
            // No early return here: returning from an inline composable lambda (Column) corrupts
            // the Compose slot table and crashed fresh installs, where no advertisement is cached yet.
            if (adv == null) {
                Text(stringResource(R.string.backpack_device_unknown), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    listOf(
                        stringResource(R.string.backpack_device_firmware, adv.versionCode),
                        stringResource(R.string.backpack_device_panel, adv.width, adv.height),
                        stringResource(R.string.backpack_device_colors, colorTypeLabel(adv.colorType)),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium
                )
                val features = buildList {
                    if (adv.supportsGif) add(R.string.backpack_feature_gif)
                    if (adv.supportsGifText) add(R.string.backpack_feature_gif_text)
                    if (adv.supportsBrightness) add(R.string.backpack_feature_brightness)
                    if (adv.supportsTime) add(R.string.backpack_feature_time)
                    if (adv.supportsPassword) add(R.string.backpack_feature_password)
                    if (adv.supportsRotation) add(R.string.backpack_feature_rotation)
                    if (adv.supportsPartition) add(R.string.backpack_feature_partition)
                    if (adv.supportsBorder) add(R.string.backpack_feature_border)
                }.map { stringResource(it) }
                Text(
                    if (features.isEmpty()) stringResource(R.string.backpack_device_features_none)
                    else stringResource(R.string.backpack_device_features, features.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** iPixel brightness, display, rotation and clock; the panel cannot report its state, so the last set values are shown. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun IPixelSettingsSection(settings: IPixelSettings, commandError: String?, enabled: Boolean, viewModel: BackpackViewModel) {
    var brightness by remember(settings.brightness) { mutableFloatStateOf(settings.brightness.toFloat()) }
    var clockStyle by rememberSaveable(settings.clockStyle) { mutableIntStateOf(settings.clockStyle) }
    var use24h by rememberSaveable(settings.use24h) { mutableStateOf(settings.use24h) }
    var showDate by rememberSaveable(settings.showDate) { mutableStateOf(settings.showDate) }
    Text(stringResource(R.string.backpack_display), style = MaterialTheme.typography.titleMedium)
    commandError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Text(stringResource(R.string.ipixel_brightness, kotlin.math.round(brightness).toInt()))
    Slider(value = brightness, onValueChange = { brightness = it }, valueRange = 0f..100f, enabled = enabled,
        onValueChangeFinished = { viewModel.ipixelBrightness(kotlin.math.round(brightness).toInt()) })
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(if (settings.screenOn) R.string.backpack_screen_on else R.string.backpack_screen_off), Modifier.weight(1f))
        Switch(checked = settings.screenOn, onCheckedChange = viewModel::ipixelScreen, enabled = enabled)
    }
    Text(stringResource(R.string.backpack_rotation), style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("0°", "90°", "180°", "270°").forEachIndexed { index, label ->
            FilterChip(selected = settings.quarterTurns == index, onClick = { viewModel.ipixelRotation(index) },
                label = { Text(label) }, enabled = enabled)
        }
    }
    Text(stringResource(R.string.ipixel_clock), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.ipixel_clock_style), style = MaterialTheme.typography.bodyMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (0..8).forEach { style ->
            FilterChip(selected = clockStyle == style, onClick = { clockStyle = style },
                label = { Text("$style") }, enabled = enabled)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.ipixel_clock_24h), Modifier.weight(1f))
        Switch(checked = use24h, onCheckedChange = { use24h = it }, enabled = enabled)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.ipixel_clock_date), Modifier.weight(1f))
        Switch(checked = showDate, onCheckedChange = { showDate = it }, enabled = enabled)
    }
    OutlinedButton(onClick = { viewModel.ipixelClock(clockStyle, use24h, showDate) }, enabled = enabled,
        modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.ipixel_clock_show))
    }
}

/** Device card for a connected iPixel panel: family, size and whether that size was tested on hardware. */
@Composable
private fun IPixelDeviceCard(name: String?, spec: PanelSpec?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name ?: stringResource(R.string.backpack_device_title), style = MaterialTheme.typography.titleSmall)
            Text(
                listOf(stringResource(R.string.panel_family_ipixel), spec?.let { panelSizeText(it) } ?: stringResource(R.string.panel_unknown_type))
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium
            )
            if (spec != null) {
                Text(stringResource(if (spec.verified) R.string.panel_verified_size else R.string.panel_untested_size),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun panelSizeText(spec: PanelSpec): String = stringResource(R.string.panel_size, spec.width, spec.height)

/** One panel seen in a scan: name, family, size (or unknown type), untested-size note and signal. */
@Composable
private fun NearbyPanelRow(panel: NearbyPanel, enabled: Boolean, onPick: () -> Unit) {
    val spec = panel.spec
    val family = stringResource(
        if (spec?.family == PanelFamily.Backpack) R.string.panel_family_backpack else R.string.panel_family_ipixel)
    val details = listOfNotNull(
        family,
        spec?.let { panelSizeText(it) } ?: stringResource(R.string.panel_unknown_type),
        stringResource(R.string.panel_untested_size).takeIf { spec != null && !spec.verified },
        stringResource(R.string.panel_signal, panel.rssi)
    ).joinToString(" · ")
    ListItem(
        headlineContent = { Text(panel.name ?: stringResource(R.string.backpack_unknown_device)) },
        supportingContent = { Text(details) },
        modifier = Modifier.clickable(enabled = enabled, onClick = onPick)
    )
}

/** One of my panels: name, family, size and whether it is in range; removing it only clears the picked flag. */
@Composable
private fun MyPanelRow(panel: MyPanel, inRange: Boolean, removeEnabled: Boolean, onRemove: () -> Unit) {
    val spec = panel.spec
    val details = listOfNotNull(
        spec?.let { stringResource(if (it.family == PanelFamily.Backpack) R.string.panel_family_backpack else R.string.panel_family_ipixel) },
        spec?.let { panelSizeText(it) } ?: stringResource(R.string.panel_unknown_type),
        stringResource(if (inRange) R.string.my_panels_in_range else R.string.my_panels_not_in_range)
    ).joinToString(" · ")
    ListItem(
        headlineContent = { Text(panel.name ?: stringResource(R.string.backpack_unknown_device)) },
        supportingContent = { Text(details) },
        trailingContent = {
            // An icon keeps the name and details readable; the full action text stays as the description.
            IconButton(onClick = onRemove, enabled = removeEnabled) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.my_panels_remove))
            }
        }
    )
}

/** Progress and per-panel results of a "send to all my panels" run; shared by the panel and text banner screens. */
@Composable
internal fun MultiPanelStatus(stage: UploadStage, multi: MultiPanelProgress) {
    val active = stage in setOf(UploadStage.Preparing, UploadStage.Connecting, UploadStage.Sending, UploadStage.Finishing)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (active) {
            Text(
                if (multi.index == 0) stringResource(R.string.multi_send_scanning)
                else stringResource(R.string.multi_send_progress, multi.index, multi.total,
                    multi.panelName ?: stringResource(R.string.backpack_unknown_device)),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        multi.results.forEach { result ->
            val outcome = result.outcome
            val (symbol, label) = when (outcome) {
                PanelSendOutcome.Sent -> "✓" to stringResource(R.string.multi_send_sent)
                PanelSendOutcome.AlreadyPresent -> "✓" to stringResource(R.string.backpack_history_already_present)
                is PanelSendOutcome.Failed -> outcome.failure.text().let { "✕" to stringResource(it.title, *it.args.toTypedArray()) }
                PanelSendOutcome.NotInRange -> "✕" to stringResource(R.string.multi_send_not_in_range)
                PanelSendOutcome.Skipped -> "–" to stringResource(R.string.multi_send_skipped)
            }
            Text("$symbol ${result.panel.name ?: stringResource(R.string.backpack_unknown_device)}: $label",
                style = MaterialTheme.typography.bodySmall,
                color = if (outcome.succeeded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
        }
    }
}

/** Pixel format names per `GraffitiBean.getSendData` (re_iledcolor/03_command_catalog.md §12.2). */
private fun colorTypeLabel(type: Int): String = when (type) {
    0, 1 -> "1 bit"
    2 -> "RGB 3 bit"
    3 -> "RGB888"
    4 -> "RGB565"
    5, 6, 7 -> "RGB332"
    else -> "#$type"
}

@Composable
private fun BuiltInProgramsSection(count: Int, enabled: Boolean, onPlay: (Int) -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(1) }
    LaunchedEffect(count) { selected = selected.coerceIn(1, count) }
    Text(stringResource(R.string.backpack_builtin_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.backpack_builtin_hint, count), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val previous = stringResource(R.string.backpack_builtin_previous)
        val next = stringResource(R.string.backpack_builtin_next)
        OutlinedButton(onClick = { selected = if (selected > 1) selected - 1 else count }, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = previous }) {
            Text("\u2212")
        }
        Text(stringResource(R.string.backpack_builtin_selected, selected, count), Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = { selected = if (selected < count) selected + 1 else 1 }, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = next }) {
            Text("+")
        }
    }
    Button(onClick = { onPlay(selected) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.backpack_builtin_play))
    }
}

@Composable
private fun UploadHistorySection(history: List<UploadHistoryEntry>, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.backpack_history_title), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f))
        if (history.isNotEmpty()) TextButton(onClick = onClear) { Text(stringResource(R.string.backpack_history_clear)) }
    }
    if (history.isEmpty()) {
        Text(stringResource(R.string.backpack_history_empty), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        UploadHistoryList(history)
    }
}

/** Typed failure reason with its "what to do" hint, shown on the upload card. */
@Composable
private fun UploadFailureDetails(failure: UploadFailure) {
    val text = failure.text()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(text.title, *text.args.toTypedArray()), style = MaterialTheme.typography.bodyMedium,
            color = if (failure == UploadFailure.Cancelled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.upload_hint_label, stringResource(text.hint)), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UploadHistoryList(history: List<UploadHistoryEntry>) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val format = remember(locale) {
        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT, locale)
    }
    history.forEach { entry ->
        val confirmed = entry.outcome == UploadOutcome.Confirmed || entry.outcome == UploadOutcome.AlreadyPresent
        val (symbol, outcomeLabel, color) = when (entry.outcome) {
            UploadOutcome.Confirmed -> Triple("✓", stringResource(R.string.backpack_history_confirmed), MaterialTheme.colorScheme.primary)
            UploadOutcome.AlreadyPresent -> Triple("✓", stringResource(R.string.backpack_history_already_present), MaterialTheme.colorScheme.primary)
            UploadOutcome.Failed -> Triple("✕", stringResource(R.string.backpack_history_failed), MaterialTheme.colorScheme.error)
            UploadOutcome.Cancelled -> Triple("–", stringResource(R.string.backpack_history_cancelled), MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Entries written before failure reasons existed keep the generic outcome label.
        val reason = entry.failure?.takeIf { entry.outcome == UploadOutcome.Failed }?.text()
        val label = if (reason != null) stringResource(reason.title, *reason.args.toTypedArray()) else outcomeLabel
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(symbol, color = color, style = MaterialTheme.typography.titleMedium)
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text("$label · ${format.format(java.util.Date(entry.timeMillis))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (confirmed) MaterialTheme.colorScheme.onSurfaceVariant else color)
                if (reason != null) {
                    Text(stringResource(R.string.upload_hint_label, stringResource(reason.hint)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
