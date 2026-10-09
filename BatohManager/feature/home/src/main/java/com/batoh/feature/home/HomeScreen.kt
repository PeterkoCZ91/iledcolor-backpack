package com.batoh.feature.home

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.batoh.core.common.UiPreferences
import com.batoh.core.common.UiThemeMode
import com.batoh.core.domain.model.PredefinedInterests
import com.batoh.feature.home.R

@Composable
fun HomeRoute(
    themeMode: UiThemeMode,
    gridColumns: Int,
    appLanguage: String,
    onAppLanguageChanged: (String) -> Unit,
    onThemeModeChanged: (UiThemeMode) -> Unit,
    onGridColumnsChanged: (Int) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToConvert: () -> Unit,
    onNavigateToBackpack: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToTextBanner: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val backpackStatus by viewModel.backpackStatus.collectAsStateWithLifecycle()
    val recentGifs by viewModel.recentGifs.collectAsStateWithLifecycle()
    HomeScreen(
        themeMode = themeMode,
        gridColumns = gridColumns,
        appLanguage = appLanguage,
        onAppLanguageChanged = onAppLanguageChanged,
        onThemeModeChanged = onThemeModeChanged,
        onGridColumnsChanged = onGridColumnsChanged,
        onSearchClick = onNavigateToSearch,
        onLibraryClick = onNavigateToLibrary,
        onConvertClick = onNavigateToConvert,
        onBackpackClick = onNavigateToBackpack,
        onCategoriesClick = onNavigateToCategories,
        onTextBannerClick = onNavigateToTextBanner,
        backpackStatus = backpackStatus,
        recentGifs = recentGifs,
        onRecentGifClick = onNavigateToDetail
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    themeMode: UiThemeMode,
    gridColumns: Int,
    appLanguage: String,
    onAppLanguageChanged: (String) -> Unit,
    onThemeModeChanged: (UiThemeMode) -> Unit,
    onGridColumnsChanged: (Int) -> Unit,
    onSearchClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onConvertClick: () -> Unit,
    onBackpackClick: () -> Unit,
    onCategoriesClick: () -> Unit,
    onTextBannerClick: () -> Unit = {},
    backpackStatus: HomeBackpackStatus = HomeBackpackStatus(),
    recentGifs: List<HomeRecentGif> = emptyList(),
    onRecentGifClick: (String) -> Unit = {}
) {
    var showInfoDialog by rememberSaveable { mutableStateOf(false) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val appVersion = remember(context) { getAppVersion(context) }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text(stringResource(R.string.home_info_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.home_info_body))
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.home_info_version, appVersion),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(stringResource(R.string.home_info_ok))
                }
            }
        )
    }

    if (showSettingsDialog) {
        val prefs = remember {
            context.getSharedPreferences(UiPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        }
        var selectedInterests by remember {
            mutableStateOf(
                (prefs.getString(UiPreferences.KEY_USER_INTERESTS, "") ?: "")
                    .split(",")
                    .filter { it.isNotBlank() }
                    .toSet()
            )
        }
        var klipyApiKey by remember {
            mutableStateOf(prefs.getString(UiPreferences.PREF_KLIPY_KEY, "") ?: "")
        }
        var giphyApiKey by remember {
            mutableStateOf(prefs.getString(UiPreferences.PREF_GIPHY_KEY, "") ?: "")
        }
        var showApiKey by remember { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = { showSettingsDialog = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_settings_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.home_settings_theme),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = themeMode == UiThemeMode.SYSTEM,
                        onClick = { onThemeModeChanged(UiThemeMode.SYSTEM) },
                        label = { Text(stringResource(R.string.home_settings_theme_system)) }
                    )
                    FilterChip(
                        selected = themeMode == UiThemeMode.LIGHT,
                        onClick = { onThemeModeChanged(UiThemeMode.LIGHT) },
                        label = { Text(stringResource(R.string.home_settings_theme_light)) }
                    )
                    FilterChip(
                        selected = themeMode == UiThemeMode.DARK,
                        onClick = { onThemeModeChanged(UiThemeMode.DARK) },
                        label = { Text(stringResource(R.string.home_settings_theme_dark)) }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.home_settings_language),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        "system" to R.string.home_settings_language_system,
                        "cs" to R.string.home_settings_language_czech,
                        "en" to R.string.home_settings_language_english
                    ).forEach { (tag, labelRes) ->
                        FilterChip(
                            selected = appLanguage == tag,
                            onClick = { onAppLanguageChanged(tag) },
                            label = { Text(stringResource(labelRes)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.home_settings_grid_density),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = gridColumns == 2,
                        onClick = { onGridColumnsChanged(2) },
                        label = { Text(stringResource(R.string.home_settings_grid_2col)) }
                    )
                    FilterChip(
                        selected = gridColumns == 3,
                        onClick = { onGridColumnsChanged(3) },
                        label = { Text(stringResource(R.string.home_settings_grid_3col)) }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.home_settings_interests),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.home_settings_interests_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                @OptIn(ExperimentalLayoutApi::class)
                PredefinedInterests.groups.forEach { (group, tags) ->
                    Text(
                        text = stringResource(when (group) {
                            "Hudba" -> R.string.home_interest_group_music
                            "Vizual" -> R.string.home_interest_group_visuals
                            "Fun" -> R.string.home_interest_group_fun
                            "Priroda" -> R.string.home_interest_group_nature
                            "Symboly" -> R.string.home_interest_group_symbols
                            else -> R.string.home_settings_interests
                        }),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tags.forEach { tag ->
                            FilterChip(
                                selected = tag.name in selectedInterests,
                                onClick = {
                                    val updated = if (tag.name in selectedInterests) {
                                        selectedInterests - tag.name
                                    } else {
                                        selectedInterests + tag.name
                                    }
                                    selectedInterests = updated
                                    prefs.edit()
                                        .putString(
                                            UiPreferences.KEY_USER_INTERESTS,
                                            updated.joinToString(",")
                                        )
                                        .apply()
                                },
                                label = { Text(tag.name, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.home_settings_api_keys),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = klipyApiKey,
                    onValueChange = { newKey ->
                        klipyApiKey = newKey
                        prefs.edit()
                            .putString(UiPreferences.PREF_KLIPY_KEY, newKey)
                            .apply()
                    },
                    label = { Text(stringResource(R.string.home_settings_klipy_api_key)) },
                    supportingText = { Text(stringResource(R.string.home_settings_klipy_api_key_hint)) },
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = giphyApiKey,
                    onValueChange = { newKey ->
                        giphyApiKey = newKey
                        prefs.edit()
                            .putString(UiPreferences.PREF_GIPHY_KEY, newKey)
                            .apply()
                    },
                    label = { Text(stringResource(R.string.home_settings_giphy_api_key)) },
                    supportingText = { Text(stringResource(R.string.home_settings_giphy_api_key_hint)) },
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = { showSettingsDialog = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.home_settings_content_description),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(
                    onClick = { showInfoDialog = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.home_info_content_description),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BackpackStatusCard(status = backpackStatus, onClick = onBackpackClick)
            }
            item {
                HomeButton(
                    text = stringResource(R.string.home_search_button),
                    icon = Icons.Default.Search,
                    onClick = onSearchClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
            item {
                HomeButton(
                    text = stringResource(R.string.home_categories_button),
                    icon = Icons.Default.List,
                    onClick = onCategoriesClick,
                    containerColor = Color(0xFF7B1FA2),
                    contentColor = Color.White
                )
            }
            item {
                HomeButton(
                    text = stringResource(R.string.home_library_button),
                    icon = Icons.Default.Star,
                    onClick = onLibraryClick,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            }
            item {
                HomeButton(
                    text = stringResource(R.string.home_convert_button),
                    icon = Icons.Default.PlayArrow,
                    onClick = onConvertClick,
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            }
            // The backpack tile is replaced by the status card at the top, which opens the same screen.
            item(span = { GridItemSpan(maxLineSpan) }) {
                HomeButton(
                    text = stringResource(R.string.home_text_banner_button),
                    icon = Icons.Default.Edit,
                    onClick = onTextBannerClick,
                    containerColor = Color(0xFFE65100),
                    contentColor = Color.White,
                    height = 80.dp
                )
            }
            if (recentGifs.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    RecentGifsRow(gifs = recentGifs, onGifClick = onRecentGifClick)
                }
            }
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
    onGifClick: (String) -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_recent_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
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

/** Backpack tile: title, connection state and device name (firmware stays in the accessibility label). */
@Composable
fun BackpackHomeButton(
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
    // Two short lines fit the 120 dp tile; "state · name · FW" wrapped and got truncated.
    val subtitle = listOfNotNull(stateText, status.deviceName).joinToString("\n")

    val descriptionParts = mutableListOf(stringResource(R.string.home_status_cd_state, stateText))
    status.deviceName?.let { descriptionParts += stringResource(R.string.home_status_cd_device, it) }
    status.firmwareVersion?.let { descriptionParts += stringResource(R.string.home_status_cd_firmware, it) }

    HomeButton(
        text = stringResource(R.string.home_backpack_button),
        icon = when (status.state) {
            BackpackLinkState.CONNECTED -> Icons.Default.BluetoothConnected
            BackpackLinkState.CONNECTING -> Icons.AutoMirrored.Filled.BluetoothSearching
            BackpackLinkState.NOT_CONNECTED -> Icons.Default.Bluetooth
        },
        onClick = onClick,
        containerColor = Color(0xFF00BCD4),
        contentColor = Color.White,
        subtitle = subtitle,
        accessibilityLabel = descriptionParts.joinToString(", ")
    )
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

private fun getAppVersion(context: Context): String {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }
        "$versionName ($versionCode)"
    } catch (e: PackageManager.NameNotFoundException) {
        "Unknown"
    }
}
