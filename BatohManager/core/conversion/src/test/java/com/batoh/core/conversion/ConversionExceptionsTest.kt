package com.batoh.core.conversion

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversionExceptionsTest {
    @Test fun httpExceptionCarriesCode() {
        val e = DownloadHttpException(404)
        assertEquals(404, e.code)
        assertTrue(IOException::class.java.isAssignableFrom(e.javaClass))
    }

    @Test fun tooLargeKeepsCause() {
        val oom = OutOfMemoryError()
        assertSame(oom, VideoTooLargeException(oom).cause)
        assertTrue(IllegalArgumentException::class.java.isAssignableFrom(VideoTooLargeException::class.java))
    }
}
