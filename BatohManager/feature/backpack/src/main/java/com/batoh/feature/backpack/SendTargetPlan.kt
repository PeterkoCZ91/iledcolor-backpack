package com.batoh.feature.backpack

import com.batoh.core.data.bluetooth.MyPanel
import com.batoh.core.data.bluetooth.NearbyPanel
import com.batoh.core.data.bluetooth.PanelSpec

/**
 * A panel a GIF can be sent to right now. [spec] holds the real size (null while unknown), [connected] is the panel
 * the app is linked to, [mine] means the user picked it in GifPack.
 */
data class SendTarget(
    val address: String,
    val name: String?,
    val spec: PanelSpec?,
    val connected: Boolean,
    val mine: Boolean,
)

/** What a send that starts with a GIF does next. */
sealed interface SendDecision {
    /** Exactly one candidate: send at once, no question asked. */
    data class Single(val target: SendTarget) : SendDecision

    /** Zero or several candidates: the user chooses. [offerAll] adds "all my panels" (at least two of mine in range). */
    data class Choose(val targets: List<SendTarget>, val offerAll: Boolean) : SendDecision
}

/** The panel the app is linked to (or linking to) when a send starts. */
data class ConnectedPanel(val address: String, val name: String?, val spec: PanelSpec?)

/** Pure selection of the send targets; JVM-tested. */
object SendTargetPlan {
    /** A remembered panel is offered directly only if this send's fresh candidate list includes it. */
    fun preferred(candidates: List<SendTarget>, rememberedAddress: String?): SendTarget? =
        if (candidates.size < 2) null else candidates.firstOrNull { it.address == rememberedAddress }

    /**
     * Candidates: the [connected] panel plus [myPanels] that advertise ([advertising], a fresh quick scan).
     * A panel that is merely advertising and not mine is never a candidate; a connected one always is (the app
     * linked to it by its own rules). Order: connected first, then the strongest signal first. One entry per address;
     * the size comes from the live link or advert first, then from what is stored for the panel.
     */
    fun candidates(connected: ConnectedPanel?, myPanels: List<MyPanel>, advertising: List<NearbyPanel>): List<SendTarget> {
        val adverts = advertising.associateBy { it.address }
        val mine = myPanels.associateBy { it.address }
        val result = mutableListOf<SendTarget>()
        if (connected != null) {
            val stored = mine[connected.address]
            result += SendTarget(
                address = connected.address,
                name = stored?.name ?: connected.name ?: adverts[connected.address]?.name,
                spec = connected.spec ?: adverts[connected.address]?.spec ?: stored?.spec,
                connected = true,
                mine = stored != null,
            )
        }
        myPanels.filter { it.address != connected?.address && it.address in adverts }
            .sortedByDescending { adverts.getValue(it.address).rssi }
            .forEach { panel ->
                val advert = adverts.getValue(panel.address)
                result += SendTarget(panel.address, panel.name ?: advert.name, advert.spec ?: panel.spec, connected = false, mine = true)
            }
        return result
    }

    fun decide(candidates: List<SendTarget>): SendDecision =
        candidates.singleOrNull()?.let { SendDecision.Single(it) }
            ?: SendDecision.Choose(candidates, offerAll = candidates.count { it.mine } >= 2)

    /** Whether a quick scan can add anything: only when one of my panels is not the connected one. */
    fun needsScan(connectedAddress: String?, myPanels: List<MyPanel>): Boolean =
        myPanels.any { it.address != connectedAddress }
}
