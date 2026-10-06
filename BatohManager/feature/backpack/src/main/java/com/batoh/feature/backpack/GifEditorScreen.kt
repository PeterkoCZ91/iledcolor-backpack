package com.batoh.feature.backpack

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.batoh.core.conversion.GifScaleMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GifEditorScreen(
    gifUri: String,
    onBack: () -> Unit,
    onSendToBackpack: (String) -> Unit,
    viewModel: GifEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sendCallback by rememberUpdatedState(onSendToBackpack)
    LaunchedEffect(gifUri) { viewModel.load(gifUri) }
    LaunchedEffect(viewModel) { viewModel.sendEvents.collect { sendCallback(it) } }
    val processing = state.stage == GifEditorStage.Loading || state.stage == GifEditorStage.Rendering
    val controlsEnabled = !state.saving && state.stage != GifEditorStage.Loading
    val preview = remember(state.previewBytes, context) {
        state.previewBytes?.let { bytes ->
            ImageRequest.Builder(context).data(bytes)
                .decoderFactory(
                    if (Build.VERSION.SDK_INT >= 28) ImageDecoderDecoder.Factory(enforceMinimumFrameDelay = false)
                    else GifDecoder.Factory(enforceMinimumFrameDelay = false)
                ).build()
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.editor_title)) }, navigationIcon = {
            TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
        })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.editor_preview_title), style = MaterialTheme.typography.titleMedium)
            Box(Modifier.fillMaxWidth().heightIn(max = 320.dp).aspectRatio(1f).background(Color.Black)) {
                if (preview != null) {
                    AsyncImage(model = preview, contentDescription = stringResource(R.string.editor_preview_description),
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
                }
            }
            state.info?.let { info ->
                Text(stringResource(
                    R.string.editor_frame_info,
                    info.frameCount,
                    info.durationMs / 1000.0,
                    when (val loopCount = info.loopCount) {
                        null -> stringResource(R.string.editor_loop_once)
                        0 -> stringResource(R.string.editor_loop_forever)
                        else -> stringResource(R.string.editor_loop_count, loopCount)
                    }
                ),
                    style = MaterialTheme.typography.bodySmall)
            }
            if (processing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(if (state.stage == GifEditorStage.Loading) R.string.editor_loading else R.string.editor_processing))
                OutlinedButton(onClick = viewModel::cancel) { Text(stringResource(R.string.editor_cancel)) }
            }
            if (state.stage == GifEditorStage.Cancelled) Text(stringResource(R.string.editor_cancelled))
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.stage == GifEditorStage.Error || state.stage == GifEditorStage.Cancelled) {
                Button(onClick = viewModel::retry) { Text(stringResource(R.string.editor_retry)) }
            }
            Text(stringResource(R.string.editor_rotation), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (angle in listOf(0, 90, 180, 270)) {
                    FilterChip(selected = state.options.rotationDegrees == angle,
                        enabled = controlsEnabled,
                        onClick = { viewModel.edit(state.options.copy(rotationDegrees = angle)) }, label = { Text("$angle°") })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.editor_mirror_horizontal))
                    Text(stringResource(R.string.editor_mirror_horizontal_hint), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = state.options.mirrorHorizontal, enabled = controlsEnabled,
                    onCheckedChange = { viewModel.edit(state.options.copy(mirrorHorizontal = it)) })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.editor_mirror_vertical))
                    Text(stringResource(R.string.editor_mirror_vertical_hint), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = state.options.mirrorVertical, enabled = controlsEnabled,
                    onCheckedChange = { viewModel.edit(state.options.copy(mirrorVertical = it)) })
            }
            Text(stringResource(R.string.editor_placement), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.options.scaleMode == GifScaleMode.Fit, enabled = controlsEnabled,
                    onClick = { viewModel.edit(state.options.copy(scaleMode = GifScaleMode.Fit)) }, label = { Text(stringResource(R.string.editor_fit)) })
                FilterChip(selected = state.options.scaleMode == GifScaleMode.CenterCrop, enabled = controlsEnabled,
                    onClick = { viewModel.edit(state.options.copy(scaleMode = GifScaleMode.CenterCrop)) }, label = { Text(stringResource(R.string.editor_crop)) })
            }
            Text(stringResource(if (state.options.scaleMode == GifScaleMode.Fit) R.string.editor_fit_hint else R.string.editor_crop_hint), style = MaterialTheme.typography.bodySmall)
            EditorPlaybackSection(
                playback = state.playback,
                enabled = !state.saving,
                onChange = viewModel::setPlayback
            )
            Text(stringResource(R.string.editor_copy_hint), style = MaterialTheme.typography.bodySmall)
            if (state.savedUri != null) Text(stringResource(R.string.editor_saved), color = MaterialTheme.colorScheme.primary)
            val outputReady = state.stage == GifEditorStage.Ready && !state.saving
            OutlinedButton(onClick = { viewModel.saveCopy() }, enabled = outputReady && state.savedUri == null,
                modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (state.saving) R.string.editor_saving else R.string.editor_save_copy)) }
            Button(onClick = { viewModel.saveCopy(sendToBackpack = true) }, enabled = outputReady,
                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.editor_send)) }
        }
    }
}

@Composable
private fun EditorPlaybackSection(
    playback: EditorPlaybackOptions,
    enabled: Boolean,
    onChange: (EditorPlaybackOptions) -> Unit
) {
    // Local slider positions so dragging does not round-trip through the ViewModel every frame.
    var speed by remember(playback.speed) { mutableFloatStateOf(playback.speed.toFloat()) }
    var light by remember(playback.light) { mutableFloatStateOf(playback.light.toFloat()) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.editor_playback_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.editor_playback_experimental_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.editor_playback_speed, EditorPlaybackOptions.coerce(speed), EditorPlaybackOptions.DEFAULT_SPEED))
        Slider(value = speed, onValueChange = { speed = it }, valueRange = 0f..255f, enabled = enabled,
            onValueChangeFinished = { onChange(playback.copy(speed = EditorPlaybackOptions.coerce(speed))) })
        Text(stringResource(R.string.editor_playback_light, EditorPlaybackOptions.coerce(light), EditorPlaybackOptions.DEFAULT_LIGHT))
        Slider(value = light, onValueChange = { light = it }, valueRange = 0f..255f, enabled = enabled,
            onValueChangeFinished = { onChange(playback.copy(light = EditorPlaybackOptions.coerce(light))) })
        if (!playback.isDefault) {
            TextButton(onClick = { onChange(EditorPlaybackOptions()) }, enabled = enabled) {
                Text(stringResource(R.string.editor_playback_reset))
            }
        }
    }
}
