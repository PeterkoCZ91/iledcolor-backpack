package com.batoh.feature.backpack

import com.batoh.core.data.bluetooth.MyPanel
import com.batoh.core.data.bluetooth.NearbyPanel
import com.batoh.core.data.bluetooth.PanelFamily
import com.batoh.core.data.bluetooth.PanelSpec
import com.batoh.core.data.bluetooth.UploadFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiPanelSendTest {
    private val wide = PanelSpec(PanelFamily.IPixel, 64, 16, true)
    private val square = PanelSpec(PanelFamily.IPixel, 32, 32, true)
    private val mineA = MyPanel("AA:AA:AA:AA:AA:01", "LED_BLE_A", wide)
    private val mineB = MyPanel("BB:BB:BB:BB:BB:02", "LED_BLE_B", square)
    private val mineBackpack = MyPanel("CC:CC:CC:CC:CC:03", "iledcolor-0003", PanelSpec.BACKPACK_DEFAULT)
    private val stranger = "EE:EE:EE:EE:EE:05"

    private fun adv(address: String, rssi: Int, spec: PanelSpec? = wide) = NearbyPanel(address, "panel", spec, rssi)

    @Test fun plan_onlyPickedAndAdvertising() {
        val plan = MultiPanelSend.plan(
            myPanels = listOf(mineA, mineB),
            advertising = listOf(adv(mineA.address, -50), adv(stranger, -30)),
            connectedAddress = null
        )
        assertEquals(listOf(mineA), plan.targets)
        assertEquals(listOf(mineB), plan.notInRange)
        // A panel only seen in a scan is never a target, however strong its signal.
        assertTrue((plan.targets + plan.notInRange).none { it.address == stranger })
    }

    @Test fun plan_strangerConnectedIsNeverATarget() {
        val plan = MultiPanelSend.plan(listOf(mineA), listOf(adv(stranger, -20)), connectedAddress = stranger)
        assertEquals(emptyList<MyPanel>(), plan.targets)
        assertEquals(listOf(mineA), plan.notInRange)
    }

    @Test fun plan_noPickedPanelsSendsNothing() {
        val plan = MultiPanelSend.plan(emptyList(), listOf(adv(stranger, -20), adv(mineA.address, -40)), mineA.address)
        assertEquals(emptyList<MyPanel>(), plan.targets)
        assertEquals(emptyList<MyPanel>(), plan.notInRange)
    }

    /** Pinned: strongest signal first, the panel connected before the run last (it stays connected afterwards). */
    @Test fun plan_keepsPreviousPanelFirstOrLast() {
        val plan = MultiPanelSend.plan(
            myPanels = listOf(mineA, mineB, mineBackpack),
            // A connected panel usually stops advertising: it is in range without being in the scan.
            advertising = listOf(adv(mineA.address, -70), adv(mineB.address, -40)),
            connectedAddress = mineBackpack.address
        )
        assertEquals(listOf(mineB, mineA, mineBackpack), plan.targets)
        assertEquals(emptyList<MyPanel>(), plan.notInRange)

        val advertisingToo = MultiPanelSend.plan(listOf(mineA, mineB),
            listOf(adv(mineA.address, -30), adv(mineB.address, -60)), connectedAddress = mineA.address)
        assertEquals(listOf(mineB, mineA), advertisingToo.targets)
    }

    @Test fun summary_countsSuccessAndFailure() {
        val results = listOf(
            PanelSendResult(mineA, PanelSendOutcome.Sent),
            PanelSendResult(mineB, PanelSendOutcome.Failed(UploadFailure.ConnectionLost)),
            PanelSendResult(mineBackpack, PanelSendOutcome.AlreadyPresent),
            PanelSendResult(MyPanel("DD:DD:DD:DD:DD:04", null, null), PanelSendOutcome.NotInRange),
            PanelSendResult(MyPanel("FF:FF:FF:FF:FF:06", null, null), PanelSendOutcome.Skipped)
        )
        val summary = MultiPanelSend.summary(results)
        assertEquals(2, summary.succeeded)
        assertEquals(5, summary.total)
        assertEquals(false, summary.allSucceeded)
        assertEquals(true, MultiPanelSend.summary(results.take(1)).allSucceeded)
        assertEquals(false, MultiPanelSend.summary(emptyList()).allSucceeded)
    }

    @Test fun closeLinkAfterRun_closesRunLinksOnly() {
        // A link the run opened to another panel is closed; the panel connected before stays.
        assertEquals(true, MultiPanelSend.closeLinkAfterRun(mineA.address, mineB.address, "Ready"))
        assertEquals(false, MultiPanelSend.closeLinkAfterRun(mineB.address, mineB.address, "Ready"))
        assertEquals(false, MultiPanelSend.closeLinkAfterRun(null, mineB.address, "Disconnected"))
        assertEquals(false, MultiPanelSend.closeLinkAfterRun(null, null, "Disconnected"))
    }

    @Test fun closeLinkAfterRun_cancelsPendingRetry() {
        // After a status-133 drop there is no link but a retry is scheduled ("Connecting (retry …)"): cancel it,
        // or it would reopen the run's panel after the last panel was restored.
        assertEquals(true, MultiPanelSend.closeLinkAfterRun(null, mineB.address, "Connecting (retry 1/2)..."))
        assertEquals(true, MultiPanelSend.closeLinkAfterRun(null, null, "Connecting (retry 1/2)..."))
    }
}
