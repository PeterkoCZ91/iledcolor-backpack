package com.batoh.core.storage.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

class MediaStoreHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun saveImage(filename: String, mimeType: String, inputStream: InputStream): Uri? = withContext(Dispatchers.IO) {
        val coroutine = kotlinx.coroutines.currentCoroutineContext()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GifPack")
            } else {
                @Suppress("DEPRECATION")
                val directory = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "GifPack")
                if (!directory.exists() && !directory.mkdirs()) throw IOException("Nelze vytvořit složku GifPack")
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATA, java.io.File(directory, filename).absolutePath)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(collection, contentValues)

        uri?.let {
            try {
                resolver.openOutputStream(it)?.use { outputStream ->
                    inputStream.use { input ->
                        input.copyToCancellable(outputStream, ensureActive = { coroutine.ensureActive() })
                    }
                } ?: throw IOException("Failed to open MediaStore output stream")
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(it, contentValues, null, null)
                }
                it
            } catch (e: Exception) {
                // Cleanup
                runCatching { resolver.delete(it, null, null) }
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
        }
    }
}
