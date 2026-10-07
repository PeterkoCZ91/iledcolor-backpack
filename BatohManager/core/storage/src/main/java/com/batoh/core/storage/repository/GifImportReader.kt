package com.batoh.core.storage.repository

import com.batoh.core.conversion.GifInfo
import com.batoh.core.conversion.SafeGifDecoder
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CancellationException

/** Why reading a picked or shared GIF failed, independent of the UI language. */
enum class GifImportFailure {
    /** The URI grant was revoked or expired (for example after process death). */
    PermissionRevoked,
    /** The source document was deleted, moved or the provider returned no stream. */
    SourceMissing,
    /** The provider failed or disappeared while the bytes were being read. */
    SourceUnreadable,
    TooLarge,
    Corrupt
}

/**
 * Typed import failure. It extends [IllegalArgumentException] so existing callers that treat
 * invalid input as IAE keep working; the [reason] lets the UI show a precise message.
 */
class GifImportException(
    val reason: GifImportFailure,
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

/**
 * Maps an import error (including wrapped causes) to a [GifImportFailure], or null when the
 * failure did not come from the import source (for example a MediaStore write error).
 */
fun classifyGifImportFailure(error: Throwable): GifImportFailure? {
    var current: Throwable? = error
    val seen = HashSet<Throwable>()
    while (current != null && seen.add(current)) {
        when {
            current is GifImportException -> return current.reason
            current is CancellationException -> return null
            current is SecurityException -> return GifImportFailure.PermissionRevoked
            current is FileNotFoundException -> return GifImportFailure.SourceMissing
            // MediaStore write errors arrive as MediaStoreWriteException (no cause), so a raw IOException
            // reaching the import caller comes from opening/reading the source.
            current is IOException -> return GifImportFailure.SourceUnreadable
            current is IllegalStateException && current.isFromContentProvider() ->
                return GifImportFailure.SourceUnreadable
        }
        current = current.cause
    }
    return null
}

/** Provider-side IllegalStateExceptions surface through ContentResolver/database frames. */
private fun Throwable.isFromContentProvider(): Boolean = stackTrace.any { frame ->
    frame.className.startsWith("android.content.ContentResolver") ||
        frame.className.startsWith("android.content.ContentProvider") ||
        frame.className.startsWith("android.database.")
}

/** Bounded, cancellable byte reader shared by the Gallery and ACTION_SEND import paths. */
internal object GifImportReader {
    /**
     * Opens the source via [open] (typically `contentResolver.openInputStream(uri)`) and reads it.
     * A revoked grant or a vanished document is reported as a typed [GifImportException].
     */
    fun read(open: () -> InputStream?, ensureActive: () -> Unit): ByteArray {
        ensureActive()
        val stream = try {
            open()
        } catch (e: Exception) {
            throw sourceFailure(e, opening = true)
        }
        return read(stream, ensureActive)
    }

    fun read(input: InputStream?, ensureActive: () -> Unit): ByteArray {
        val source = input ?: throw GifImportException(
            GifImportFailure.SourceMissing,
            "Vybraný GIF nelze načíst"
        )
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var failure: Throwable? = null
        try {
            while (true) {
                ensureActive()
                val count = try {
                    source.read(buffer)
                } catch (e: Exception) {
                    throw sourceFailure(e, opening = false)
                }
                if (count < 0) break
                if (output.size().toLong() + count > SafeGifDecoder.MAX_BYTES) {
                    throw GifImportException(GifImportFailure.TooLarge, "GIF je příliš velký (maximum 20 MB)")
                }
                output.write(buffer, 0, count)
            }
        } catch (e: Throwable) {
            failure = e
            throw e
        } finally {
            try {
                source.close()
            } catch (closeError: Exception) {
                // A close failure after a complete read means the provider broke; never hide it,
                // but do not mask an earlier, more specific failure either.
                if (failure == null) throw sourceFailure(closeError, opening = false)
                failure.addSuppressed(closeError)
            }
        }
        return output.toByteArray()
    }

    fun validate(bytes: ByteArray, ensureActive: () -> Unit): GifInfo = try {
        SafeGifDecoder.validate(bytes, ensureActive)
    } catch (e: IllegalArgumentException) {
        throw GifImportException(
            GifImportFailure.Corrupt,
            "GIF je poškozený nebo překračuje podporované limity",
            e
        )
    }

    private fun sourceFailure(error: Exception, opening: Boolean): Exception {
        if (error is CancellationException || error is GifImportException) return error
        val reason = when (error) {
            is SecurityException -> GifImportFailure.PermissionRevoked
            is FileNotFoundException -> GifImportFailure.SourceMissing
            else -> GifImportFailure.SourceUnreadable
        }
        val message = when (reason) {
            GifImportFailure.PermissionRevoked -> "Přístup ke sdílenému GIFu byl odebrán"
            GifImportFailure.SourceMissing -> "Sdílený GIF už neexistuje"
            else -> if (opening) "Sdílený GIF nelze otevřít" else "Čtení sdíleného GIFu bylo přerušeno"
        }
        return GifImportException(reason, message, error)
    }
}
