package com.batoh.feature.backpack

import com.batoh.core.data.bluetooth.MyPanel
import com.batoh.core.data.bluetooth.NearbyPanel
import com.batoh.core.data.bluetooth.PanelFamily
import com.batoh.core.data.bluetooth.PanelSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SendTargetPlanTest {
    private val wide = PanelSpec(PanelFamily.IPixel, 64, 16, true)
    private val square = PanelSpec(PanelFamily.IPixel, 32, 32, true)
    private val mineA = MyPanel("AA:AA:AA:AA:AA:01", "Panel A", wide)
    private val mineB = MyPanel("BB:BB:BB:BB:BB:02", "Panel B", square)
    private val mineC = MyPanel("CC:CC:CC:CC:CC:03", null, null)
    private val stranger = "EE:EE:EE:EE:EE:05"

    private fun adv(address: String, rssi: Int, spec: PanelSpec? = square, name: String? = "adv") = NearbyPanel(address, name, spec, rssi)

    @Test fun noPanelAtAll_choosesWithNothingAndNoAll() {
        val decision = SendTargetPlan.decide(SendTargetPlan.candidates(null, listOf(mineA), emptyList()))
        assertEquals(SendDecision.Choose(emptyList(), offerAll = false), decision)
    }

    @Test fun onlyConnected_sendsAtOnceWithItsRealSize() {
        val connected = ConnectedPanel(mineA.address, null, square)
        val decision = SendTargetPlan.decide(SendTargetPlan.candidates(connected, listOf(mineA), emptyList()))
        val target = (decision as SendDecision.Single).target
        assertEquals(mineA.address, target.address)
        assertEquals("Panel A", target.name)
        assertEquals(square, target.spec)
        assertTrue(target.connected)
    }

    @Test fun onlyOneMineInRange_sendsAtOnce() {
        val decision = SendTargetPlan.decide(SendTargetPlan.candidates(null, listOf(mineA, mineB), listOf(adv(mineB.address, -60))))
        val target = (decision as SendDecision.Single).target
        assertEquals(mineB.address, target.address)
        assertFalse(target.connected)
    }

    @Test fun connectedPlusAnotherInRange_asksAndOffersAll() {
        val connected = ConnectedPanel(mineA.address, "Panel A", wide)
        val decision = SendTargetPlan.decide(
            SendTargetPlan.candidates(connected, listOf(mineA, mineB), listOf(adv(mineB.address, -60)))
        ) as SendDecision.Choose
        assertEquals(listOf(mineA.address, mineB.address), decision.targets.map { it.address })
        assertTrue(decision.targets[0].connected)
        assertTrue(decision.offerAll)
    }

    @Test fun order_connectedFirstThenStrongestSignal() {
        val connected = ConnectedPanel(mineC.address, null, null)
        val candidates = SendTargetPlan.candidates(
            connected, listOf(mineA, mineB, mineC),
            listOf(adv(mineA.address, -80), adv(mineB.address, -40))
        )
        assertEquals(listOf(mineC.address, mineB.address, mineA.address), candidates.map { it.address })
    }

    @Test fun strangerAdvertisingIsNeverACandidate() {
        val candidates = SendTargetPlan.candidates(null, listOf(mineA), listOf(adv(stranger, -20), adv(mineA.address, -70)))
        assertEquals(listOf(mineA.address), candidates.map { it.address })
    }

    @Test fun connectedStrangerIsACandidateButNotMine_noAllOffer() {
        val connected = ConnectedPanel(stranger, "Backpack", PanelSpec.BACKPACK_DEFAULT)
        val decision = SendTargetPlan.decide(
            SendTargetPlan.candidates(connected, listOf(mineA), listOf(adv(mineA.address, -50)))
        ) as SendDecision.Choose
        assertEquals(2, decision.targets.size)
        assertFalse(decision.targets[0].mine)
        assertFalse(decision.offerAll)
    }

    @Test fun connectedPanelIsListedOnceEvenWhenAlsoAdvertising() {
        val connected = ConnectedPanel(mineA.address, "Panel A", wide)
        val candidates = SendTargetPlan.candidates(connected, listOf(mineA), listOf(adv(mineA.address, -40)))
        assertEquals(1, candidates.size)
        assertEquals(wide, candidates.single().spec)
    }

    @Test fun unknownSizeStaysNull_andFallsBackToStoredThenAdvert() {
        assertNull(SendTargetPlan.candidates(null, listOf(mineC), listOf(adv(mineC.address, -50, spec = null))).single().spec)
        val stored = SendTargetPlan.candidates(ConnectedPanel(mineA.address, null, null), listOf(mineA), emptyList()).single()
        assertEquals(wide, stored.spec)
    }

    @Test fun allOffer_needsTwoOfMine() {
        val two = SendTargetPlan.decide(SendTargetPlan.candidates(null, listOf(mineA, mineB),
            listOf(adv(mineA.address, -50), adv(mineB.address, -60)))) as SendDecision.Choose
        assertTrue(two.offerAll)
    }

    @Test fun needsScan_onlyWhenAnotherOfMineExists() {
        assertFalse(SendTargetPlan.needsScan(mineA.address, listOf(mineA)))
        assertTrue(SendTargetPlan.needsScan(mineA.address, listOf(mineA, mineB)))
        assertTrue(SendTargetPlan.needsScan(null, listOf(mineA)))
        assertFalse(SendTargetPlan.needsScan(null, emptyList()))
    }

    @Test fun rememberedTargetIsPromotedOnlyWhenItIsAvailableAlongsideAnother() {
        val available = SendTargetPlan.candidates(null, listOf(mineA, mineB),
            listOf(adv(mineA.address, -50), adv(mineB.address, -60)))
        assertEquals(mineB.address, SendTargetPlan.preferred(available, mineB.address)?.address)
        assertNull(SendTargetPlan.preferred(available, mineC.address))
        assertNull(SendTargetPlan.preferred(available.take(1), mineA.address))
    }
}
