package com.batoh.feature.backpack

import com.batoh.core.conversion.GifEditOptions
import com.batoh.core.conversion.GifEditorProcessor
import com.batoh.core.conversion.SafeGifDecoder
import com.batoh.core.data.bluetooth.BackpackPayload
import com.batoh.core.data.bluetooth.PanelFamily
import com.batoh.core.data.bluetooth.PanelSpec
import com.batoh.core.data.bluetooth.UploadFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Turns a picked GIF into what a panel family receives; pure, so both paths are unit-testable. */
internal object PanelUploadPrep {
    /**
     * Validates [gif] and scales it to 64x64 exactly like a single upload ([convertTo64] for other sizes),
     * then wraps it into a programme payload. Throws [UploadFailureException] with a typed failure.
     */
    suspend fun programme(gif: ByteArray, convertTo64: suspend (ByteArray) -> ByteArray): PreparedProgramme {
        val dimensions = BackpackPayload.gifSize(gif) ?: throw UploadFailureException(UploadFailure.NotAGif)
        var frames = 0
        val converted = try {
            val taskContext = currentCoroutineContext()
            frames = SafeGifDecoder.validate(gif) { taskContext.ensureActive() }.frameCount
            if (dimensions == (64 to 64)) gif else convertTo64(gif)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw UploadFailureException(UploadFailure.GifInvalid, e)
        }
        return PreparedProgramme(payload { BackpackPayload.fromGif(converted) }, frames)
    }

    /**
     * Backpack: the programme payload of [programme]. iPixel: the GIF itself, validated and,
     * when its size differs from [spec], re-rendered at the panel size with the default scale mode.
     */
    suspend fun prepare(gif: ByteArray, spec: PanelSpec, convertTo64: suspend (ByteArray) -> ByteArray): ByteArray {
        if (spec.family == PanelFamily.Backpack) return programme(gif, convertTo64).payload
        val (width, height) = BackpackPayload.gifSize(gif) ?: throw UploadFailureException(UploadFailure.NotAGif)
        return try {
            val taskContext = currentCoroutineContext()
            SafeGifDecoder.validate(gif) { taskContext.ensureActive() }
            if (width == spec.width && height == spec.height) gif else GifEditorProcessor.transform(
                gif,
                GifEditOptions(scaleMode = GifEditorProcessor.defaultScaleMode(width, height, spec.width, spec.height)),
                spec.width, spec.height
            ) { taskContext.ensureActive() }.gifBytes
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw UploadFailureException(UploadFailure.GifInvalid, e)
        }
    }

    /**
     * Programme payload of a ready GIF (editor output, text banner) built before connecting, or null to
     * build it once the panel is Ready: an iPixel link needs none, and a GIF rendered at another size
     * (for an iPixel panel) is meant for a target that is not known yet.
     */
    fun eagerProgramme(gif: ByteArray, ipixelLink: Boolean, playback: EditorPlaybackOptions?): ByteArray? {
        if (ipixelLink || BackpackPayload.gifSize(gif) != (64 to 64)) return null
        return playback?.buildPayload(gif) ?: BackpackPayload.fromGif(gif)
    }

    /** Backpack programme payload of a ready GIF; a size other than 64x64 is scaled like a single upload first. */
    suspend fun readyGifProgramme(
        gif: ByteArray,
        playback: EditorPlaybackOptions?,
        convertTo64: suspend (ByteArray) -> ByteArray
    ): ByteArray {
        val sized = if (BackpackPayload.gifSize(gif) == (64 to 64)) gif else try {
            convertTo64(gif)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw UploadFailureException(UploadFailure.GifInvalid, e)
        }
        return payload { playback?.buildPayload(sized) ?: BackpackPayload.fromGif(sized) }
    }

    inline fun payload(block: () -> ByteArray): ByteArray = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw UploadFailureException(UploadFailure.PayloadInvalid, e)
    }
}
