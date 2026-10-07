package com.batoh.core.data.bluetooth

import com.batoh.core.data.bluetooth.GattRetryPolicy.Phase
import org.junit.Assert.*
import org.junit.Test

class MtuNegotiationTest {
    @Test fun successKeepsNegotiatedMtu() = assertEquals(512, MtuNegotiation.effectiveMtu(0, 512))
    @Test fun failureFallsBackToDefault() = assertEquals(23, MtuNegotiation.effectiveMtu(133, 512))
    @Test fun bogusMtuFallsBackToDefault() = assertEquals(23, MtuNegotiation.effectiveMtu(0, 5))
    @Test fun fallbackOnlyWhileDiscovering() {
        assertTrue(MtuNegotiation.shouldFallBack(Phase.DISCOVERING))
        assertFalse(MtuNegotiation.shouldFallBack(Phase.READY))
        assertFalse(MtuNegotiation.shouldFallBack(Phase.IDLE))
    }
    @Test fun lateMtuChangeIgnoredAfterReady() {
        assertTrue(MtuNegotiation.shouldAcceptMtuChange(Phase.DISCOVERING))
        assertFalse(MtuNegotiation.shouldAcceptMtuChange(Phase.READY))
        assertFalse(MtuNegotiation.shouldAcceptMtuChange(Phase.IDLE))
        assertFalse(MtuNegotiation.shouldAcceptMtuChange(Phase.CONNECTING))
    }
}
