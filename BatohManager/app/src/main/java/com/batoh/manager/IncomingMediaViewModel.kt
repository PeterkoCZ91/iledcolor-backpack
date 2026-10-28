package com.batoh.manager

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batoh.core.common.Result
import com.batoh.core.conversion.VideoToGifConverter
import com.batoh.core.conversion.VideoTooLargeException
import com.batoh.core.domain.usecase.SaveGifBytesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Shared video or still image on its way to the one send flow.
 * [sendUri] is set once the item is ready to send; [messageRes] is progress or an error.
 */
data class IncomingMediaState(
    val event: Long = 0,
    @StringRes val messageRes: Int? = null,
    val sendUri: String? = null
)

/**
 * Handles `video` MIME types, `image/png` and `image/jpeg` shares. The shared stream is copied into the app's
 * cache straight away (this is the read-grant retention: the grant of a share only lives as long as
 * the receiving task), then a video is converted to a GIF and saved, while a still image is handed on
 * as it is (the send flow turns PNG/JPEG into a one-frame GIF). The latest share wins.
 */
@HiltViewModel
class IncomingMediaViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val converter: VideoToGifConverter,
    private val saveGifBytes: SaveGifBytesUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(IncomingMediaState())
    val state = _state.asStateFlow()
    private var job: Job? = null

    fun submitVideo(uri: Uri) = submit(uri, MAX_VIDEO_BYTES, "mp4") { file ->
        _state.value = IncomingMediaState(System.nanoTime(), R.string.incoming_video_converting)
        val bytes = converter.convertFile(file)
        when (val saved = saveGifBytes(bytes, "gif_${System.currentTimeMillis()}")) {
            is Result.Success -> saved.data.toString()
            else -> throw IOException("Converted GIF could not be saved")
        }
    }

    fun submitImage(uri: Uri) = submit(uri, MAX_IMAGE_BYTES, "img", keepFile = true) { file ->
        Uri.fromFile(file).toString()
    }

    private fun submit(
        uri: Uri,
        limit: Long,
        extension: String,
        keepFile: Boolean = false,
        process: suspend (File) -> String
    ) {
        job?.cancel()
        _state.value = IncomingMediaState(System.nanoTime(), R.string.incoming_media_preparing)
        job = viewModelScope.launch {
            var file: File? = null
            try {
                val sendUri = withContext(Dispatchers.IO) {
                    val copy = copyToCache(uri, limit, extension).also { file = it }
                    process(copy)
                }
                _state.value = IncomingMediaState(System.nanoTime(), null, sendUri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // OutOfMemoryError on a huge video is not an Exception.
                android.util.Log.w("IncomingMedia", "shared item failed: ${e.javaClass.simpleName}")
                val tooLarge = e is VideoTooLargeException || e is OutOfMemoryError ||
                    (e is IOException && e.message == TOO_LARGE)
                _state.value = IncomingMediaState(
                    System.nanoTime(),
                    if (tooLarge) R.string.incoming_media_too_large else R.string.incoming_media_failed
                )
            } finally {
                // A still image stays in the cache until the send has read it; pruned on a later share.
                if (!keepFile) file?.delete()
            }
        }
    }

    private fun copyToCache(uri: Uri, limit: Long, extension: String): File {
        val dir = File(appContext.cacheDir, "shared_media").apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - STALE_MS
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        val target = File(dir, "share_${System.nanoTime()}.$extension")
        try {
            val input = appContext.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open shared item")
            input.use { source ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val n = source.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > limit) throw IOException(TOO_LARGE)
                        out.write(buffer, 0, n)
                    }
                }
            }
        } catch (e: Throwable) {
            target.delete()
            throw e
        }
        return target
    }

    /** The UI has navigated to [sendUri] (or shown the failure); drop the one-shot parts of the state. */
    fun handled(event: Long) {
        if (_state.value.event == event) _state.value = _state.value.copy(sendUri = null, messageRes = null)
    }

    fun dismissMessage() { _state.value = _state.value.copy(messageRes = null) }

    private companion object {
        const val MAX_VIDEO_BYTES = 100L * 1024 * 1024
        const val MAX_IMAGE_BYTES = 20L * 1024 * 1024
        const val STALE_MS = 24L * 60 * 60 * 1000
        const val TOO_LARGE = "Shared item is too large"
    }
}
