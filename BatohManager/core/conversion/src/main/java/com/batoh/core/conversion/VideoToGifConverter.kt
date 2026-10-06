package com.batoh.core.conversion

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads an MP4 from a URL and converts it to an animated GIF
 * at 64×64 pixels — matching the LED backpack display resolution.
 */
@Singleton
class VideoToGifConverter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        const val TARGET_SIZE = 64       // backpack display resolution
        const val FRAME_DELAY_MS = 200   // 5 fps — matches iledeyes (200 ms/frame)
        const val MAX_FRAMES = 30        // cap to keep GIF small
        private const val NQ_SAMPLE = 10 // NeuQuant quality factor
    }

    /**
     * Downloads an MP4 from [url], extracts frames, scales to 64×64,
     * encodes as GIF and returns raw bytes.
     *
     * [onProgress] is called with 0–100 as work advances:
     *   0–50 = download, 50–100 = conversion.
     */
    suspend fun convertFromUrl(
        url: String,
        onProgress: suspend (Int) -> Unit = {}
    ): ByteArray = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "tmp_mp4_${System.currentTimeMillis()}.mp4")
        try {
            downloadToFile(url, tempFile) { dl ->
                // map download progress 0-100 → 0-50
                onProgress(dl / 2)
            }
            onProgress(50)
            convertFile(tempFile) { cv ->
                // map conversion progress 0-100 → 50-100
                onProgress(50 + cv / 2)
            }
        } finally {
            tempFile.delete()
        }
    }

    /** Convert a local MP4 [file] to GIF bytes. */
    suspend fun convertFile(
        file: File,
        onProgress: suspend (Int) -> Unit = {}
    ): ByteArray = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            extractAndEncode(retriever, onProgress)
        } finally {
            retriever.release()
        }
    }

    /** Convert a content/file [uri] (e.g. from picker) to GIF bytes. */
    suspend fun convertUri(
        uri: Uri,
        onProgress: suspend (Int) -> Unit = {}
    ): ByteArray = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            extractAndEncode(retriever, onProgress)
        } finally {
            retriever.release()
        }
    }

    /** Safely decode and prepare every GIF frame without entering the native Movie decoder. */
    suspend fun convertGifBytes(
        gifBytes: ByteArray,
        onProgress: suspend (Int) -> Unit = {}
    ): ByteArray = withContext(Dispatchers.IO) {
        val coroutineContext = currentCoroutineContext()
        onProgress(0)
        val result = GifEditorProcessor.transform(gifBytes) { coroutineContext.ensureActive() }
        onProgress(100)
        result.gifBytes
    }

    // ---- private ----

    private suspend fun extractAndEncode(
        retriever: MediaMetadataRetriever,
        onProgress: suspend (Int) -> Unit
    ): ByteArray {
        val durationMs = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 1000L

        val frameCount = minOf(MAX_FRAMES, maxOf(1, (durationMs / FRAME_DELAY_MS).toInt()))
        val intervalUs = if (frameCount > 1) durationMs * 1000L / frameCount else 0L

        val output = ByteArrayOutputStream()
        val encoder = GifEncoder().apply {
            setSize(TARGET_SIZE, TARGET_SIZE)
            setRepeat(0)
            // Real sampling interval (see convertGifBytes) — long videos capped by
            // MAX_FRAMES would otherwise play back faster than the source
            setDelay(if (intervalUs > 0) (intervalUs / 1000L).toInt() else FRAME_DELAY_MS)
            start(output)
        }

        var encodedFrames = 0
        for (i in 0 until frameCount) {
            currentCoroutineContext().ensureActive() // stop extracting when the caller is cancelled
            val timeUs = i * intervalUs
            val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            if (frame != null) {
                val cropped = centerCropToSquare(frame)
                val scaled = Bitmap.createScaledBitmap(cropped, TARGET_SIZE, TARGET_SIZE, true)
                check(encoder.addFrame(scaled)) { "Snímek videa nelze převést" }
                encodedFrames++
                scaled.recycle()
                if (cropped !== frame) cropped.recycle()
                frame.recycle()
            }
            onProgress((i + 1) * 100 / frameCount)
        }

        require(encodedFrames > 0) { "Video neobsahuje čitelné snímky" }
        check(encoder.finish()) { "GIF nelze dokončit" }
        return output.toByteArray()
    }

    private fun centerCropToSquare(bitmap: Bitmap): Bitmap {
        if (bitmap.width == bitmap.height) return bitmap
        val size = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        return Bitmap.createBitmap(bitmap, x, y, size, size)
    }

    private suspend fun downloadToFile(url: String, dest: File, onProgress: suspend (Int) -> Unit) = withContext(Dispatchers.IO) {
        val request = okhttp3.Request.Builder().url(url).build()
        okHttpClient.newCall(request).awaitResponse().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed: ${response.code}")
            val body = response.body ?: throw IOException("Empty response")
            val contentLength = body.contentLength()
            require(contentLength <= 100L * 1024 * 1024) { "Video je příliš velké (maximum 100 MB)" }
            body.byteStream().use { input ->
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(8_192)
                    var downloaded = 0L
                    var bytes: Int
                    while (input.read(buffer).also { bytes = it } != -1) {
                        currentCoroutineContext().ensureActive()
                        require(downloaded + bytes <= 100L * 1024 * 1024) { "Video je příliš velké (maximum 100 MB)" }
                        output.write(buffer, 0, bytes)
                        downloaded += bytes
                        if (contentLength > 0) {
                            onProgress((downloaded * 100 / contentLength).toInt())
                        }
                    }
                }
            }
        }
    }
}
