package com.batoh.manager

import android.app.Application
import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import androidx.appcompat.app.AppCompatDelegate
import com.batoh.core.common.UiPreferences
import com.batoh.core.common.UiThemeMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BatohApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        val prefs = getSharedPreferences(UiPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        val themeMode = runCatching {
            UiThemeMode.valueOf(
                prefs.getString(UiPreferences.KEY_THEME_MODE, UiThemeMode.SYSTEM.name)
                    ?: UiThemeMode.SYSTEM.name
            )
        }.getOrDefault(UiThemeMode.SYSTEM)

        AppCompatDelegate.setDefaultNightMode(
            when (themeMode) {
                UiThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                UiThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                UiThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            }
        )
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            // android.graphics.Movie crashes natively on malformed GIFs on newer devices.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(ImageDecoderDecoder.Factory())
            else add(GifDecoder.Factory())
        }
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(256L * 1024 * 1024)
                .build()
        }
        // Keep GIF thumbnails cached even when the server sends no-cache / short max-age headers.
        .respectCacheHeaders(false)
        .build()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
