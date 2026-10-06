package com.batoh.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeBackpackStatusTest {

    @Test
    fun readyStatusesAreConnected() {
        assertEquals(BackpackLinkState.CONNECTED, HomeBackpackStatus.linkStateOf("Ready (MTU=517)"))
        assertEquals(
            BackpackLinkState.CONNECTED,
            HomeBackpackStatus.linkStateOf("Ready (MTU negotiation failed, using default)")
        )
    }

    @Test
    fun setupStepsAreConnecting() {
        listOf(
            "Connecting (reading capabilities)...",
            "Connecting to iledcolor-0000...",
            "Connected! Discovering services..."
        ).forEach { assertEquals(it, BackpackLinkState.CONNECTING, HomeBackpackStatus.linkStateOf(it)) }
    }

    @Test
    fun everythingElseIsNotConnected() {
        listOf(
            "Disconnected",
            "Scanning (Hybrid)...",
            "Scan Stopped",
            "Missing Permissions",
            "Connection state: 0 (Status: 133)",
            "Service Discovery Failed: 129",
            "Target Service Not Found",
            ""
        ).forEach { assertEquals(it, BackpackLinkState.NOT_CONNECTED, HomeBackpackStatus.linkStateOf(it)) }
    }

    @Test
    fun blankNameAndNegativeFirmwareAreDropped() {
        val status = HomeBackpackStatus.from("Disconnected", "  ", -1)
        assertNull(status.deviceName)
        assertNull(status.firmwareVersion)
    }

    @Test
    fun cachedDetailsAreKeptWhileDisconnected() {
        val status = HomeBackpackStatus.from("Disconnected", "iledcolor-0000", 14)
        assertEquals(HomeBackpackStatus(BackpackLinkState.NOT_CONNECTED, "iledcolor-0000", 14), status)
    }
}
