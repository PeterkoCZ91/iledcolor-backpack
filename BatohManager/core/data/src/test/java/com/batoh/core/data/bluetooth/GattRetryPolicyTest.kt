package com.batoh.core.data.bluetooth

import com.batoh.core.data.bluetooth.GattRetryPolicy.Phase
import org.junit.Assert.*
import org.junit.Test

class GattRetryPolicyTest {
    @Test fun status133WhileConnectingIsRetriedOnce() {
        val policy = GattRetryPolicy()
        policy.reset()
        val retry = policy.onDisconnected(133, Phase.CONNECTING)
        assertNotNull(retry)
        assertEquals(1, retry!!.attempt)
        assertEquals(1, retry.maxRetries)
        assertTrue(retry.delayMs in 600L..1000L)
        assertTrue(policy.isCurrent(retry.token))
        assertNull("budget is spent", policy.onDisconnected(133, Phase.CONNECTING))
    }

    @Test fun status133DuringDiscoveryIsRetried() {
        assertNotNull(GattRetryPolicy().onDisconnected(133, Phase.DISCOVERING))
    }

    @Test fun status62IsRetriedButTimeoutAndOthersAreNot() {
        assertNotNull(GattRetryPolicy().onDisconnected(62, Phase.CONNECTING))
        assertNull(GattRetryPolicy().onDisconnected(8, Phase.CONNECTING))
        assertNull(GattRetryPolicy().onDisconnected(0, Phase.CONNECTING))
        assertNull(GattRetryPolicy().onDisconnected(19, Phase.DISCOVERING))
        assertNull(GattRetryPolicy().onDisconnected(257, Phase.CONNECTING))
    }

    @Test fun neverRetriesAfterReadyOrWhenIdle() {
        assertNull(GattRetryPolicy().onDisconnected(133, Phase.READY))
        assertNull(GattRetryPolicy().onDisconnected(133, Phase.IDLE))
    }

    @Test fun neverRetriesWhileAnOperationIsInFlight() {
        assertNull(GattRetryPolicy().onDisconnected(133, Phase.DISCOVERING, operationInFlight = true))
    }

    @Test fun rejectedFailureDoesNotSpendBudget() {
        val policy = GattRetryPolicy()
        assertNull(policy.onDisconnected(133, Phase.READY))
        assertNull(policy.onDisconnected(8, Phase.CONNECTING))
        assertNotNull(policy.onDisconnected(133, Phase.CONNECTING))
    }

    @Test fun resetRestoresBudgetForNewManualConnect() {
        val policy = GattRetryPolicy()
        assertNotNull(policy.onDisconnected(133, Phase.CONNECTING))
        assertNull(policy.onDisconnected(133, Phase.CONNECTING))
        policy.reset()
        assertNotNull(policy.onDisconnected(133, Phase.CONNECTING))
    }

    @Test fun configurableMaxRetries() {
        val policy = GattRetryPolicy(maxRetries = 2)
        assertEquals(1, policy.onDisconnected(133, Phase.CONNECTING)!!.attempt)
        assertEquals(2, policy.onDisconnected(133, Phase.DISCOVERING)!!.attempt)
        assertNull(policy.onDisconnected(133, Phase.CONNECTING))
        assertNull(GattRetryPolicy(maxRetries = 0).onDisconnected(133, Phase.CONNECTING))
    }

    @Test fun cancelInvalidatesPendingRetry() {
        val policy = GattRetryPolicy()
        val retry = policy.onDisconnected(133, Phase.CONNECTING)!!
        policy.cancel()
        assertFalse(policy.isCurrent(retry.token))
    }

    @Test fun resetAlsoInvalidatesPendingRetry() {
        val policy = GattRetryPolicy()
        val retry = policy.onDisconnected(133, Phase.CONNECTING)!!
        policy.reset()
        assertFalse(policy.isCurrent(retry.token))
    }
}
