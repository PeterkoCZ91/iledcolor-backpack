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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
                    is CategoriesUiState.Success -> if (
                        state.pinnedCategories.isEmpty() && state.monthlyTrendingCategories.isEmpty() &&
                        state.recentCategories.isEmpty() && state.categories.isEmpty()
                    ) {
                        // Nothing to show: announce it and offer the same recovery as the error state.
                        CategoriesMessage(
                            message = stringResource(R.string.categories_empty),
                            isError = false,
                            onRetry = onRetry
                        )
                    } else {
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
                                        modifier = Modifier.padding(vertical = 8.dp).semantics { heading() }
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
                        CategoriesMessage(message = state.message, isError = true, onRetry = onRetry)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoriesMessage(message: String, isError: Boolean, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = message,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                // Announced when it appears so TalkBack users learn why the list is missing.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            Button(onClick = onRetry) { Text(stringResource(R.string.categories_retry)) }
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
            modifier = Modifier.padding(vertical = 8.dp).semantics { heading() }
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories, key = { it.nameEncoded }) { category ->
                val normalizedQuery = normalizeQuery(category.nameEncoded)
                val pinned = pinnedQueries.contains(normalizedQuery)
                val pinLabel = context.getString(if (pinned) R.string.categories_unpin else R.string.categories_pin)
                val pinnedState = stringResource(
                    if (pinned) R.string.categories_pinned_state else R.string.categories_not_pinned_state
                )
                InputChip(
                    selected = pinned,
                    onClick = { onCategoryClick(category) },
                    // The chip's "selected" means pinned; say so, and expose the tiny heart
                    // icon as a custom action because it is hard to hit with TalkBack.
                    modifier = Modifier.semantics {
                        stateDescription = pinnedState
                        customActions = listOf(CustomAccessibilityAction(pinLabel) {
                            onTogglePin(category); true
                        })
                    },
                    label = { Text(category.localizedDisplayName(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = {
                        Icon(
                            imageVector = if (pinnedQueries.contains(normalizedQuery)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = context.getString(
                                if (pinnedQueries.contains(normalizedQuery)) R.string.categories_unpin else R.string.categories_pin
                            ),
                            // 4 dp padding inside the click area enlarges the target without
                            // changing the chip height.
                            modifier = Modifier
                                .clickable { onTogglePin(category) }
                                .padding(4.dp)
                                .size(16.dp)
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
    val openLabel = stringResource(R.string.categories_open)
    val pinA11yLabel = stringResource(R.string.categories_pin_a11y, displayName)
    val pinnedState = stringResource(
        if (isPinned) R.string.categories_pinned_state else R.string.categories_not_pinned_state
    )
    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = openLabel, onClick = onClick),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column {
                if (previewGif != null) {
                    AsyncImage(
                        model = thumbnailRequest,
                        // The name is printed right below; reading it twice adds noise.
                        contentDescription = null,
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
                            .background(color = getCategoryColor(category.name))
                            .clearAndSetSemantics { },
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

        // Toggle semantics: "Pin <name>, switch, pinned / not pinned".
        IconToggleButton(
            checked = isPinned,
            onCheckedChange = { onTogglePin() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .semantics {
                    contentDescription = pinA11yLabel
                    stateDescription = pinnedState
                }
        ) {
            Icon(
                imageVector = if (isPinned) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = null,
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
