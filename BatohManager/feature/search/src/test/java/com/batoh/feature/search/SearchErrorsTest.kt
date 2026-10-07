package com.batoh.feature.search

import com.batoh.core.common.MissingApiKeyException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchErrorsTest {
    @Test fun classifiesKnownFailures() {
        assertEquals(LoadError.MissingApiKey, MissingApiKeyException("Klipy").toLoadError())
        assertEquals(LoadError.Network, UnknownHostException("api.example").toLoadError())
        assertEquals(LoadError.Network, SocketTimeoutException().toLoadError())
        assertEquals(LoadError.Network, IOException("wrapped", UnknownHostException()).toLoadError())
    }

    @Test fun unknownOrMissingCauseIsGeneric() {
        assertEquals(LoadError.Unknown, IllegalStateException("Unexpected JSON").toLoadError())
        assertEquals(LoadError.Unknown, (null as Throwable?).toLoadError())
    }
}
