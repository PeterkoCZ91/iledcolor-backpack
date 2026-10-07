package com.batoh.feature.convert

import com.batoh.core.conversion.DownloadHttpException
import com.batoh.core.conversion.EmptyVideoResponseException
import com.batoh.core.conversion.VideoTooLargeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException

class ConvertErrorTest {
    @Test fun cleartext() = assertEquals(
        ConvertError.CleartextBlocked,
        classifyConvertError(UnknownServiceException("CLEARTEXT communication to localhost not permitted by network security policy"))
    )

    @Test fun httpStatus() = assertEquals(ConvertError.Http(403), classifyConvertError(DownloadHttpException(403)))

    @Test fun network() {
        assertEquals(ConvertError.Network, classifyConvertError(SocketTimeoutException("timeout")))
        assertEquals(ConvertError.Network, classifyConvertError(UnknownHostException("x")))
    }

    @Test fun tooLarge() = assertEquals(
        ConvertError.TooLarge, classifyConvertError(VideoTooLargeException())
    )

    @Test fun outOfMemoryIsTooLarge() = assertEquals(ConvertError.TooLarge, classifyConvertError(OutOfMemoryError()))

    @Test fun decoderMessageIsNotTooLarge() = assertEquals(
        ConvertError.Unreadable,
        classifyConvertError(IllegalArgumentException("GIF má příliš velké rozměry (nejvýše 4 miliony pixelů)"))
    )

    @Test fun plainIoWithHttpLikeTextIsNetwork() =
        assertEquals(ConvertError.Network, classifyConvertError(IOException("Download failed: 403")))

    @Test fun emptyResponse() =
        assertEquals(ConvertError.Unreadable, classifyConvertError(EmptyVideoResponseException()))

    @Test fun invalidUrlFromOkHttp() = assertEquals(
        ConvertError.InvalidUrl, classifyConvertError(IllegalArgumentException("Expected URL scheme 'http' or 'https' but was 'ftp'"))
    )

    @Test fun unreadable() {
        assertEquals(ConvertError.Unreadable, classifyConvertError(IllegalArgumentException("Video neobsahuje čitelné snímky")))
        assertEquals(ConvertError.Unreadable, classifyConvertError(IllegalStateException("GIF nelze dokončit")))
        assertEquals(ConvertError.Unreadable, classifyConvertError(RuntimeException("setDataSource failed: status = 0x80000000")))
    }

    @Test fun unknown() = assertEquals(ConvertError.Unknown, classifyConvertError(Exception("boom")))

    @Test fun urlValidation() {
        assertTrue(isValidVideoUrl("https://example.com/a.mp4"))
        assertTrue(isValidVideoUrl("http://example.com/a.mp4"))
        assertFalse(isValidVideoUrl("example.com/a.mp4"))
        assertFalse(isValidVideoUrl("ftp://example.com/a.mp4"))
        assertFalse(isValidVideoUrl("https://"))
        assertFalse(isValidVideoUrl("not a url"))
    }

    @Test fun editingClearsError() {
        assertEquals(ConvertUiState.Idle, ConvertUiState.Error(ConvertError.Network).afterUrlEdit())
    }

    @Test fun editingKeepsOtherStates() {
        val converting = ConvertUiState.Converting(40)
        assertSame(converting, converting.afterUrlEdit())
        assertSame(ConvertUiState.Idle, ConvertUiState.Idle.afterUrlEdit())
    }
}
