package com.batoh.feature.search

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.batoh.core.domain.model.GifCategory
import com.batoh.core.ui.components.ShimmerSkeletonGrid
import java.util.Locale

@Composable
fun CategoriesRoute(
    gridColumns: Int,
    onCategoryClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CategoriesScreen(
        uiState = uiState,
        gridColumns = gridColumns,
        onCategoryClick = { category ->
            viewModel.onCategoryOpened(category.nameEncoded)
            onCategoryClick(category.nameEncoded)
        },
        onTogglePin = { category -> viewModel.togglePinned(category.nameEncoded) },
        onRetry = viewModel::retry,
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    uiState: CategoriesUiState,
    gridColumns: Int,
    onCategoryClick: (GifCategory) -> Unit,
    onTogglePin: (GifCategory) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.categories_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = context.getString(R.string.categories_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Crossfade(targetState = uiState, label = "categories_state") { state ->
                when (state) {
                    is CategoriesUiState.Loading -> {
                        ShimmerSkeletonGrid(gridColumns = gridColumns, cornerRadius = 12.dp)
                    }
                    is CategoriesUiState.Success -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(gridColumns.coerceIn(2, 3)),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (state.pinnedCategories.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    QuickCategorySection(
                                        title = context.getString(R.string.categories_pinned),
                                        categories = state.pinnedCategories,
                                        pinnedQueries = state.pinnedQueries,
                                        onCategoryClick = onCategoryClick,
                                        onTogglePin = onTogglePin
                                    )
                                }
                            }

                            if (state.monthlyTrendingCategories.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    QuickCategorySection(
                                        title = context.getString(R.string.categories_monthly_trending),
                                        categories = state.monthlyTrendingCategories,
                                        pinnedQueries = state.pinnedQueries,
                                        onCategoryClick = onCategoryClick,
                                        onTogglePin = onTogglePin
                                    )
                                }
                            }

                            if (state.recentCategories.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    QuickCategorySection(
                                        title = context.getString(R.string.categories_recent),
                                        categories = state.recentCategories,
                                        pinnedQueries = state.pinnedQueries,
                                        onCategoryClick = onCategoryClick,
                                        onTogglePin = onTogglePin
                                    )
                                }
                            }

                            if (state.categories.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(
                                        text = context.getString(R.string.categories_discover),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }

                            items(state.categories, key = { it.nameEncoded }) { category ->
                                val isPinned = state.pinnedQueries.contains(
                                    normalizeQuery(category.nameEncoded)
                                )
                                @OptIn(ExperimentalFoundationApi::class)
                                CategoryItem(
                                    category = category,
                                    isPinned = isPinned,
                                    onClick = { onCategoryClick(category) },
                                    onTogglePin = { onTogglePin(category) },
                                    modifier = Modifier.animateItemPlacement()
                                )
                            }
                        }
                    }
                    is CategoriesUiState.Error -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Button(onClick = onRetry) { Text(context.getString(R.string.categories_retry)) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickCategorySection(
    title: String,
    categories: List<GifCategory>,
    pinnedQueries: Set<String>,
    onCategoryClick: (GifCategory) -> Unit,
    onTogglePin: (GifCategory) -> Unit
) {
    val context = LocalContext.current
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories, key = { it.nameEncoded }) { category ->
                val normalizedQuery = normalizeQuery(category.nameEncoded)
                InputChip(
                    selected = pinnedQueries.contains(normalizedQuery),
                    onClick = { onCategoryClick(category) },
                    label = { Text(category.localizedDisplayName(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = {
                        Icon(
                            imageVector = if (pinnedQueries.contains(normalizedQuery)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = context.getString(
                                if (pinnedQueries.contains(normalizedQuery)) R.string.categories_unpin else R.string.categories_pin
                            ),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onTogglePin(category) }
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun CategoryItem(
    category: GifCategory,
    isPinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val previewGif = category.previewGif
    val displayName = category.localizedDisplayName()
    val thumbnailRequest = remember(previewGif?.thumbnailUrl) {
        previewGif?.thumbnailUrl?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .size(256)
                .crossfade(true)
                .build()
        }
    }
    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column {
                if (previewGif != null) {
                    AsyncImage(
                        model = thumbnailRequest,
                        contentDescription = displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    )
                } else {
                    // Placeholder for curated categories without preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(color = getCategoryColor(category.name)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = category.name.firstOrNull()?.toString() ?: "?",
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        IconButton(
            onClick = onTogglePin,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(
                imageVector = if (isPinned) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = context.getString(
                    if (isPinned) R.string.categories_unpin else R.string.categories_pin
                ),
                tint = if (isPinned) MaterialTheme.colorScheme.primary else Color.White
            )
        }
    }
}

@Composable
private fun getCategoryColor(name: String): Color {
    val hash = name.hashCode()
    return remember(name) {
        val colors = listOf(
            Color(0xFFF44336), Color(0xFFE91E63), Color(0xFF9C27B0),
            Color(0xFF673AB7), Color(0xFF3F51B5), Color(0xFF2196F3),
            Color(0xFF03A9F4), Color(0xFF00BCD4), Color(0xFF009688),
            Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFFFFC107),
            Color(0xFFFF9800), Color(0xFFFF5722)
        )
        colors[kotlin.math.abs(hash) % colors.size]
    }
}

@Composable
private fun GifCategory.localizedDisplayName(): String {
    val resource = when (nameEncoded) {
        "pixel art" -> R.string.category_pixel_art
        "8-bit" -> R.string.category_retro_8bit
        "pixel landscape loop" -> R.string.category_pixel_landscape
        "pixel character sprite" -> R.string.category_pixel_characters
        "neon loop" -> R.string.category_neon
        "cyberpunk city pixel" -> R.string.category_cyberpunk
        "vaporwave loop" -> R.string.category_vaporwave
        "matrix digital rain" -> R.string.category_matrix
        "glitch art loop" -> R.string.category_glitch
        "synthwave sunset loop" -> R.string.category_synthwave
        "lo-fi aesthetic loop" -> R.string.category_lo_fi
        "cat pixel art" -> R.string.category_cats
        "dog pixel art" -> R.string.category_dogs
        "funny animals loop" -> R.string.category_funny_animals
        "fish aquarium loop" -> R.string.category_fish
        "smiley face loop" -> R.string.category_smileys
        "emoji animated" -> R.string.category_emoji
        "thumbs up animated" -> R.string.category_thumbs_up
        "pixel fire loop" -> R.string.category_fire
        "water waves pixel loop" -> R.string.category_water
        "rain pixel loop" -> R.string.category_rain
        "sunset loop" -> R.string.category_sunset
        "pixel space stars" -> R.string.category_stars
        "gaming pixel art" -> R.string.category_gaming
        "retro game loop" -> R.string.category_retro_games
        "game over pixel" -> R.string.category_game_over
        "joystick controller pixel" -> R.string.category_joystick
        "heart pixel loop" -> R.string.category_heart
        "skull pixel art" -> R.string.category_skull
        "rainbow loop" -> R.string.category_rainbow
        "lightning bolt loop" -> R.string.category_lightning
        "hypnotic loop pattern" -> R.string.category_hypnotic
        "geometric pattern loop" -> R.string.category_geometric
        "kaleidoscope loop" -> R.string.category_kaleidoscope
        "christmas pixel art" -> R.string.category_christmas
        "halloween pixel art" -> R.string.category_halloween
        "hello text animated" -> R.string.category_hello
        "lol text animated" -> R.string.category_lol
        else -> 0
    }
    return if (resource == 0) name else stringResource(resource)
}

private fun normalizeQuery(query: String): String = query.trim().lowercase(Locale.ROOT)
