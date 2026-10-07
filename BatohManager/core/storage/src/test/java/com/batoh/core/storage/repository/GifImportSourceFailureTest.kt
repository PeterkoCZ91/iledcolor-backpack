package com.batoh.core.storage.repository

import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Simulates ContentResolver failures around a shared or picked GIF without Android. */
class GifImportSourceFailureTest {
    /** Stand-in for ContentResolver.openInputStream with a scripted outcome. */
    private class FakeResolver(private val opener: () -> InputStream?) {
        var opened = 0
        fun openInputStream(): InputStream? { opened++; return opener() }
    }

    /** Provider stream that serves [goodBytes] and then fails like a revoked/dead provider. */
    private class FailingStream(private val goodBytes: Int, private val failure: Exception) : InputStream() {
        private var served = 0
        var closed = false
        override fun read(): Int = throw UnsupportedOperationException()
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (served >= goodBytes) throw failure
            val count = minOf(len, goodBytes - served)
            served += count
            return count
        }
        override fun close() { closed = true }
    }

    @Test
    fun revokedGrantBeforeOpenIsReportedAsPermissionRevoked() {
        val resolver = FakeResolver { throw SecurityException("Permission Denial: opening provider") }

        val error = assertThrows(GifImportException::class.java) {
            GifImportReader.read({ resolver.openInputStream() }) {}
        }

        assertEquals(GifImportFailure.PermissionRevoked, error.reason)
        assertTrue(error.cause is SecurityException)
        assertEquals(1, resolver.opened)
        assertEquals(GifImportFailure.PermissionRevoked, classifyGifImportFailure(error))
    }

    @Test
    fun deletedSourceBeforeOpenIsReportedAsMissing() {
        val error = assertThrows(GifImportException::class.java) {
            GifImportReader.read({ throw FileNotFoundException("No such document") }) {}
        }
        assertEquals(GifImportFailure.SourceMissing, error.reason)
    }

    @Test
    fun providerReturningNoStreamIsReportedAsMissing() {
        val error = assertThrows(GifImportException::class.java) {
            GifImportReader.read({ null }) {}
        }
        assertEquals(GifImportFailure.SourceMissing, error.reason)
    }

    @Test
    fun cancelledImportDoesNotOpenTheSource() {
        val resolver = FakeResolver { ByteArrayInputStream(ByteArray(4)) }
        val cancellation = CancellationException("cancelled")

        val thrown = assertThrows(CancellationException::class.java) {
            GifImportReader.read({ resolver.openInputStream() }) { throw cancellation }
        }

        assertSame(cancellation, thrown)
        assertEquals(0, resolver.opened)
    }

    @Test
    fun providerFailureMidCopyIsTypedAndClosesTheStream() {
        listOf(
            IllegalStateException("provider died") to GifImportFailure.SourceUnreadable,
            SecurityException("grant revoked mid-read") to GifImportFailure.PermissionRevoked,
            IOException("pipe broken") to GifImportFailure.SourceUnreadable,
            FileNotFoundException("deleted") to GifImportFailure.SourceMissing
        ).forEach { (failure, expected) ->
            val stream = FailingStream(goodBytes = 20_000, failure = failure)

            val error = assertThrows(GifImportException::class.java) {
                GifImportReader.read({ stream }) {}
            }

            assertEquals(expected, error.reason)
            assertSame(failure, error.cause)
            assertTrue("stream must be closed after $failure", stream.closed)
        }
    }

    @Test
    fun cancellationMidCopyIsNotWrappedAsSourceFailure() {
        val cancellation = CancellationException("cancel")
        var checks = 0
        val stream = FailingStream(goodBytes = 100_000, failure = IOException("unused"))

        val thrown = assertThrows(CancellationException::class.java) {
            GifImportReader.read({ stream }) { if (++checks == 3) throw cancellation }
        }

        assertSame(cancellation, thrown)
        assertTrue(stream.closed)
    }

    @Test
    fun classifiesRawResolverExceptionsFromCallersThatOpenTheStreamThemselves() {
        assertEquals(GifImportFailure.PermissionRevoked, classifyGifImportFailure(SecurityException()))
        assertEquals(GifImportFailure.SourceMissing, classifyGifImportFailure(RuntimeException(FileNotFoundException())))
        assertEquals(GifImportFailure.SourceUnreadable, classifyGifImportFailure(IOException("broken pipe")))
        assertNull(classifyGifImportFailure(CancellationException()))
        // A storage-side IllegalStateException (no provider frames) is not blamed on the source.
        assertNull(classifyGifImportFailure(IllegalStateException("could not save")))
        val providerFailure = IllegalStateException("Couldn't read row").apply {
            stackTrace = arrayOf(StackTraceElement("android.content.ContentResolver", "openInputStream", null, 0))
        }
        assertEquals(GifImportFailure.SourceUnreadable, classifyGifImportFailure(providerFailure))
        assertEquals(GifImportFailure.Corrupt, classifyGifImportFailure(GifImportException(GifImportFailure.Corrupt, "x")))
    }

    @Test
    fun mediaStoreWriteFailureIsNeverBlamedOnTheSource() {
        val error = com.batoh.core.storage.util.MediaStoreWriteException("x").apply {
            addSuppressed(SecurityException("write denied"))
            addSuppressed(IOException("disk full"))
        }
        assertNull(classifyGifImportFailure(error))
    }
}
