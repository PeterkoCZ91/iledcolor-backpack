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

/** Name/size precedence and duplicate-advert handling of [SendTargetPlan]; complements SendTargetPlanTest. */
class SendTargetPlanPrecedenceTest {
    private val wide = PanelSpec(PanelFamily.IPixel, 64, 16, true)
    private val square = PanelSpec(PanelFamily.IPixel, 32, 32, true)
    private val tall = PanelSpec(PanelFamily.IPixel, 16, 64, true)
    private val mineA = MyPanel("AA:AA:AA:AA:AA:01", "Stored A", wide)
    private val mineB = MyPanel("BB:BB:BB:BB:BB:02", "Stored B", square)
    private val mineC = MyPanel("CC:CC:CC:CC:CC:03", null, null)
    private val stranger = "EE:EE:EE:EE:EE:05"

    private fun adv(address: String, rssi: Int, spec: PanelSpec? = null, name: String? = null) =
        NearbyPanel(address, name, spec, rssi)

    @Test fun connectedName_storedWinsOverLiveWinsOverAdvert() {
        val advert = listOf(adv(mineA.address, -50, name = "Advert A"))
        val withStored = SendTargetPlan.candidates(ConnectedPanel(mineA.address, "Live A", null), listOf(mineA), advert)
        assertEquals("Stored A", withStored.single().name)

        val unnamedStored = MyPanel(mineA.address, null, wide)
        val live = SendTargetPlan.candidates(ConnectedPanel(mineA.address, "Live A", null), listOf(unnamedStored), advert)
        assertEquals("Live A", live.single().name)

        val onlyAdvert = SendTargetPlan.candidates(ConnectedPanel(mineA.address, null, null), listOf(unnamedStored), advert)
        assertEquals("Advert A", onlyAdvert.single().name)
    }

    @Test fun connectedSize_liveWinsOverAdvertWinsOverStored() {
        val advert = listOf(adv(mineA.address, -50, spec = square))
        assertEquals(tall, SendTargetPlan.candidates(ConnectedPanel(mineA.address, null, tall), listOf(mineA), advert).single().spec)
        assertEquals(square, SendTargetPlan.candidates(ConnectedPanel(mineA.address, null, null), listOf(mineA), advert).single().spec)
        assertEquals(wide, SendTargetPlan.candidates(ConnectedPanel(mineA.address, null, null), listOf(mineA), emptyList()).single().spec)
    }

    @Test fun connectedWithNothingKnown_hasNullNameAndSize() {
        val target = SendTargetPlan.candidates(ConnectedPanel(stranger, null, null), emptyList(), emptyList()).single()
        assertNull(target.name)
        assertNull(target.spec)
        assertTrue(target.connected)
        assertFalse(target.mine)
    }

    @Test fun inRangeMine_storedNameWinsButAdvertSizeWins() {
        val target = SendTargetPlan.candidates(null, listOf(mineA), listOf(adv(mineA.address, -50, spec = tall, name = "Advert A"))).single()
        assertEquals("Stored A", target.name)
        assertEquals(tall, target.spec)
    }

    @Test fun inRangeMine_fallsBackToAdvertNameAndStoredSize() {
        val named = SendTargetPlan.candidates(null, listOf(mineC), listOf(adv(mineC.address, -50, name = "Advert C"))).single()
        assertEquals("Advert C", named.name)
        val stored = SendTargetPlan.candidates(null, listOf(mineA), listOf(adv(mineA.address, -50, spec = null))).single()
        assertEquals(wide, stored.spec)
    }

    @Test fun duplicateAdverts_lastOneDecidesTheSignalOrder() {
        // associateBy keeps the last advert of an address: A ends up at -90, B at -60, so B comes first.
        val candidates = SendTargetPlan.candidates(
            null, listOf(mineA, mineB),
            listOf(adv(mineA.address, -30), adv(mineB.address, -60), adv(mineA.address, -90))
        )
        assertEquals(listOf(mineB.address, mineA.address), candidates.map { it.address })
    }

    @Test fun equalSignal_keepsStoredPanelOrder() {
        // sortedByDescending is stable.
        val candidates = SendTargetPlan.candidates(
            null, listOf(mineB, mineA), listOf(adv(mineA.address, -60), adv(mineB.address, -60))
        )
        assertEquals(listOf(mineB.address, mineA.address), candidates.map { it.address })
    }

    @Test fun mineNotAdvertisingIsOutOfRangeAndNotACandidate() {
        assertTrue(SendTargetPlan.candidates(null, listOf(mineA, mineB), emptyList()).isEmpty())
    }

    @Test fun connectedStrangerPlusTwoMine_offersAll() {
        val decision = SendTargetPlan.decide(
            SendTargetPlan.candidates(
                ConnectedPanel(stranger, "Backpack", null), listOf(mineA, mineB),
                listOf(adv(mineA.address, -50), adv(mineB.address, -60))
            )
        ) as SendDecision.Choose
        assertEquals(listOf(stranger, mineA.address, mineB.address), decision.targets.map { it.address })
        assertTrue(decision.offerAll)
    }

    @Test fun singleConnectedStranger_isSentAtOnce() {
        val decision = SendTargetPlan.decide(SendTargetPlan.candidates(ConnectedPanel(stranger, null, square), emptyList(), emptyList()))
        val target = (decision as SendDecision.Single).target
        assertEquals(stranger, target.address)
        assertFalse(target.mine)
    }

    @Test fun preferred_ignoresMissingRememberedAddressAndEmptyLists() {
        val two = SendTargetPlan.candidates(null, listOf(mineA, mineB), listOf(adv(mineA.address, -50), adv(mineB.address, -60)))
        assertNull(SendTargetPlan.preferred(two, null))
        assertNull(SendTargetPlan.preferred(emptyList(), mineA.address))
    }

    @Test fun needsScan_connectedStrangerStillScansForMine() {
        assertTrue(SendTargetPlan.needsScan(stranger, listOf(mineA)))
        assertFalse(SendTargetPlan.needsScan(stranger, emptyList()))
    }
}
