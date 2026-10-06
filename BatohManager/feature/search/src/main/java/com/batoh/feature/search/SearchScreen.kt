package com.batoh.feature.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.batoh.core.domain.model.*
import com.batoh.core.ui.components.ShimmerSkeletonGrid
import com.batoh.core.ui.components.shimmerEffect
import com.batoh.feature.search.R
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

@Composable
fun SearchRoute(
    gridColumns: Int,
    onNavigateToDetail: (gifUrl: String, mp4Url: String) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val saveStates by viewModel.saveStates.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()

    SearchScreen(
        uiState = uiState,
        gridColumns = gridColumns,
        searchQuery = searchQuery,
        filter = filter,
        saveStates = saveStates,
        searchHistory = searchHistory,
        onQueryChanged = viewModel::onQueryChanged,
        onSetGifSource = viewModel::setGifSource,
        onSetContentType = viewModel::setContentType,
        onSetAspectRatio = viewModel::setAspectRatio,
        onSaveGif = viewModel::saveGif,
        onGifClick = { gif -> onNavigateToDetail(gif.originalUrl, gif.mp4Url) },
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onRetryLoadMore = viewModel::retryLoadMore,
        onBack = onBack,
        onHistoryItemClick = viewModel::onQueryChanged,
        onDeleteHistoryItem = viewModel::deleteHistoryItem
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    uiState: SearchUiState,
    gridColumns: Int,
    searchQuery: String,
    filter: GifFilter,
    saveStates: Map<String, GifSaveState>,
    searchHistory: List<String>,
    onQueryChanged: (String) -> Unit,
    onSetGifSource: (GifSource) -> Unit,
    onSetContentType: (GifType) -> Unit,
    onSetAspectRatio: (AspectRatio?) -> Unit,
    onSaveGif: (Gif) -> Unit,
    onGifClick: (Gif) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onBack: () -> Unit,
    onHistoryItemClick: (String) -> Unit,
    onDeleteHistoryItem: (String) -> Unit
) {
    var showFilterSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val gridState = rememberLazyGridState()
    val searchFocus = remember { FocusRequester() }
    val successState = uiState as? SearchUiState.Success
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) return@derivedStateOf false
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index
                ?: return@derivedStateOf false
            lastVisibleItemIndex >= totalItems - 6
        }
    }

    LaunchedEffect(shouldLoadMore, successState?.hasMore, successState?.isLoadingMore, successState?.gifs?.size, successState?.loadMoreError) {
        val state = successState ?: return@LaunchedEffect
        if (shouldLoadMore && state.hasMore && !state.isLoadingMore && state.loadMoreError == null) {
            onLoadMore()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.search_back))
                    }
                },
                actions = {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.search_refresh))
                    }
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.search_filters),
                            tint = if (filter.aspectRatio != null) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
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
            // Search Input
            TextField(
                value = searchQuery,
                onValueChange = onQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(searchFocus),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onQueryChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.search_clear))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            if (searchQuery.isEmpty() && searchHistory.isNotEmpty()) {
                Text(
                    stringResource(R.string.search_recent),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        .semantics { heading() },
                    color = MaterialTheme.colorScheme.primary
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchHistory, key = { it }) { item ->
                        val removeLabel = stringResource(R.string.search_history_remove, item)
                        InputChip(
                            selected = false,
                            onClick = { onHistoryItemClick(item) },
                            label = { Text(item) },
                            // The tiny close icon is hard to hit; TalkBack users get a custom action.
                            modifier = Modifier.semantics {
                                customActions = listOf(CustomAccessibilityAction(removeLabel) {
                                    onDeleteHistoryItem(item); true
                                })
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = removeLabel,
                                    // 4 dp padding inside the click area widens the touch target
                                    // to 24 dp without changing the chip height.
                                    modifier = Modifier.clickable { onDeleteHistoryItem(item) }.padding(4.dp).size(16.dp)
                                )
                            }
                        )
                    }
                }
            }

            // Quick source selection
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = filter.source == GifSource.GIPHY,
                        onClick = { onSetGifSource(GifSource.GIPHY) },
                        label = { Text("Giphy") }
                    )
                }
                item {
                    FilterChip(
                        selected = filter.source == GifSource.KLIPY,
                        onClick = { onSetGifSource(GifSource.KLIPY) },
                        label = { Text("Klipy") }
                    )
                }
                item {
                    FilterChip(
                        selected = filter.aspectRatio == AspectRatio.SQUARE,
                        onClick = { onSetAspectRatio(if (filter.aspectRatio == AspectRatio.SQUARE) null else AspectRatio.SQUARE) },
                        label = {
                            // Spoken without the decorative square emoji.
                            val spoken = stringResource(R.string.search_square_only_a11y)
                            Text(stringResource(R.string.filter_square_only),
                                modifier = Modifier.semantics { contentDescription = spoken })
                        },
                        leadingIcon = { if (filter.aspectRatio == AspectRatio.SQUARE) Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                    )
                }
            }

            // Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (uiState) {
                    is SearchUiState.Loading -> {
                        ShimmerSkeletonGrid(gridColumns = gridColumns)
                    }
                    is SearchUiState.Success -> {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(gridColumns.coerceIn(2, 3)),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val headerLabel = when {
                                uiState.isPersonalized -> R.string.search_header_personalized
                                uiState.isTrending -> R.string.search_header_trending
                                else -> null
                            }
                            if (headerLabel != null) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    Text(
                                        text = stringResource(headerLabel),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 4.dp).semantics { heading() }
                                    )
                                }
                            }
                            items(uiState.gifs, key = { "${it.source}|${it.id}|${it.originalUrl}" }) { gif ->
                                @OptIn(ExperimentalFoundationApi::class)
                                GifItem(
                                    gif = gif,
                                    saveState = saveStates[gif.downloadKey()],
                                    onClick = { onGifClick(gif) },
                                    onSaveGif = { onSaveGif(gif) },
                                    modifier = Modifier.animateItemPlacement()
                                )
                            }
                            // Load more footer
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                if (uiState.loadMoreError != null) {
                                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(uiState.loadMoreError, color = MaterialTheme.colorScheme.error,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                                        TextButton(onClick = onRetryLoadMore) { Text(stringResource(R.string.search_retry)) }
                                    }
                                } else if (uiState.isLoadingMore) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(40.dp))
                                    }
                                }
                            }
                        }
                    }
                    is SearchUiState.Empty -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = when {
                                    (filter.source == GifSource.KLIPY || filter.source == GifSource.LOSPEC) && searchQuery.isBlank() ->
                                        stringResource(R.string.search_empty_source, filter.source.name.lowercase().replaceFirstChar { it.uppercase() })
                                    searchQuery.isNotBlank() ->
                                        stringResource(R.string.search_empty)
                                    else ->
                                        stringResource(R.string.search_empty_prompt)
                                },
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            // Recovery: clear a query with no results, or jump to the search field.
                            if (searchQuery.isNotBlank()) {
                                OutlinedButton(onClick = { onQueryChanged("") }) {
                                    Text(stringResource(R.string.search_clear_query))
                                }
                            } else {
                                OutlinedButton(onClick = {
                                    searchFocus.requestFocus()
                                    keyboardController?.show()
                                }) { Text(stringResource(R.string.search_start_typing)) }
                            }
                        }
                    }
                    is SearchUiState.Error -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(80.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = uiState.message,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = onRetry,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.search_retry),
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showFilterSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                sheetState = sheetState
            ) {
                FilterContent(
                    filter = filter,
                    onSetGifSource = onSetGifSource,
                    onSetContentType = onSetContentType,
                    onSetAspectRatio = onSetAspectRatio
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FilterContent(
    filter: GifFilter,
    onSetGifSource: (GifSource) -> Unit,
    onSetContentType: (GifType) -> Unit,
    onSetAspectRatio: (AspectRatio?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 48.dp, start = 16.dp, end = 16.dp)
    ) {
        Text(stringResource(R.string.filter_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() })
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(stringResource(R.string.filter_source), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GifSource.values().filter { it != GifSource.ALL }.forEach { source ->
                FilterChip(
                    selected = filter.source == source,
                    onClick = { onSetGifSource(source) },
                    label = { Text(stringResource(when (source) {
                        GifSource.GIPHY -> R.string.source_giphy
                        GifSource.KLIPY -> R.string.source_klipy
                        GifSource.LOSPEC -> R.string.source_lospec
                        GifSource.ALL -> R.string.filter_all
                    })) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.filter_type), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GifType.values().forEach { type ->
                FilterChip(
                    selected = filter.type == type,
                    onClick = { onSetContentType(type) },
                    label = { Text(stringResource(if (type == GifType.GIF) R.string.type_gif else R.string.type_sticker)) },
                    enabled = filter.source == GifSource.GIPHY || type == GifType.GIF
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.filter_aspect_ratio), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filter.aspectRatio == null,
                onClick = { onSetAspectRatio(null) },
                label = { Text(stringResource(R.string.filter_all)) }
            )
            FilterChip(
                selected = filter.aspectRatio == AspectRatio.SQUARE,
                onClick = { onSetAspectRatio(AspectRatio.SQUARE) },
                label = { Text(stringResource(R.string.filter_square)) }
            )
            FilterChip(
                selected = filter.aspectRatio == AspectRatio.WIDE,
                onClick = { onSetAspectRatio(AspectRatio.WIDE) },
                label = { Text(stringResource(R.string.filter_wide)) }
            )
        }
    }
}

@Composable
fun GifItem(
    gif: Gif,
    saveState: GifSaveState?,
    onClick: () -> Unit,
    onSaveGif: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val thumbnailRequest = remember(gif.thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(gif.thumbnailUrl)
            .size(256)
            .crossfade(true)
            .listener(
                onError = { _, result -> android.util.Log.e("CoilDebug", "FAIL: ${gif.thumbnailUrl} → ${result.throwable}") },
                onSuccess = { _, _ -> android.util.Log.d("CoilDebug", "OK: ${gif.thumbnailUrl.take(80)}") }
            )
            .build()
    }

    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "gif_fade_in"
    )
    LaunchedEffect(Unit) { visible = true }

    val title = gif.title.ifBlank { stringResource(R.string.search_untitled) }
    val saving = saveState?.stage == GifSaveStage.Pending || saveState?.stage == GifSaveStage.Running
    val saveLabel = stringResource(when (saveState?.stage) {
        GifSaveStage.Pending -> R.string.search_save_pending
        GifSaveStage.Running -> R.string.search_save_running
        GifSaveStage.Success -> R.string.search_save_done
        GifSaveStage.Error -> R.string.search_save_retry
        null -> R.string.search_save
    })
    val sourceLabel = remember(gif.source) {
        when (gif.source.lowercase()) {
            "giphy" -> "Giphy"
            "klipy" -> "Klipy"
            "lospec" -> "Lospec"
            "sticker" -> "Sticker"
            else -> null
        }
    }
    // Error text overlays the image; include it so a failed download is announced on the tile.
    val errorLabel = if (saveState?.stage == GifSaveStage.Error) saveState.message ?: stringResource(R.string.search_save_failed) else null
    val baseDescription = if (sourceLabel != null) stringResource(R.string.search_tile_a11y, title, sourceLabel) else title
    val tileDescription = listOfNotNull(baseDescription, errorLabel).joinToString(", ")

    Card(
        modifier = modifier.graphicsLayer { this.alpha = alpha },
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shimmerEffect()
            )
            AsyncImage(
                model = thumbnailRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clickable(onClickLabel = stringResource(R.string.search_open_detail), onClick = onClick)
                    .semantics { contentDescription = tileDescription }
            )
            // Source badge
            if (sourceLabel != null) {
                // Source is read as part of the tile description.
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                ) {
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp).clearAndSetSemantics { }
                    )
                }
            }
            val saveDescription = stringResource(R.string.search_save_a11y, saveLabel, title)
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = RoundedCornerShape(6.dp), modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                TextButton(onClick = onSaveGif, enabled = !saving && saveState?.stage != GifSaveStage.Success,
                    modifier = Modifier.semantics {
                        contentDescription = saveDescription
                        liveRegion = LiveRegionMode.Polite
                    }) {
                    if (saving) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(saveLabel)
                }
            }
            if (saveState?.stage == GifSaveStage.Error) {
                Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    Text(saveState.message ?: stringResource(R.string.search_save_failed), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(4.dp))
                }
            }
        }
    }
}
