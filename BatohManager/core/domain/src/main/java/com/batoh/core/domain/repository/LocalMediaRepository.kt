package com.batoh.core.domain.repository

import android.net.Uri
import com.batoh.core.common.Result
import com.batoh.core.domain.model.Gif
import com.batoh.core.domain.model.GifFileProblem
import kotlinx.coroutines.flow.Flow

import com.batoh.core.domain.model.DownloadStatus

interface LocalMediaRepository {
    fun getLocalGifs(): Flow<Result<List<Gif>>>
    suspend fun saveGif(url: String, title: String): Result<String> // Returns Work UUID string
    suspend fun saveGifBytes(bytes: ByteArray, title: String): Result<Uri>
    suspend fun importGif(uri: Uri): Result<Uri>
    fun observeDownload(workId: String): Flow<DownloadStatus>
    suspend fun deleteGif(gif: Gif): Result<Unit>
    /** Reads the library file with the bounded GIF parser; null = valid for editing and upload. */
    suspend fun inspectGif(gif: Gif): GifFileProblem?
}
