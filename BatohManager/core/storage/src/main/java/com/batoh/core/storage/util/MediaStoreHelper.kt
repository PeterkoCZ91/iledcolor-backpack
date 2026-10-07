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

    /**
     * Saves the stream into the MediaStore. Any write/folder/publish failure is reported as a
     * [MediaStoreWriteException] (original error attached as suppressed, NOT as cause) so that
     * `classifyGifImportFailure` never mistakes a destination error for a revoked source grant.
     * Cancellation is rethrown unchanged; a partially written item is always deleted.
     */
    suspend fun saveImage(filename: String, mimeType: String, inputStream: InputStream): Uri? = withContext(Dispatchers.IO) {
        val coroutine = kotlinx.coroutines.currentCoroutineContext()
        try {
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
            val sink = object : PendingItemSink<Uri> {
                override fun insert(): Uri? = resolver.insert(collection, contentValues)
                override fun openOutput(item: Uri) = resolver.openOutputStream(item)
                override fun publish(item: Uri) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val published = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                        if (resolver.update(item, published, null, null) <= 0) {
                            throw IOException("MediaStore item could not be published")
                        }
                    }
                }
                override fun delete(item: Uri) {
                    resolver.delete(item, null, null)
                }
            }
            inputStream.writeToPendingItem(sink, ensureActive = { coroutine.ensureActive() })
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw MediaStoreWriteException("Uložení do MediaStore selhalo").also { it.addSuppressed(e) }
        }
    }
}

/** Destination-side save failure; deliberately has no cause so it is never classified as a source error. */
class MediaStoreWriteException(message: String) : RuntimeException(message)
