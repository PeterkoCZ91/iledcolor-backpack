package com.batoh.feature.home

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
    onNavigateToTextBanner: () -> Unit = {}
) {
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
        onTextBannerClick = onNavigateToTextBanner
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
    onTextBannerClick: () -> Unit = {}
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
            item {
                HomeButton(
                    text = stringResource(R.string.home_backpack_button),
                    icon = Icons.Default.Bluetooth,
                    onClick = onBackpackClick,
                    containerColor = Color(0xFF00BCD4),
                    contentColor = Color.White
                )
            }
            item {
                HomeButton(
                    text = stringResource(R.string.home_text_banner_button),
                    icon = Icons.Default.Edit,
                    onClick = onTextBannerClick,
                    containerColor = Color(0xFFE65100),
                    contentColor = Color.White
                )
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
    contentColor: Color
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium
            )
        }
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
        "Neznámá"
    }
}
