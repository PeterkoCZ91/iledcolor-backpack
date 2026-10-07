package com.batoh.manager

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.batoh.core.common.UiPreferences
import com.batoh.core.common.UiThemeMode
import com.batoh.core.ui.theme.BatohTheme
import com.batoh.manager.ui.BatohApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val incomingGif: IncomingGifViewModel by viewModels()
    private var pendingSharedUri: android.net.Uri? = null
    // Set once the user refused the legacy permission; survives recreation so we do not ask again.
    private var legacyPermissionDenied = false
    private val legacyStoragePermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        val uri = pendingSharedUri
        pendingSharedUri = null
        legacyPermissionDenied = !granted
        if (granted && uri != null) incomingGif.importShared(uri, restoring = false)
        else incomingGif.reportError(getString(R.string.incoming_permission_required))
    }

    private fun importShared(intent: android.content.Intent, restoring: Boolean) {
        if (intent.action != android.content.Intent.ACTION_SEND) return
        val uri = if (android.os.Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)
        else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
        }
        val clipUri = intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        (uri ?: clipUri)?.let { shared ->
            if (android.os.Build.VERSION.SDK_INT < 29 && androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // After recreation the permission dialog result is redelivered for the restored
                // pending share; asking again would stack a second dialog.
                if (!(restoring && (pendingSharedUri != null || legacyPermissionDenied))) {
                    legacyPermissionDenied = false
                    pendingSharedUri = shared
                    legacyStoragePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            } else incomingGif.importShared(shared, restoring)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // A share waiting for the legacy storage permission must survive process death too.
        pendingSharedUri?.let { outState.putParcelable(KEY_PENDING_LEGACY_SHARE, it) }
        outState.putBoolean(KEY_LEGACY_PERMISSION_DENIED, legacyPermissionDenied)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        importShared(intent, restoring = false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        pendingSharedUri = savedInstanceState?.let {
            androidx.core.os.BundleCompat.getParcelable(it, KEY_PENDING_LEGACY_SHARE, android.net.Uri::class.java)
        }
        legacyPermissionDenied = savedInstanceState?.getBoolean(KEY_LEGACY_PERMISSION_DENIED) ?: false
        importShared(intent, restoring = savedInstanceState != null)
        setContent {
            val incoming by incomingGif.state.collectAsStateWithLifecycle()
            val prefs = remember {
                getSharedPreferences(UiPreferences.PREFS_NAME, MODE_PRIVATE)
            }

            var themeMode by remember {
                mutableStateOf(
                    runCatching {
                        UiThemeMode.valueOf(
                            prefs.getString(UiPreferences.KEY_THEME_MODE, UiThemeMode.SYSTEM.name)
                                ?: UiThemeMode.SYSTEM.name
                        )
                    }.getOrDefault(UiThemeMode.SYSTEM)
                )
            }
            var gridColumns by remember {
                mutableIntStateOf(
                    prefs.getInt(UiPreferences.KEY_GRID_COLUMNS, UiPreferences.DEFAULT_GRID_COLUMNS)
                        .coerceIn(2, 3)
                )
            }

            DisposableEffect(prefs) {
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    when (key) {
                        UiPreferences.KEY_THEME_MODE -> {
                            themeMode = runCatching {
                                UiThemeMode.valueOf(
                                    prefs.getString(UiPreferences.KEY_THEME_MODE, UiThemeMode.SYSTEM.name)
                                        ?: UiThemeMode.SYSTEM.name
                                )
                            }.getOrDefault(UiThemeMode.SYSTEM)
                        }
                        UiPreferences.KEY_GRID_COLUMNS -> {
                            gridColumns = prefs
                                .getInt(UiPreferences.KEY_GRID_COLUMNS, UiPreferences.DEFAULT_GRID_COLUMNS)
                                .coerceIn(2, 3)
                        }
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    prefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

            BatohTheme(
                darkTheme = when (themeMode) {
                    UiThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                    UiThemeMode.LIGHT -> false
                    UiThemeMode.DARK -> true
                }
            ) {
                BatohApp(
                    incomingGif = incoming,
                    onIncomingNavigationHandled = incomingGif::navigationHandled,
                    onDismissIncomingMessage = incomingGif::dismissMessage,
                    themeMode = themeMode,
                    gridColumns = gridColumns,
                    appLanguage = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                        .substringBefore(',')
                        .substringBefore('-')
                        .ifBlank { "system" },
                    onAppLanguageChanged = { languageTag ->
                        val locales = if (languageTag == "system") LocaleListCompat.getEmptyLocaleList()
                        else LocaleListCompat.forLanguageTags(languageTag)
                        AppCompatDelegate.setApplicationLocales(locales)
                    },
                    onThemeModeChanged = { selected ->
                        prefs.edit().putString(UiPreferences.KEY_THEME_MODE, selected.name).apply()
                        AppCompatDelegate.setDefaultNightMode(
                            when (selected) {
                                UiThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                                UiThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                                UiThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                            }
                        )
                    },
                    onGridColumnsChanged = { columns ->
                        prefs.edit()
                            .putInt(UiPreferences.KEY_GRID_COLUMNS, columns.coerceIn(2, 3))
                            .apply()
                    }
                )
            }
        }
    }

    private companion object {
        const val KEY_PENDING_LEGACY_SHARE = "pending_legacy_share"
        const val KEY_LEGACY_PERMISSION_DENIED = "legacy_permission_denied"
    }
}
