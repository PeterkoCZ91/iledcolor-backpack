package com.batoh.feature.backpack

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.batoh.core.conversion.TextBannerLayout
import com.batoh.core.conversion.TextBannerSize
import com.batoh.core.conversion.TextBannerSpeed

private data class BannerColor(val argb: Int, @StringRes val label: Int)

private val bannerColors = listOf(
    BannerColor(0xFFFFFFFF.toInt(), R.string.text_banner_color_white),
    BannerColor(0xFFFF0000.toInt(), R.string.text_banner_color_red),
    BannerColor(0xFFFF8000.toInt(), R.string.text_banner_color_orange),
    BannerColor(0xFFFFFF00.toInt(), R.string.text_banner_color_yellow),
    BannerColor(0xFF00FF00.toInt(), R.string.text_banner_color_green),
    BannerColor(0xFF00FFFF.toInt(), R.string.text_banner_color_cyan),
    BannerColor(0xFF0000FF.toInt(), R.string.text_banner_color_blue),
    BannerColor(0xFFFF00FF.toInt(), R.string.text_banner_color_magenta),
    BannerColor(0xFF000000.toInt(), R.string.text_banner_color_black)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TextBannerScreen(
    onBack: () -> Unit,
    onOpenBackpack: () -> Unit,
    viewModel: TextBannerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val upload by viewModel.uploadState.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val options = state.options
    val activeUpload = upload.stage in setOf(
        UploadStage.Preparing, UploadStage.Connecting, UploadStage.Sending, UploadStage.Finishing
    )
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
        }
    }
    fun hasPermissions() = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    var permissionDenied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasPermissions()) {
            permissionDenied = false
            viewModel.sendToBackpack()
        } else permissionDenied = true
    }
    val preview = remember(state.previewBytes, context) {
        state.previewBytes?.let { bytes ->
            ImageRequest.Builder(context).data(bytes)
                .decoderFactory(
                    if (Build.VERSION.SDK_INT >= 28) ImageDecoderDecoder.Factory(enforceMinimumFrameDelay = false)
                    else GifDecoder.Factory(enforceMinimumFrameDelay = false)
                ).build()
        }
    }
    val outputReady = state.stage == TextBannerStage.Ready && !state.saving

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.text_banner_title)) }, navigationIcon = {
            TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
        })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = options.text,
                onValueChange = { viewModel.update(options.copy(text = it.replace('\n', ' '))) },
                label = { Text(stringResource(R.string.text_banner_input_label)) },
                supportingText = {
                    Text(stringResource(R.string.text_banner_input_hint, options.text.length, TextBannerLayout.MAX_TEXT_LENGTH))
                },
                singleLine = true,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth()
            )

            Text(stringResource(R.string.text_banner_preview_title), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            Box(
                Modifier.fillMaxWidth().heightIn(max = 320.dp).aspectRatio(1f)
                    .background(Color(options.backgroundColor)),
                contentAlignment = Alignment.Center
            ) {
                if (preview != null) {
                    AsyncImage(model = preview, contentDescription = stringResource(R.string.text_banner_preview_description),
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
                } else if (state.stage == TextBannerStage.Rendering) {
                    CircularProgressIndicator()
                }
            }
            when (state.stage) {
                TextBannerStage.Empty -> Text(stringResource(R.string.text_banner_empty), style = MaterialTheme.typography.bodySmall)
                TextBannerStage.Rendering -> Text(stringResource(R.string.text_banner_rendering), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                TextBannerStage.Ready -> state.plan?.let { plan ->
                    Text(stringResource(R.string.text_banner_info, plan.frameCount, plan.durationMs / 1000.0,
                        ((state.previewBytes?.size ?: 0) + 1023) / 1024), style = MaterialTheme.typography.bodySmall)
                }
                TextBannerStage.Error -> Button(onClick = viewModel::retry) { Text(stringResource(R.string.common_retry)) }
            }
            state.errorRes?.let { res ->
                val detail = state.errorDetail
                // Announced; the retry button above is the recovery action.
                Text(if (detail != null) stringResource(R.string.text_banner_error_with_detail, stringResource(res), detail)
                    else stringResource(res), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
            }

            Text(stringResource(R.string.text_banner_text_color), style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() })
            ColorRow(selected = options.textColor, enabled = !state.saving) { viewModel.update(options.copy(textColor = it)) }
            Text(stringResource(R.string.text_banner_background_color), style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() })
            ColorRow(selected = options.backgroundColor, enabled = !state.saving) { viewModel.update(options.copy(backgroundColor = it)) }
            if (options.textColor == options.backgroundColor) {
                Text(stringResource(R.string.text_banner_same_colors), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }

            Text(stringResource(R.string.text_banner_speed), style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() })
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((speed, label) in listOf(
                    TextBannerSpeed.Slow to R.string.text_banner_speed_slow,
                    TextBannerSpeed.Normal to R.string.text_banner_speed_normal,
                    TextBannerSpeed.Fast to R.string.text_banner_speed_fast
                )) {
                    FilterChip(selected = options.speed == speed, enabled = !state.saving,
                        onClick = { viewModel.update(options.copy(speed = speed)) }, label = { Text(stringResource(label)) })
                }
            }
            Text(stringResource(R.string.text_banner_size), style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() })
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((size, label) in listOf(
                    TextBannerSize.Small to R.string.text_banner_size_small,
                    TextBannerSize.Medium to R.string.text_banner_size_medium,
                    TextBannerSize.Large to R.string.text_banner_size_large
                )) {
                    FilterChip(selected = options.size == size, enabled = !state.saving,
                        onClick = { viewModel.update(options.copy(size = size)) }, label = { Text(stringResource(label)) })
                }
            }
            // Whole row toggles, so the label is read with the switch and the target is full width.
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                    value = options.bold,
                    enabled = !state.saving,
                    role = Role.Switch,
                    onValueChange = { viewModel.update(options.copy(bold = it)) }
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.text_banner_bold), Modifier.weight(1f))
                Switch(checked = options.bold, enabled = !state.saving, onCheckedChange = null)
            }

            if (state.savedUri != null) {
                Text(stringResource(R.string.text_banner_saved), color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            OutlinedButton(onClick = viewModel::save, enabled = outputReady && state.savedUri == null,
                modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.saving) R.string.text_banner_saving else R.string.text_banner_save))
            }
            Button(
                onClick = {
                    if (hasPermissions()) viewModel.sendToBackpack()
                    else launcher.launch(permissions.toTypedArray())
                },
                enabled = outputReady && !busy && !activeUpload,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.text_banner_send)) }
            if (permissionDenied) {
                Text(stringResource(R.string.text_banner_bluetooth_permission), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                // Recovery when the system no longer shows the permission dialog.
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text(stringResource(R.string.text_banner_open_settings)) }
            }
            if (state.uploadRequested && upload.stage != UploadStage.Idle) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            when (upload.stage) {
                                UploadStage.Idle -> ""
                                UploadStage.Preparing -> stringResource(R.string.backpack_upload_preparing)
                                UploadStage.Connecting -> stringResource(R.string.backpack_connecting)
                                UploadStage.Sending -> stringResource(R.string.backpack_upload_progress, (upload.progress.coerceIn(0f, 1f) * 100).toInt())
                                UploadStage.Finishing -> stringResource(R.string.backpack_upload_finishing)
                                UploadStage.Success -> stringResource(R.string.backpack_upload_success)
                                UploadStage.Error -> stringResource(R.string.backpack_upload_failed)
                                UploadStage.Cancelled -> stringResource(R.string.backpack_upload_cancelled)
                            },
                            color = if (upload.stage == UploadStage.Error) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            // Announce stage changes, but not every percent while sending.
                            modifier = Modifier.semantics {
                                if (upload.stage != UploadStage.Sending) liveRegion = LiveRegionMode.Polite
                            }
                        )
                        upload.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        if (activeUpload) {
                            if (upload.stage == UploadStage.Sending) {
                                LinearProgressIndicator(progress = { upload.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            } else LinearProgressIndicator(Modifier.fillMaxWidth())
                            OutlinedButton(onClick = viewModel::cancelUpload, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.backpack_cancel_upload))
                            }
                        }
                    }
                }
            }
            TextButton(onClick = onOpenBackpack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.text_banner_open_backpack))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorRow(selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    val selectedState = stringResource(R.string.text_banner_color_selected)
    val notSelectedState = stringResource(R.string.text_banner_color_not_selected)
    // Each swatch keeps its 40 dp look inside a 48 dp touch target; the former 8 dp gaps
    // are now part of the targets, so the visual spacing stays the same.
    FlowRow(Modifier.selectableGroup()) {
        for (color in bannerColors) {
            val label = stringResource(color.label)
            val isSelected = color.argb == selected
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton) { onSelect(color.argb) }
                    .semantics {
                        contentDescription = label
                        stateDescription = if (isSelected) selectedState else notSelectedState
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color(color.argb))
                        .border(if (isSelected) 3.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                )
            }
        }
    }
}
