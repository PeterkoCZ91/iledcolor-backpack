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
import com.batoh.core.domain.model.GifDisplayName
import com.batoh.core.domain.model.GifNameValidation
import com.batoh.core.domain.model.GifRenameException
import com.batoh.core.domain.model.GifRenameFailure
import com.batoh.core.domain.model.LibraryEntry
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
import kotlinx.coroutines.NonCancellable
import javax.inject.Inject
import java.util.UUID
import java.io.ByteArrayInputStream

class LocalMediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStoreHelper: MediaStoreHelper
) : LocalMediaRepository {

    private val workManager = WorkManager.getInstance(context)

    override fun getLocalGifs(): Flow<Result<List<Gif>>> = getLibraryEntries().map { result ->
        when (result) {
            is Result.Success -> Result.Success(result.data.map { it.gif })
            is Result.Error -> result
            Result.Loading -> Result.Loading
        }
    }

    override fun getLibraryEntries(): Flow<Result<List<LibraryEntry>>> = callbackFlow {
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

    private suspend fun ProducerScope<Result<List<LibraryEntry>>>.fetchGifs() {
        try {
            val gifs = withContext(Dispatchers.IO) {
                val result = mutableListOf<LibraryEntry>()
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.WIDTH,
                    MediaStore.Images.Media.HEIGHT,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.DATE_ADDED
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
                    val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                    val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

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
                            LibraryEntry(
                                gif = Gif(
                                    id = id.toString(),
                                    title = name,
                                    thumbnailUrl = contentUri.toString(),
                                    originalUrl = contentUri.toString(),
                                    width = width,
                                    height = height
                                ),
                                sizeBytes = if (cursor.isNull(sizeColumn)) 0L else cursor.getLong(sizeColumn),
                                dateAddedSeconds = if (cursor.isNull(dateColumn)) 0L else cursor.getLong(dateColumn)
                            )
                        )
                    }
                }
                result
            }
            trySend(Result.Success(gifs))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
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
            else Result.Error(Exception("Failed to save GIF"))
        } catch (e: Exception) {
            Result.Error(e, "Saving failed: ${e.message}")
        }
    }

    override suspend fun importGif(uri: Uri): Result<Uri> {
        val prepared = try {
            withContext(Dispatchers.IO) {
                require(uri.scheme == "content") { "Select the GIF from the gallery or files" }
                val coroutine = kotlinx.coroutines.currentCoroutineContext()
                // Open inside read() so a revoked grant / missing source is classified, not thrown raw
                val bytes = GifImportReader.read({ context.contentResolver.openInputStream(uri) }) { coroutine.ensureActive() }
                GifImportReader.validate(bytes) { coroutine.ensureActive() }
                val displayName = runCatching {
                    context.contentResolver.query(uri,
                        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                }.getOrNull() ?: "import"
                val safeName = displayName.substringBeforeLast('.').replace(Regex("[^\\p{L}\\p{N}_-]"), "_").take(48).ifBlank { "import" }
                bytes to "${safeName}_${UUID.randomUUID().toString().take(8)}.gif"
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.Error(e, "GIF import failed: ${e.message ?: "file cannot be read"}")
        }
        val (bytes, name) = prepared
        // Cancellation before anything is stored: nothing pending exists yet, just stop.
        kotlin.coroutines.coroutineContext.ensureActive()
        // From here the commit is not cancellable: a cancel racing with a successful save must not
        // swallow the URI (the file would be stored while the caller retries -> duplicate).
        return commitIgnoringCancellation {
            try {
                val copied = mediaStoreHelper.saveImage(name, "image/gif", ByteArrayInputStream(bytes))
                    ?: error("Failed to save GIF to the library")
                Result.Success(copied)
            } catch (e: Exception) {
                Result.Error(e, "GIF import failed: ${e.message ?: "file cannot be read"}")
            }
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
                        Result.Error(Exception("GIF not found on the device"))
                    }
                } else {
                    Result.Error(Exception("Invalid GIF ID"))
                }
            } catch (e: Exception) {
                Result.Error(e, "Deletion failed: ${e.message}")
            }
        }
    }

    override suspend fun renameGif(gif: Gif, newName: String): Result<String> = withContext(Dispatchers.IO) {
        var renamedFiles: Pair<java.io.File, java.io.File>? = null
        try {
            val fileName = when (val validation = GifDisplayName.validate(newName)) {
                is GifNameValidation.Valid -> validation.fileName
                is GifNameValidation.Invalid -> throw GifRenameException(GifRenameFailure.INVALID_NAME)
            }
            val mediaId = gif.id.toLongOrNull() ?: throw GifRenameException(GifRenameFailure.NOT_FOUND)
            val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)
            val resolver = context.contentResolver
            val modernStorage = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
            @Suppress("DEPRECATION")
            val locationColumn = if (modernStorage) MediaStore.Images.Media.RELATIVE_PATH else MediaStore.Images.Media.DATA
            val row = resolver.query(contentUri,
                arrayOf(MediaStore.Images.Media.DISPLAY_NAME, locationColumn), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) to cursor.getString(1) else null
            } ?: throw GifRenameException(GifRenameFailure.NOT_FOUND)
            val (currentName, location) = row
            @Suppress("DEPRECATION")
            val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            val inCollection = if (modernStorage) GifPackLocation.isCollectionRelativePath(location)
                else GifPackLocation.isCollectionFilePath(location, picturesDir.absolutePath)
            if (!inCollection) throw GifRenameException(GifRenameFailure.NOT_IN_COLLECTION)
            if (currentName == fileName) return@withContext Result.Success(fileName)

            // MediaStore would silently add " (1)" on a clash; refuse instead so the user sees why.
            // The file system is case-insensitive for our purposes, so compare names ignoring case in Kotlin.
            val folderSelection = if (modernStorage) "$locationColumn = ?" else "$locationColumn LIKE ? ESCAPE '\\'"
            val folderArg = if (modernStorage) location else GifRenameRules.legacyFolderLikePattern(location)
            val siblings = mutableListOf<GifRenameRules.Sibling>()
            resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, locationColumn),
                folderSelection, arrayOf(folderArg), null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    siblings += GifRenameRules.Sibling(cursor.getLong(0), cursor.getString(1), cursor.getString(2))
                }
            }
            val folder = if (modernStorage) location else java.io.File(location).parent.orEmpty()
            if (GifRenameRules.isNameTaken(siblings, mediaId, fileName, folder, modernStorage)) {
                throw GifRenameException(GifRenameFailure.NAME_TAKEN)
            }

            val values = android.content.ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            }
            if (!modernStorage) {
                // Before Android 10 DISPLAY_NAME is only a label; move the file and its DATA path too.
                val oldFile = java.io.File(location)
                val newFile = java.io.File(oldFile.parentFile, fileName)
                if (newFile.exists()) throw GifRenameException(GifRenameFailure.NAME_TAKEN)
                if (!oldFile.renameTo(newFile)) throw GifRenameException(GifRenameFailure.NOT_CHANGED)
                @Suppress("DEPRECATION")
                values.put(MediaStore.Images.Media.DATA, newFile.absolutePath)
                renamedFiles = oldFile to newFile
            }
            val updated = resolver.update(contentUri, values, null, null)
            if (updated <= 0) throw GifRenameException(GifRenameFailure.NOT_CHANGED)
            renamedFiles = null
            Result.Success(fileName)
        } catch (e: kotlinx.coroutines.CancellationException) {
            rollbackLegacyRename(renamedFiles)
            throw e
        } catch (e: GifRenameException) {
            rollbackLegacyRename(renamedFiles)
            Result.Error(e, e.message)
        } catch (e: Exception) {
            // The file was moved but MediaStore update failed: put it back and report a rename failure.
            val rolledBack = renamedFiles != null
            rollbackLegacyRename(renamedFiles)
            val mapped = if (rolledBack) GifRenameException(GifRenameFailure.NOT_CHANGED) else e
            Result.Error(mapped, mapped.message)
        }
    }

    /** Undo the pre-MediaStore file move (API 26-28) so file and DB row never diverge. */
    private fun rollbackLegacyRename(moved: Pair<java.io.File, java.io.File>?) {
        if (moved == null) return
        runCatching { moved.second.renameTo(moved.first) }
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

/** Runs [block] to completion and returns its value even if the caller was cancelled meanwhile. */
internal suspend fun <T> commitIgnoringCancellation(block: suspend () -> T): T {
    // withContext drops the result with a CancellationException when the caller was cancelled
    // meanwhile, so keep the value ourselves and hand it back if the block really finished.
    var finished = false
    var value: T? = null
    try {
        return withContext(Dispatchers.IO + NonCancellable) {
            block().also { value = it; finished = true }
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        if (finished) {
            @Suppress("UNCHECKED_CAST")
            return value as T
        }
        throw e
    }
}

/** Pure rules for the rename name-clash check (testable on the JVM). */
internal object GifRenameRules {
    data class Sibling(val id: Long, val displayName: String?, val location: String?)

    /** Escapes LIKE wildcards (`%`, `_`) and the escape char `\` itself; use with `ESCAPE '\'`. */
    fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    /** Legacy (DATA) pattern matching files directly or deeper below [filePath]'s folder. */
    fun legacyFolderLikePattern(filePath: String): String =
        escapeLike(java.io.File(filePath).parent.orEmpty()) + "/%"

    /**
     * True when another row (not [selfId]) in exactly [folder] already has [newName], ignoring case.
     * Modern: [Sibling.location] is the RELATIVE_PATH; legacy: the full DATA path, whose parent must equal [folder]
     * (so sub-folders do not count).
     */
    fun isNameTaken(siblings: List<Sibling>, selfId: Long, newName: String, folder: String, modern: Boolean): Boolean =
        siblings.any { row ->
            row.id != selfId &&
                row.displayName?.equals(newName, ignoreCase = true) == true &&
                row.location != null &&
                if (modern) row.location == folder else java.io.File(row.location).parent == folder
        }
}
