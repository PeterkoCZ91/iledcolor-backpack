package com.batoh.feature.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.batoh.core.data.bluetooth.PanelFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeRoute(
    onNavigateToSearch: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToConvert: () -> Unit,
    onNavigateToBackpack: () -> Unit,
    onNavigateToTextBanner: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val backpackStatus by viewModel.backpackStatus.collectAsStateWithLifecycle()
    val recentGifs by viewModel.recentGifs.collectAsStateWithLifecycle()
    val noSavedPanel by viewModel.noSavedPanel.collectAsStateWithLifecycle()
    HomeScreen(
        onSearchClick = onNavigateToSearch,
        onLibraryClick = onNavigateToLibrary,
        onConvertClick = onNavigateToConvert,
        onBackpackClick = onNavigateToBackpack,
        onTextBannerClick = onNavigateToTextBanner,
        backpackStatus = backpackStatus,
        recentGifs = recentGifs,
        onRecentGifClick = onNavigateToDetail,
        showFirstPanelCard = noSavedPanel
    )
}

/** Dashboard: backpack status, search entry, quick actions and recent GIFs. */
@Composable
fun HomeScreen(
    onSearchClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onConvertClick: () -> Unit,
    onBackpackClick: () -> Unit,
    onTextBannerClick: () -> Unit = {},
    backpackStatus: HomeBackpackStatus = HomeBackpackStatus(),
    recentGifs: List<HomeRecentGif> = emptyList(),
    onRecentGifClick: (String) -> Unit = {},
    showFirstPanelCard: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (showFirstPanelCard) {
            FirstPanelCard(onConnectClick = onBackpackClick)
        }
        BackpackStatusCard(status = backpackStatus, onClick = onBackpackClick)
        SearchEntry(onClick = onSearchClick)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
                HomeButton(
                    text = stringResource(R.string.home_text_banner_button),
                    icon = Icons.Default.Edit,
                    onClick = onTextBannerClick,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    height = 96.dp
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                HomeButton(
                    text = stringResource(R.string.home_convert_button),
                    icon = Icons.Default.PlayArrow,
                    onClick = onConvertClick,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    height = 96.dp
                )
            }
        }
        if (recentGifs.isNotEmpty()) {
            RecentGifsRow(gifs = recentGifs, onGifClick = onRecentGifClick, onShowAll = onLibraryClick)
        }
    }
}

/** First-run card shown until a panel is saved in My panels; the button opens the Panel tab. */
@Composable
private fun FirstPanelCard(onConnectClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.home_first_panel_title),
                style = MaterialTheme.typography.titleMedium
            )
            Button(onClick = onConnectClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_first_panel_button))
            }
        }
    }
}

/** Search-bar-looking button; a tap opens the Search tab. */
@Composable
private fun SearchEntry(onClick: () -> Unit) {
    val label = stringResource(R.string.home_search_hint)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { role = Role.Button },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null)
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}


/** Full-width backpack status card at the top of the home screen; a tap opens the Backpack screen. */
@Composable
fun BackpackStatusCard(
    status: HomeBackpackStatus,
    onClick: () -> Unit
) {
    val stateText = stringResource(
        when (status.state) {
            BackpackLinkState.CONNECTED -> R.string.home_status_connected
            BackpackLinkState.CONNECTING -> R.string.home_status_connecting
            BackpackLinkState.NOT_CONNECTED -> R.string.home_status_not_connected
        }
    )
    val deviceText = status.deviceName ?: stringResource(R.string.home_status_card_no_device)
    val label = stringResource(
        R.string.home_status_card_open_cd,
        listOf(stringResource(R.string.home_status_cd_state, stateText), status.deviceName?.let {
            stringResource(R.string.home_status_cd_device, it)
        }).filterNotNull().joinToString(", ")
    )
    val connected = status.state == BackpackLinkState.CONNECTED
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics { contentDescription = label; role = Role.Button },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (connected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (connected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (status.state) {
                    BackpackLinkState.CONNECTED -> Icons.Default.BluetoothConnected
                    BackpackLinkState.CONNECTING -> Icons.AutoMirrored.Filled.BluetoothSearching
                    BackpackLinkState.NOT_CONNECTED -> Icons.Default.Bluetooth
                },
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stateText, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = deviceText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                status.panel?.let { panel ->
                    Text(
                        text = stringResource(
                            R.string.home_panel_summary,
                            stringResource(
                                if (panel.family == PanelFamily.IPixel) R.string.home_panel_family_ipixel
                                else R.string.home_panel_family_backpack
                            ),
                            panel.width,
                            panel.height
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** "Recently in collection": horizontal row of static thumbnails; a tap opens the GIF detail. */
@Composable
fun RecentGifsRow(
    gifs: List<HomeRecentGif>,
    onGifClick: (String) -> Unit,
    onShowAll: () -> Unit = {}
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.home_recent_title),
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = onShowAll) {
                Text(stringResource(R.string.home_recent_show_all))
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(gifs, key = { it.id }) { gif ->
                val title = gif.title.ifBlank { stringResource(R.string.home_recent_untitled) }
                val cd = stringResource(R.string.home_recent_item_cd, title)
                val thumb by produceState<Bitmap?>(null, gif.thumbnailUrl) {
                    value = loadThumbnail(context, url = gif.thumbnailUrl)
                }
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(role = Role.Button, onClick = { onGifClick(gif.originalUrl) })
                        .semantics { contentDescription = cd },
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = thumb
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    subtitle: String? = null,
    accessibilityLabel: String? = null,
    height: Dp = 120.dp
) {
    val buttonModifier = Modifier
        .fillMaxWidth()
        .height(height)
    Button(
        onClick = onClick,
        modifier = if (accessibilityLabel != null) {
            buttonModifier.semantics { contentDescription = accessibilityLabel }
        } else {
            buttonModifier
        },
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        if (height < 100.dp && subtitle == null) {
            // Wide, low tile: icon and label side by side so the label is not clipped.
            Row(
                modifier = if (accessibilityLabel != null) Modifier.clearAndSetSemantics { } else Modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(32.dp))
                Text(text = text, style = MaterialTheme.typography.titleMedium)
            }
            return@Button
        }
        Column(
            // The button's own label replaces the visible texts for screen readers.
            modifier = if (accessibilityLabel != null) Modifier.clearAndSetSemantics { } else Modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(if (subtitle != null) 36.dp else 48.dp)
            )
            Spacer(modifier = Modifier.height(if (subtitle != null) 4.dp else 8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Decodes the first frame of a local image, down-sampled to roughly 192 px. Returns null for
 * remote URLs or on any decode failure (the tile then shows a placeholder icon). Uses only the
 * platform decoder, so the home module needs no image-loading dependency.
 */
private suspend fun loadThumbnail(context: Context, url: String): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val uri = Uri.parse(url)
        if (uri.scheme != "content" && uri.scheme != "file") return@withContext null
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / (sample * 2) >= 192) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (e: Exception) {
        null
    }
}
