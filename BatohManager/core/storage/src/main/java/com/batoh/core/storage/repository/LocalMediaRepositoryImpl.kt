package com.batoh.core.storage.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.batoh.core.common.Result
import com.batoh.core.domain.model.Gif
import com.batoh.core.domain.repository.LocalMediaRepository
import com.batoh.core.storage.util.MediaStoreHelper
import com.batoh.core.storage.worker.DownloadWorker
import com.batoh.core.domain.model.DownloadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import javax.inject.Inject
import java.util.UUID
import java.io.ByteArrayInputStream

class LocalMediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStoreHelper: MediaStoreHelper
) : LocalMediaRepository {

    private val workManager = WorkManager.getInstance(context)

    override fun getLocalGifs(): Flow<Result<List<Gif>>> = callbackFlow {
        var fetchJob: Job? = null

        fun scheduleFetch() {
            fetchJob?.cancel()
            fetchJob = launch {
                trySend(Result.Loading)
                fetchGifs()
            }
        }

        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scheduleFetch()
            }
        }
        
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
        
        // Initial fetch
        scheduleFetch()
        
        awaitClose {
            fetchJob?.cancel()
            context.contentResolver.unregisterContentObserver(observer)
        }
    }.conflate()

    private suspend fun ProducerScope<Result<List<Gif>>>.fetchGifs() {
        try {
            val gifs = withContext(Dispatchers.IO) {
                val result = mutableListOf<Gif>()
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.WIDTH,
                    MediaStore.Images.Media.HEIGHT
                )
                val selection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    "${MediaStore.Images.Media.MIME_TYPE} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} = ?"
                } else {
                    "${MediaStore.Images.Media.MIME_TYPE} = ? AND ${MediaStore.Images.Media.DATA} LIKE ?"
                }
                val selectionArgs = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    arrayOf("image/gif", "Pictures/GifPack/")
                } else {
                    @Suppress("DEPRECATION")
                    val directory = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                    arrayOf("image/gif", java.io.File(directory, "GifPack").absolutePath + "/%")
                }
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                    val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val name = cursor.getString(nameColumn) ?: "Unknown"
                        val width = cursor.getInt(widthColumn)
                        val height = cursor.getInt(heightColumn)

                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )

                        result.add(
                            Gif(
                                id = id.toString(),
                                title = name,
                                thumbnailUrl = contentUri.toString(),
                                originalUrl = contentUri.toString(),
                                width = width,
                                height = height
                            )
                        )
                    }
                }
                result
            }
            trySend(Result.Success(gifs))
        } catch (e: Exception) {
            trySend(Result.Error(e))
        }
    }

    override suspend fun saveGifBytes(bytes: ByteArray, title: String): Result<Uri> {
        return try {
            // Bytes are already a valid 64x64 animated GIF from VideoToGifConverter — save as-is
            val filename = "${title}_64x64_${System.currentTimeMillis()}.gif"
            val uri = mediaStoreHelper.saveImage(filename, "image/gif", ByteArrayInputStream(bytes))
            if (uri != null) Result.Success(uri)
            else Result.Error(Exception("Nepodařilo se uložit GIF"))
        } catch (e: Exception) {
            Result.Error(e, "Uložení selhalo: ${e.message}")
        }
    }

    override suspend fun importGif(uri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            require(uri.scheme == "content") { "GIF vyber z galerie nebo souborů" }
            val coroutine = kotlinx.coroutines.currentCoroutineContext()
            val bytes = GifImportReader.read(context.contentResolver.openInputStream(uri)) { coroutine.ensureActive() }
            GifImportReader.validate(bytes) { coroutine.ensureActive() }
            val displayName = runCatching {
                context.contentResolver.query(uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull() ?: "import"
            val safeName = displayName.substringBeforeLast('.').replace(Regex("[^\\p{L}\\p{N}_-]"), "_").take(48).ifBlank { "import" }
            val name = "${safeName}_${UUID.randomUUID().toString().take(8)}.gif"
            val copied = mediaStoreHelper.saveImage(name, "image/gif", ByteArrayInputStream(bytes))
                ?: error("GIF se nepodařilo uložit do knihovny")
            Result.Success(copied)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(e, "Import GIFu selhal: ${e.message ?: "soubor nelze načíst"}")
        }
    }

    override suspend fun saveGif(url: String, title: String): Result<String> {
        // Generate a unique ID for the file tracking
        val uniqueId = UUID.randomUUID().toString()

        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()
        
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(
                DownloadWorker.KEY_URL to url,
                DownloadWorker.KEY_TITLE to title,
                DownloadWorker.KEY_ID to uniqueId
            ))
            .build()
        
        workManager.enqueue(workRequest)
        return Result.Success(workRequest.id.toString())
    }

    override suspend fun deleteGif(gif: Gif): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val mediaId = gif.id.toLongOrNull()
                if (mediaId != null) {
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    )
                    val deleted = context.contentResolver.delete(contentUri, null, null)
                    if (deleted > 0) {
                        Result.Success(Unit)
                    } else {
                        Result.Error(Exception("GIF nenalezen v zařízení"))
                    }
                } else {
                    Result.Error(Exception("Neplatné ID GIFu"))
                }
            } catch (e: Exception) {
                Result.Error(e, "Smazání se nezdařilo: ${e.message}")
            }
        }
    }

    override suspend fun inspectGif(gif: Gif): com.batoh.core.domain.model.GifFileProblem? =
        withContext(Dispatchers.IO) {
            val coroutine = kotlinx.coroutines.currentCoroutineContext()
            val uri = Uri.parse(gif.originalUrl)
            GifFileInspector.inspect(open = { context.contentResolver.openInputStream(uri) }) {
                coroutine.ensureActive()
            }
        }

    override fun observeDownload(workId: String): Flow<DownloadStatus> {
        return try {
            val uuid = UUID.fromString(workId)
            workManager.getWorkInfoByIdFlow(uuid).map { workInfo ->
                if (workInfo == null) return@map DownloadStatus.UNKNOWN
                when (workInfo.state) {
                    androidx.work.WorkInfo.State.ENQUEUED -> DownloadStatus.PENDING
                    androidx.work.WorkInfo.State.RUNNING -> DownloadStatus.RUNNING
                    androidx.work.WorkInfo.State.SUCCEEDED -> DownloadStatus.SUCCESS
                    androidx.work.WorkInfo.State.FAILED -> DownloadStatus.FAILED
                    androidx.work.WorkInfo.State.BLOCKED -> DownloadStatus.PENDING
                    androidx.work.WorkInfo.State.CANCELLED -> DownloadStatus.FAILED
                }
            }
        } catch (e: Exception) {
            flow { emit(DownloadStatus.UNKNOWN) }
        }
    }
}
