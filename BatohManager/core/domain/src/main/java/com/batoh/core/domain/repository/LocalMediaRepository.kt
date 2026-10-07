package com.batoh.core.domain.repository

import android.net.Uri
import com.batoh.core.common.Result
import com.batoh.core.domain.model.Gif
import com.batoh.core.domain.model.GifFileProblem
import com.batoh.core.domain.model.GifRenameException
import com.batoh.core.domain.model.LibraryEntry
import kotlinx.coroutines.flow.Flow

import com.batoh.core.domain.model.DownloadStatus

interface LocalMediaRepository {
    fun getLocalGifs(): Flow<Result<List<Gif>>>
    /** Same items as [getLocalGifs] (newest first) with file size and date added. */
    fun getLibraryEntries(): Flow<Result<List<LibraryEntry>>>
    suspend fun saveGif(url: String, title: String): Result<String> // Returns Work UUID string
    suspend fun saveGifBytes(bytes: ByteArray, title: String): Result<Uri>
    suspend fun importGif(uri: Uri): Result<Uri>
    fun observeDownload(workId: String): Flow<DownloadStatus>
    suspend fun deleteGif(gif: Gif): Result<Unit>
    /** Reads the library file with the bounded GIF parser; null = valid for editing and upload. */
    suspend fun inspectGif(gif: Gif): GifFileProblem?
    /**
     * Renames a collection file in `Pictures/GifPack` (MediaStore DISPLAY_NAME, `.gif` kept).
     * Returns the new display name. Expected failures come as [GifRenameException]; a
     * [SecurityException] (possibly recoverable) is passed through unchanged so the caller
     * can ask the user for write access.
     */
    suspend fun renameGif(gif: Gif, newName: String): Result<String>
}
