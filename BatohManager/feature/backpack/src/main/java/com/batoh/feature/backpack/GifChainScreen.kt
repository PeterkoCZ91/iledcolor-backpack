package com.batoh.feature.backpack

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.batoh.core.conversion.GifScaleMode

@Composable
private fun GifChainError.message(): String = when (this) {
    GifChainError.Empty -> stringResource(R.string.chain_error_empty)
    is GifChainError.SourceUnreadable -> stringResource(R.string.chain_error_source_unreadable, index + 1)
    is GifChainError.SourceInvalid -> stringResource(R.string.chain_error_source_invalid, index + 1)
    is GifChainError.SourceTooLarge -> stringResource(R.string.chain_error_source_too_large, index + 1)
    GifChainError.InputsTooLarge -> stringResource(R.string.chain_error_inputs_too_large)
    is GifChainError.TooManyFrames -> stringResource(R.string.chain_error_too_many_frames, limit)
    GifChainError.TooLarge -> stringResource(R.string.chain_error_too_large)
    GifChainError.SaveFailed -> stringResource(R.string.chain_error_save_failed)
    GifChainError.Generic -> stringResource(R.string.chain_error_generic)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GifChainScreen(
    gifUris: String?,
    onBack: () -> Unit,
    onSendToBackpack: (String) -> Unit,
    viewModel: GifChainViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sendCallback by rememberUpdatedState(onSendToBackpack)
    LaunchedEffect(gifUris) { viewModel.load(gifUris) }
    LaunchedEffect(viewModel) { viewModel.sendEvents.collect { sendCallback(it) } }
    val controlsEnabled = !state.saving
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
        TopAppBar(
            title = { Text(stringResource(R.string.chain_title)) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
            }
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.chain_items_title), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            state.items.forEachIndexed { index, item ->
                ChainItemRow(
                    position = index + 1,
                    uri = item.uri,
                    canMoveUp = index > 0,
                    canMoveDown = index < state.items.lastIndex,
                    enabled = controlsEnabled,
                    onUp = { viewModel.moveUp(index) },
                    onDown = { viewModel.moveDown(index) },
                    onRemove = { viewModel.remove(index) }
                )
            }
            if (!GifChainPlan.canRender(state.items.size)) {
                Text(stringResource(R.string.chain_need_two), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }

            Text(stringResource(R.string.chain_settings_title), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.chain_scale_title), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    GifScaleMode.Fit to R.string.chain_scale_fit,
                    GifScaleMode.CenterCrop to R.string.chain_scale_crop
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = state.scaleMode == mode,
                        enabled = controlsEnabled,
                        onClick = { viewModel.setScaleMode(mode) },
                        label = { Text(stringResource(label)) },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { role = Role.RadioButton }
                    )
                }
            }
            val pauseDescription = stringResource(R.string.chain_pause_description)
            Text(stringResource(R.string.chain_pause_label, state.pauseMs), style = MaterialTheme.typography.labelLarge)
            var pauseDraft by remember(state.pauseMs) { mutableFloatStateOf(state.pauseMs.toFloat()) }
            Slider(
                value = pauseDraft,
                onValueChange = { pauseDraft = it },
                onValueChangeFinished = { viewModel.setPause(pauseDraft.toInt()) },
                valueRange = 0f..GifChainPlan.MAX_PAUSE_MS.toFloat(),
                steps = GifChainPlan.MAX_PAUSE_MS / 100 - 1,
                enabled = controlsEnabled,
                modifier = Modifier.heightIn(min = 48.dp).semantics {
                    contentDescription = pauseDescription
                    stateDescription = "${pauseDraft.toInt()} ms"
                }
            )
            val speedOn = state.speedOverrideMs != null
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                    value = speedOn, enabled = controlsEnabled, role = Role.Switch,
                    onValueChange = { viewModel.setSpeedOverride(if (it) 100 else null) }
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.chain_speed_toggle), Modifier.weight(1f))
                Switch(checked = speedOn, onCheckedChange = null, enabled = controlsEnabled)
            }
            state.speedOverrideMs?.let { ms ->
                val speedDescription = stringResource(R.string.chain_speed_description)
                Text(stringResource(R.string.chain_speed_label, ms), style = MaterialTheme.typography.labelLarge)
                var speedDraft by remember(ms) { mutableFloatStateOf(ms.toFloat()) }
                Slider(
                    value = speedDraft,
                    onValueChange = { speedDraft = it },
                    onValueChangeFinished = { viewModel.setSpeedOverride(speedDraft.toInt() / 10 * 10) },
                    valueRange = GifChainPlan.MIN_SPEED_MS.toFloat()..GifChainPlan.MAX_SPEED_MS.toFloat(),
                    enabled = controlsEnabled,
                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                        contentDescription = speedDescription
                        stateDescription = "${speedDraft.toInt()} ms"
                    }
                )
            }

            Text(stringResource(R.string.chain_preview_title), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            Box(Modifier.fillMaxWidth().heightIn(max = 320.dp).aspectRatio(1f).background(Color.Black),
                contentAlignment = Alignment.Center) {
                if (preview != null) {
                    AsyncImage(model = preview, contentDescription = stringResource(R.string.chain_preview_description),
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
                }
                if (state.stage == ChainStage.Rendering) CircularProgressIndicator()
            }
            when (state.stage) {
                ChainStage.Rendering -> {
                    Text(stringResource(R.string.chain_rendering),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    OutlinedButton(onClick = viewModel::cancel, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.chain_cancel_render))
                    }
                }
                ChainStage.Cancelled -> {
                    Text(stringResource(R.string.chain_cancelled),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    OutlinedButton(onClick = viewModel::retry, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.chain_render_again))
                    }
                }
                ChainStage.Error -> OutlinedButton(onClick = viewModel::retry, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.chain_render_again))
                }
                else -> Unit
            }
            state.error?.let {
                Text(it.message(), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
            }
            state.status?.let { status ->
                Text(stringResource(R.string.chain_info, status.frames, status.sizeKb),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                when (status.kind) {
                    ChainStatusKind.WARN -> Text(stringResource(R.string.chain_warn), color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    ChainStatusKind.BLOCK -> Text(stringResource(R.string.chain_block), color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    ChainStatusKind.OK -> Unit
                }
            }
            if (state.savedUri != null) {
                Text(stringResource(R.string.chain_saved), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { viewModel.save(sendToBackpack = false) },
                    enabled = state.canSave,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) { Text(stringResource(if (state.saving) R.string.chain_saving else R.string.chain_save)) }
                Button(
                    onClick = { viewModel.save(sendToBackpack = true) },
                    enabled = state.canSave,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) { Text(stringResource(R.string.chain_send)) }
            }
        }
    }
}

@Composable
private fun ChainItemRow(
    position: Int,
    uri: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    enabled: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val request = remember(uri, context) { ImageRequest.Builder(context).data(uri).size(128).build() }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = request,
            contentDescription = stringResource(R.string.chain_item_thumb_description, position),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).background(Color.Black)
        )
        Text(stringResource(R.string.chain_item_label, position), modifier = Modifier.weight(1f).padding(start = 12.dp))
        IconButton(onClick = onUp, enabled = enabled && canMoveUp, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.chain_move_up, position))
        }
        IconButton(onClick = onDown, enabled = enabled && canMoveDown, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.chain_move_down, position))
        }
        IconButton(onClick = onRemove, enabled = enabled, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.chain_remove, position))
        }
    }
}
