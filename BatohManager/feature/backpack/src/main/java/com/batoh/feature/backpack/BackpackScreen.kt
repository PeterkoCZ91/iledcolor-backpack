package com.batoh.feature.backpack

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.batoh.core.data.bluetooth.UploadFailure
import com.batoh.core.data.bluetooth.UploadHistoryEntry
import com.batoh.core.data.bluetooth.UploadOutcome
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class BackpackViewModel @Inject constructor(
    application: Application,
    private val transfers: BackpackTransferManager,
    getLocalGifs: GetLocalGifsUseCase,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    val connectionStatus = transfers.connectionStatus
    val scannedDevices = transfers.scannedDevices
    val bleLog = transfers.bleLog
    val libraryGifs = getLocalGifs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Result.Loading)
    val uploadState = transfers.uploadState
    val uploadProgress = transfers.uploadProgress
    val uploadError = transfers.uploadError
    val commandError = transfers.commandError
    val busy = transfers.busy
    val panelState = transfers.panelState
    val advertisement = transfers.advertisement
    val deviceName = transfers.deviceName
    val builtInCount = transfers.builtInCount
    val uploadHistory = transfers.uploadHistory
    private var pendingGifUri = savedStateHandle.get<String>(Screen.Backpack.ARG_GIF_URI)

    fun onPermissionsGranted(): Boolean {
        if (transfers.startStagedSequence()) return true
        val uri = pendingGifUri ?: return false
        pendingGifUri = null
        savedStateHandle.remove<String>(Screen.Backpack.ARG_GIF_URI)
        transfers.uploadGif(Uri.parse(uri))
        return true
    }
    fun startScan() = transfers.startScan()
    fun stopScan() = transfers.stopScan()
    fun connect(device: BluetoothDevice) = transfers.connect(device)
    fun connectToTarget() = transfers.connectToTarget()
    fun disconnect() = transfers.disconnect()
    fun uploadGif(uri: Uri) = transfers.uploadGif(uri)
    fun uploadPayload(payload: ByteArray) = transfers.uploadPayload(payload)
    fun sendTestImage() = transfers.sendTestImage()
    fun dumpBackpackInfo() = transfers.dumpBackpackInfo()
    fun queryPasswordStatus() = transfers.queryPasswordStatus()
    fun sendEffectTest(effect: Int) = transfers.sendEffectTest(effect)
    fun sendPlaylistTest() = transfers.sendPlaylistTest()
    fun autoConnectAndUpload() = transfers.autoConnectAndUpload()
    fun setBrightness(level: Int) = transfers.setBrightness(level)
    fun setScreen(on: Boolean) = transfers.setScreen(on)
    fun setRotation(index: Int, mirror: Boolean) = transfers.setRotation(index, mirror)
    fun clearPrograms() = transfers.clearPrograms()
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
    val scannedDevices by viewModel.scannedDevices.collectAsStateWithLifecycle()
    val bleLog by viewModel.bleLog.collectAsStateWithLifecycle()
    val upload by viewModel.uploadState.collectAsStateWithLifecycle()
    val commandError by viewModel.commandError.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val panelState by viewModel.panelState.collectAsStateWithLifecycle()
    val advertisement by viewModel.advertisement.collectAsStateWithLifecycle()
    val deviceName by viewModel.deviceName.collectAsStateWithLifecycle()
    val builtInCount by viewModel.builtInCount.collectAsStateWithLifecycle()
    val uploadHistory by viewModel.uploadHistory.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var permissionsGranted by remember { mutableStateOf(false) }
    var showLibraryPicker by remember { mutableStateOf(false) }
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
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (checkPermissions()) viewModel.onPermissionsGranted()
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
    LaunchedEffect(activeUpload) { if (activeUpload) showLibraryPicker = false }
    if (showLibraryPicker) {
        val libraryGifs by viewModel.libraryGifs.collectAsStateWithLifecycle()
        BackpackGifPicker(
            gifs = libraryGifs,
            onDismiss = { showLibraryPicker = false },
            onSelect = { gif ->
                showLibraryPicker = false
                viewModel.uploadGif(Uri.parse(gif.originalUrl))
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
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.backpack_title)) }, navigationIcon = {
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
            BackpackDeviceCard(deviceName, advertisement)
            if (!permissionsGranted) {
                Text(stringResource(R.string.backpack_bluetooth_permission), color = MaterialTheme.colorScheme.error)
                Button(onClick = { launcher.launch(permissions.toTypedArray()) }) { Text(stringResource(R.string.backpack_allow_bluetooth)) }
                TextButton(onClick = {
                    context.startActivity(android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    ))
                }) { Text(stringResource(R.string.backpack_open_permission_settings)) }
            }
            Button(
                onClick = { showLibraryPicker = true },
                enabled = permissionsGranted && !busy && !activeUpload,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.backpack_select_gif)) }
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
                        val failure = upload.failure
                        if (failure != null) {
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
                                enabled = permissionsGranted && !busy, modifier = Modifier.fillMaxWidth()) {
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
                scannedDevices.forEach { device ->
                    ListItem(headlineContent = { Text(device.name ?: stringResource(R.string.backpack_unknown_device)) },
                        supportingContent = { Text(device.address) },
                        modifier = Modifier.clickable(enabled = permissionsGranted && !activeUpload) {
                            viewModel.connect(device)
                        })
                }
            }
            if (isReady) {
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
            if (isReady || isConnecting) {
                TextButton(onClick = viewModel::disconnect, enabled = !activeUpload) { Text(stringResource(R.string.backpack_disconnect)) }
            }
            Divider()
            UploadHistorySection(uploadHistory, onClear = viewModel::clearUploadHistory)
            Divider()
            if (isReady) {
                TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (showAdvanced) R.string.backpack_hide_advanced else R.string.backpack_show_advanced))
                }
                if (showAdvanced) {
                    OutlinedButton(onClick = { confirmClear = true }, enabled = controlsEnabled,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.backpack_clear_confirm), color = MaterialTheme.colorScheme.error)
                    }
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = viewModel::queryPasswordStatus, enabled = controlsEnabled,
                        modifier = Modifier.weight(1f)) { Text(stringResource(R.string.backpack_password_status_action)) }
                    OutlinedButton(onClick = viewModel::sendPlaylistTest, enabled = controlsEnabled,
                        modifier = Modifier.weight(1f)) { Text(stringResource(R.string.backpack_playlist_test_action)) }
                }
                Text(stringResource(R.string.backpack_effect_test_title), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(0, 1, 2, 3, 5, 6, 7).forEach { code ->
                        OutlinedButton(onClick = { viewModel.sendEffectTest(code) }, enabled = controlsEnabled,
                            contentPadding = PaddingValues(0.dp), modifier = Modifier.weight(1f)) { Text(code.toString()) }
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
            Text("−")
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
