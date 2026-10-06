package com.batoh.feature.library
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import android.util.Log
import com.batoh.core.domain.model.Gif
import com.batoh.core.ui.components.ShimmerSkeletonGrid
import com.batoh.core.ui.components.shimmerEffect
import com.batoh.feature.library.R
import androidx.compose.foundation.background
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.graphics.FilterQuality
import coil.decode.BitmapFactoryDecoder
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

@Composable
fun LibraryRoute(
    gridColumns: Int,
    onNavigateToDetail: (String) -> Unit,
    onSendToBackpack: (String) -> Unit,
    onEditGif: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.actionMessage.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val consent by viewModel.deleteConsent.collectAsStateWithLifecycle()
    val previewDiagnoses by viewModel.previewDiagnoses.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingImport by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val storagePermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        val uri = pendingImport
        pendingImport = null
        if (granted && uri != null) viewModel.importGif(android.net.Uri.parse(uri))
        else viewModel.showMessage(context.getString(R.string.library_storage_permission_needed))
    }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            if (android.os.Build.VERSION.SDK_INT < 29 && androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                pendingImport = it.toString()
                storagePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else viewModel.importGif(it)
        }
    }
    val deleteLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> viewModel.deleteConsentResult(result.resultCode == android.app.Activity.RESULT_OK) }
    androidx.compose.runtime.LaunchedEffect(consent) {
        consent?.let { sender ->
            deleteLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build())
            viewModel.deleteConsentLaunched()
        }
    }

    LibraryScreen(
        uiState = uiState,
        gridColumns = gridColumns,
        onGifClick = { gif -> onNavigateToDetail(gif.originalUrl) },
        onGifLongClick = viewModel::deleteGif,
        onSendGif = { gif -> onSendToBackpack(gif.originalUrl) },
        onEditGif = { gif -> onEditGif(gif.originalUrl) },
        onImport = { picker.launch(arrayOf("image/gif")) },
        importing = importing,
        actionMessage = message,
        onBack = onBack,
        previewDiagnoses = previewDiagnoses,
        onPreviewFailed = viewModel::onPreviewFailed,
        onPreviewLoaded = viewModel::onPreviewLoaded,
        onRetry = viewModel::retry
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    gridColumns: Int,
    onGifClick: (Gif) -> Unit,
    onGifLongClick: (Gif) -> Unit,
    onSendGif: (Gif) -> Unit,
    onEditGif: (Gif) -> Unit,
    onImport: () -> Unit,
    importing: Boolean,
    actionMessage: String?,
    onBack: () -> Unit,
    previewDiagnoses: Map<String, PreviewDiagnosis> = emptyMap(),
    onPreviewFailed: (Gif) -> Unit = {},
    onPreviewLoaded: (Gif) -> Unit = {},
    onRetry: () -> Unit = {}
) {
    var gifToAction by remember { mutableStateOf<Gif?>(null) }
    var gifToRemove by remember { mutableStateOf<Gif?>(null) }
    var backpackView by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    gifToRemove?.let { gif ->
        AlertDialog(
            onDismissRequest = { gifToRemove = null },
            title = { Text(stringResource(R.string.library_remove_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.library_remove_message, gif.title))
                    (previewDiagnoses[gif.id] as? PreviewDiagnosis.Broken)?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(it.problem.reasonRes()), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    gifToRemove = null
                    onGifLongClick(gif)
                }) { Text(stringResource(R.string.library_remove_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { gifToRemove = null }) { Text(stringResource(R.string.library_remove_cancel)) }
            }
        )
    }

    // Single action dialog — Share or Delete
    if (gifToAction != null) {
        AlertDialog(
            onDismissRequest = { gifToAction = null },
            title = { Text(stringResource(R.string.library_action_dialog_title), style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    Text(gifToAction?.title ?: "", style = MaterialTheme.typography.bodyMedium)
                    val diagnosis = gifToAction?.let { previewDiagnoses[it.id] }
                    (diagnosis as? PreviewDiagnosis.Broken)?.let {
                        Text(stringResource(it.problem.reasonRes()), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                    if (diagnosis.allowsEditAndSend()) {
                        TextButton(onClick = {
                            gifToAction?.let(onEditGif)
                            gifToAction = null
                        }) { Text(stringResource(R.string.library_action_edit)) }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = gifToAction?.let { previewDiagnoses[it.id] }.allowsEditAndSend(),
                    onClick = {
                        gifToAction?.let { gif ->
                            val sendIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                putExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri.parse(gif.originalUrl))
                                type = "image/gif"
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(sendIntent, null))
                        }
                        gifToAction = null
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.padding(4.dp))
                        Text(stringResource(R.string.library_action_share), style = MaterialTheme.typography.titleMedium)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        gifToRemove = gifToAction
                        gifToAction = null
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.padding(4.dp))
                        Text(stringResource(R.string.library_action_delete), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                actions = {
                    TextButton(onClick = onImport, enabled = !importing) {
                        Text(stringResource(if (importing) R.string.library_importing else R.string.library_import))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.library_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Polite live region so TalkBack announces import/delete results.
            actionMessage?.let {
                Text(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite })
            }
            Box(Modifier.weight(1f)) {
            Crossfade(targetState = uiState, label = "library_state") { state ->
                when (state) {
                    is LibraryUiState.Loading -> {
                        ShimmerSkeletonGrid(gridColumns = gridColumns, itemCount = 8)
                    }
                    is LibraryUiState.Success -> {
                        Column {
                            Text(
                                text = stringResource(R.string.library_action_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                FilterChip(
                                    selected = backpackView,
                                    onClick = { backpackView = !backpackView },
                                    label = { Text(stringResource(R.string.library_backpack_view)) }
                                )
                                if (backpackView) {
                                    Text(
                                        stringResource(R.string.library_backpack_view_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(gridColumns.coerceIn(2, 3)),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(state.gifs, key = { it.id }) { gif ->
                                    val diagnosis = previewDiagnoses[gif.id]
                                    @OptIn(ExperimentalFoundationApi::class)
                                    GifItem(
                                        gif = gif,
                                        onClick = {
                                            if (diagnosis is PreviewDiagnosis.Broken) gifToRemove = gif
                                            else onGifClick(gif)
                                        },
                                        onLongClick = { gifToAction = gif },
                                        onSend = { onSendGif(gif) },
                                        onEdit = { onEditGif(gif) },
                                        modifier = Modifier.animateItemPlacement(),
                                        diagnosis = diagnosis,
                                        backpackView = backpackView,
                                        onPreviewFailed = { onPreviewFailed(gif) },
                                        onPreviewLoaded = { onPreviewLoaded(gif) },
                                        onRemove = { gifToRemove = gif }
                                    )
                                }
                            }
                        }
                    }
                    is LibraryUiState.Empty -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.library_empty),
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                                    .semantics { liveRegion = LiveRegionMode.Polite }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onImport, enabled = !importing) {
                                Text(stringResource(if (importing) R.string.library_importing else R.string.library_import))
                            }
                        }
                    }
                    is LibraryUiState.Error -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onRetry) { Text(stringResource(R.string.library_retry)) }
                        }
                    }
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GifItem(
    gif: Gif,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSend: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    diagnosis: PreviewDiagnosis? = null,
    backpackView: Boolean = false,
    onPreviewFailed: () -> Unit = {},
    onPreviewLoaded: () -> Unit = {},
    onRemove: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var loadingPreview by remember(gif.thumbnailUrl, backpackView) { mutableStateOf(true) }
    var previewFailed by remember(gif.thumbnailUrl, backpackView) { mutableStateOf(false) }
    val thumbnailRequest = remember(gif.thumbnailUrl, backpackView) {
        ImageRequest.Builder(context)
            .data(gif.thumbnailUrl)
            .apply {
                if (backpackView) {
                    // Static first frame, sampled to the exact 64×64 panel grid like the upload path.
                    decoderFactory(BitmapFactoryDecoder.Factory())
                    size(512)
                    allowHardware(false)
                    transformations(BackpackPreviewTransformation(crop = true))
                } else {
                    size(256)
                    crossfade(true)
                }
            }
            .build()
    }
    val actionsAllowed = diagnosis.allowsEditAndSend()
    val broken = diagnosis as? PreviewDiagnosis.Broken
    // TalkBack label: title plus the preview/file state, so broken tiles are not silent.
    val title = gif.title.ifBlank { stringResource(R.string.library_a11y_untitled) }
    val stateLabel = when {
        broken != null -> stringResource(R.string.library_a11y_state_broken)
        diagnosis == PreviewDiagnosis.PreviewOnly -> stringResource(R.string.library_a11y_state_preview_only)
        previewFailed -> stringResource(R.string.library_a11y_state_checking)
        else -> null
    }
    val tileDescription = if (stateLabel != null) stringResource(R.string.library_a11y_tile_with_state, title, stateLabel)
        else stringResource(R.string.library_a11y_tile, title)
    val clickLabel = stringResource(if (broken != null) R.string.library_a11y_remove_broken else R.string.library_a11y_open_detail)
    val longClickLabel = stringResource(R.string.library_a11y_more_actions)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .then(if (loadingPreview) Modifier.shimmerEffect() else Modifier)
                    .then(if (backpackView && !loadingPreview) Modifier.background(androidx.compose.ui.graphics.Color.Black) else Modifier)
            )
            AsyncImage(
                model = thumbnailRequest,
                contentDescription = tileDescription,
                onLoading = { loadingPreview = true },
                onError = {
                    loadingPreview = false
                    previewFailed = true
                    Log.w("GifPreview", "Library decode failed", it.result.throwable)
                    onPreviewFailed()
                },
                onSuccess = {
                    loadingPreview = false
                    previewFailed = false
                    onPreviewLoaded()
                },
                contentScale = if (backpackView) ContentScale.Fit else ContentScale.Crop,
                filterQuality = if (backpackView) FilterQuality.None else FilterQuality.Low,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .combinedClickable(
                        onClickLabel = clickLabel,
                        onLongClickLabel = longClickLabel,
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
            )
            if (actionsAllowed) {
                onEdit?.let { edit ->
                    androidx.compose.material3.FilledIconButton(
                        onClick = edit,
                        enabled = !previewFailed || diagnosis == PreviewDiagnosis.PreviewOnly,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.library_a11y_tile_with_state, stringResource(R.string.library_edit_description), title))
                    }
                }
            }
            if (previewFailed) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        when {
                            broken != null -> stringResource(broken.problem.reasonRes())
                            diagnosis == PreviewDiagnosis.PreviewOnly -> stringResource(R.string.library_preview_only_failed)
                            else -> stringResource(R.string.library_preview_checking)
                        },
                        color = if (diagnosis == PreviewDiagnosis.PreviewOnly) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        // Already part of the tile's content description.
                        modifier = Modifier.clearAndSetSemantics { }
                    )
                    if (broken != null && onRemove != null) {
                        val removeDescription = stringResource(R.string.library_remove_description)
                        OutlinedButton(onClick = onRemove, modifier = Modifier.padding(top = 4.dp)
                            .semantics { contentDescription = removeDescription }) {
                            Icon(Icons.Default.Delete, contentDescription = null,
                                modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.padding(2.dp))
                            Text(stringResource(R.string.library_remove), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            if (actionsAllowed) {
                onSend?.let { send ->
                    androidx.compose.material3.FilledIconButton(
                        onClick = send,
                        enabled = !previewFailed || diagnosis == PreviewDiagnosis.PreviewOnly,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = stringResource(R.string.library_a11y_tile_with_state, stringResource(R.string.library_send_description), title))
                    }
                }
            }
        }
    }
}
