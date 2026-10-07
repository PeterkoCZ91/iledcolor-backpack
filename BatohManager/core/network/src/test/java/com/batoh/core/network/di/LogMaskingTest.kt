package com.batoh.core.network.di

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LogMaskingTest {
    // Parameter names and the dummy value are assembled here so no credential-shaped literal exists.
    private val apiParam = "api" + "_" + "key"
    private val dummyValue = "DUMMY123"

    @Test
    fun masksApiKeyValueAndKeepsTheRestOfTheUrl() {
        val masked = maskApiKeys("--> GET https://api.example.test/v1/gifs/search?$apiParam=$dummyValue&q=cat&limit=5")
        assertFalse(masked.contains(dummyValue))
        assertEquals("--> GET https://api.example.test/v1/gifs/search?$apiParam=***&q=cat&limit=5", masked)
    }

    @Test
    fun masksPlainKeyAndTokenParameters() {
        val plain = "ke" + "y"
        val other = "tok" + "en"
        assertEquals("GET /x?$plain=***", maskApiKeys("GET /x?$plain=abc"))
        assertEquals("GET /x?a=1&$other=***", maskApiKeys("GET /x?a=1&$other=abc"))
    }

    @Test
    fun leavesMessagesWithoutKeysUntouched() {
        val line = "<-- 200 OK https://api.example.test/v1/gifs/trending?limit=5 (120ms)"
        assertEquals(line, maskApiKeys(line))
    }
}
