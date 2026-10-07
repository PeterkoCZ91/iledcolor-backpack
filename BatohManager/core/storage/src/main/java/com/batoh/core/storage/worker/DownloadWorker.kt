package com.batoh.core.storage.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.batoh.core.conversion.SafeGifDecoder
import com.batoh.core.storage.util.MediaStoreHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mediaStoreHelper: MediaStoreHelper,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_URL = "url"
        const val KEY_TITLE = "title"
        const val KEY_ID = "id"
        const val KEY_RESULT_URI = "uri"
    }

    override suspend fun doWork(): Result {
        val urlString = inputData.getString(KEY_URL) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "downloaded_gif"
        val id = inputData.getString(KEY_ID) ?: System.currentTimeMillis().toString()
        
        return try {
            val request = Request.Builder().url(urlString).build()
            val gifBytes = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful || response.body == null) {
                    return Result.failure()
                }
                val body = response.body!!
                require(body.contentLength() <= SafeGifDecoder.MAX_BYTES) { "GIF is too large" }
                body.byteStream().use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    val taskContext = currentCoroutineContext()
                    while (true) {
                        taskContext.ensureActive()
                        val size = input.read(buffer)
                        if (size < 0) break
                        require(output.size().toLong() + size <= SafeGifDecoder.MAX_BYTES) { "GIF is too large" }
                        output.write(buffer, 0, size)
                    }
                    output.toByteArray()
                }
            }
            
            // Keep the original animation; prepare a 64×64 copy in the editor/upload.
            val taskContext = currentCoroutineContext()
            SafeGifDecoder.validate(gifBytes) { taskContext.ensureActive() }

            val safeTitle = title.replace(Regex("[^a-zA-Z0-9]"), "_").take(20)
            val filename = "${id}_${safeTitle}.gif"

            val uri = mediaStoreHelper.saveImage(filename, "image/gif", ByteArrayInputStream(gifBytes))
            
            if (uri != null) {
                Result.success(workDataOf(KEY_RESULT_URI to uri.toString()))
            } else {
                Result.failure()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            e.printStackTrace()
            Result.retry()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
