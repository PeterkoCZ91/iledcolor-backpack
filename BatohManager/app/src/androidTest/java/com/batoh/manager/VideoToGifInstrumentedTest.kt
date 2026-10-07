package com.batoh.manager

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.batoh.core.conversion.SafeGifDecoder
import com.batoh.core.conversion.VideoToGifConverter
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises Android's real MediaMetadataRetriever and Bitmap stack with a generated MP4 fixture. */
@RunWith(AndroidJUnit4::class)
class VideoToGifInstrumentedTest {
    @Test
    fun convertsLocalMp4IntoAnimated64PixelGif() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val input = File(context.cacheDir, "video-to-gif-instrumented-test.mp4")
        try {
            testContext.assets.open("video_to_gif_sample.mp4").use { source ->
                input.outputStream().use(source::copyTo)
            }

            val progress = mutableListOf<Int>()
            val bytes = VideoToGifConverter(context, OkHttpClient()).convertFile(input) {
                progress += it
            }
            val gif = SafeGifDecoder.validate(bytes)

            assertEquals(64, gif.width)
            assertEquals(64, gif.height)
            assertTrue("Expected several decoded video frames, got ${gif.frameCount}", gif.frameCount >= 3)
            assertTrue("Expected a non-zero animation duration", gif.durationMs > 0)
            assertTrue("Conversion should report progress", progress.isNotEmpty())
            assertTrue("Progress should finish at 100", progress.last() == 100)
            assertTrue("Progress should be monotonic", progress.zipWithNext().all { (a, b) -> b >= a })
            assertTrue("Progress should stay within 0..100", progress.all { it in 0..100 })
        } finally {
            input.delete()
        }
    }
}
