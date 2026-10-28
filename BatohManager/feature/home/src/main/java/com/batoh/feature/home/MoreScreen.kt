package com.batoh.feature.home

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.batoh.core.common.UiPreferences
import com.batoh.core.common.UiThemeMode
import com.batoh.core.domain.model.PredefinedInterests

/** "More" tab: tools, inline settings and app info. No dialogs; preferences are written immediately. */
@Composable
fun MoreRoute(
    themeMode: UiThemeMode,
    gridColumns: Int,
    appLanguage: String,
    onAppLanguageChanged: (String) -> Unit,
    onThemeModeChanged: (UiThemeMode) -> Unit,
    onGridColumnsChanged: (Int) -> Unit,
    onNavigateToConvert: () -> Unit,
    onNavigateToTextBanner: () -> Unit
) {
    MoreScreen(
        themeMode = themeMode,
        gridColumns = gridColumns,
        appLanguage = appLanguage,
        onAppLanguageChanged = onAppLanguageChanged,
        onThemeModeChanged = onThemeModeChanged,
        onGridColumnsChanged = onGridColumnsChanged,
        onConvertClick = onNavigateToConvert,
        onTextBannerClick = onNavigateToTextBanner
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    themeMode: UiThemeMode,
    gridColumns: Int,
    appLanguage: String,
    onAppLanguageChanged: (String) -> Unit,
    onThemeModeChanged: (UiThemeMode) -> Unit,
    onGridColumnsChanged: (Int) -> Unit,
    onConvertClick: () -> Unit,
    onTextBannerClick: () -> Unit
) {
    val context = LocalContext.current
    val appVersion = remember(context) {
        getAppVersion(context, context.getString(R.string.home_info_version_unknown))
    }
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
    var klipyApiKey by remember { mutableStateOf(prefs.getString(UiPreferences.PREF_KLIPY_KEY, "") ?: "") }
    var giphyApiKey by remember { mutableStateOf(prefs.getString(UiPreferences.PREF_GIPHY_KEY, "") ?: "") }
    var showApiKey by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.more_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // --- Tools ---
        SectionHeader(stringResource(R.string.more_section_tools))
        ToolRow(Icons.Default.PlayArrow, stringResource(R.string.home_convert_button), onConvertClick)
        ToolRow(Icons.Default.Edit, stringResource(R.string.home_text_banner_button), onTextBannerClick)

        // --- Settings ---
        SectionHeader(stringResource(R.string.more_section_settings))
        Text(stringResource(R.string.home_settings_theme), style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Text(stringResource(R.string.home_settings_language), style = MaterialTheme.typography.bodyLarge)
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
        Text(stringResource(R.string.home_settings_grid_density), style = MaterialTheme.typography.bodyLarge)
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
        Text(stringResource(R.string.home_settings_interests), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(R.string.home_settings_interests_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        PredefinedInterests.groups.forEach { (group, tags) ->
            Text(
                text = stringResource(
                    when (group) {
                        "Hudba" -> R.string.home_interest_group_music
                        "Vizual" -> R.string.home_interest_group_visuals
                        "Fun" -> R.string.home_interest_group_fun
                        "Priroda" -> R.string.home_interest_group_nature
                        "Symboly" -> R.string.home_interest_group_symbols
                        else -> R.string.home_settings_interests
                    }
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 4.dp)
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
                                .putString(UiPreferences.KEY_USER_INTERESTS, updated.joinToString(","))
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
            Text(stringResource(R.string.home_settings_api_keys), style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = klipyApiKey,
            onValueChange = { newKey ->
                klipyApiKey = newKey
                prefs.edit().putString(UiPreferences.PREF_KLIPY_KEY, newKey).apply()
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
                prefs.edit().putString(UiPreferences.PREF_GIPHY_KEY, newKey).apply()
            },
            label = { Text(stringResource(R.string.home_settings_giphy_api_key)) },
            supportingText = { Text(stringResource(R.string.home_settings_giphy_api_key_hint)) },
            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // --- About ---
        SectionHeader(stringResource(R.string.more_section_about))
        Text(stringResource(R.string.home_info_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.home_info_version, appVersion),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun ToolRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun getAppVersion(context: Context, unknown: String): String {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        "$versionName ($versionCode)"
    } catch (e: PackageManager.NameNotFoundException) {
        unknown
    }
}
