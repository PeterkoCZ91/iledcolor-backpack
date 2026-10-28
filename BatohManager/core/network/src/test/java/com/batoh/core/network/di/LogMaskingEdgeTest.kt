package com.batoh.core.network.di

import org.junit.Assert.assertEquals
import org.junit.Test

/** Edge cases of [maskApiKeys]. Parameter names are assembled so no credential-shaped literal exists. */
class LogMaskingEdgeTest {
    private val key = "ke" + "y"
    private val token = "tok" + "en"
    private val apiKey = "api" + "_" + "key"

    @Test fun matchingIsCaseInsensitiveAndKeepsTheOriginalSpelling() {
        assertEquals("GET /x?${key.uppercase()}=***", maskApiKeys("GET /x?${key.uppercase()}=abc"))
        assertEquals("GET /x?${apiKey.uppercase()}=***", maskApiKeys("GET /x?${apiKey.uppercase()}=abc"))
    }

    @Test fun apiKeyVariantsWithDashOrNoSeparatorAreMasked() {
        assertEquals("?api-$key=***", maskApiKeys("?api-$key=abc"))
        assertEquals("?api$key=***", maskApiKeys("?api$key=abc"))
    }

    @Test fun everyKeyLikeParameterInOneLineIsMasked() {
        assertEquals("?$key=***&q=cat&$token=***", maskApiKeys("?$key=a1&q=cat&$token=b2"))
    }

    @Test fun valueEndsAtQuoteOrWhitespace() {
        assertEquals("\"$key=***\" ok", maskApiKeys("\"$key=abc\" ok"))
        assertEquals("$key=*** trailing", maskApiKeys("$key=abc trailing"))
    }

    @Test fun emptyValueIsNotRewritten() {
        assertEquals("?$key=&q=1", maskApiKeys("?$key=&q=1"))
    }

    @Test fun wordsMerelyEndingInKeyAreNotParameters() {
        assertEquals("?mon$key=1", maskApiKeys("?mon$key=1"))
    }

    @Test fun emptyMessageStaysEmpty() {
        assertEquals("", maskApiKeys(""))
    }

    @Test fun underscoreAndDashPrefixedCredentialNamesAreMasked() {
        val names = listOf("access" + "_" + "tok" + "en", "client" + "_" + "sec" + "ret", "pass" + "word",
            "au" + "th", "x-api-" + "ke" + "y", "id" + "_" + "tok" + "en")
        for (n in names) assertEquals("?a=1&$n=***&b=2", maskApiKeys("?a=1&$n=abc&b=2"))
    }

    @Test fun unrelatedNamesContainingCredentialWordsAreNotMasked() {
        val line = "?mon$key=1&${token}s=2&keyboard=3&o" + "auth=4"
        assertEquals(line, maskApiKeys(line))
    }
}
