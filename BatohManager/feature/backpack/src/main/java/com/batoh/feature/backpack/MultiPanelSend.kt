package com.batoh.feature.backpack

import com.batoh.core.data.bluetooth.MyPanel
import com.batoh.core.data.bluetooth.NearbyPanel
import com.batoh.core.data.bluetooth.UploadFailure

/** What happened on one of my panels during a "send to all my panels" run. */
sealed interface PanelSendOutcome {
    object Sent : PanelSendOutcome
    object AlreadyPresent : PanelSendOutcome
    data class Failed(val failure: UploadFailure) : PanelSendOutcome
    /** Not advertising during the quick scan (and not connected): nothing was sent. */
    object NotInRange : PanelSendOutcome
    /** The run was cancelled before this panel's turn. */
    object Skipped : PanelSendOutcome

    val succeeded: Boolean get() = this == Sent || this == AlreadyPresent
}

data class PanelSendResult(val panel: MyPanel, val outcome: PanelSendOutcome)

/** [targets] in sending order; [notInRange] are my panels that will not be contacted. */
data class MultiPanelPlan(val targets: List<MyPanel>, val notInRange: List<MyPanel>)

data class MultiPanelSummary(val succeeded: Int, val total: Int) {
    val allSucceeded: Boolean get() = total > 0 && succeeded == total
}

/** Progress of a run for the UI: panel [index] (1-based) of [total] is being sent; [results] so far. */
data class MultiPanelProgress(val index: Int, val total: Int, val panelName: String?, val results: List<PanelSendResult>)

/** Pure planning for "send to all my panels"; JVM-tested. */
object MultiPanelSend {
    /**
     * Targets are only [myPanels] (picked in GifPack) that advertise or are connected right now; a panel
     * that is merely advertising is never added. Order: strongest signal first, the panel connected before
     * the run ([connectedAddress]) last, so it is connected again when the run ends.
     */
    fun plan(myPanels: List<MyPanel>, advertising: List<NearbyPanel>, connectedAddress: String?): MultiPanelPlan {
        val rssi = advertising.associate { it.address to it.rssi }
        val (inRange, notInRange) = myPanels.partition { it.address in rssi || it.address == connectedAddress }
        val targets = inRange.sortedWith(
            compareBy<MyPanel> { it.address == connectedAddress }.thenByDescending { rssi[it.address] ?: Int.MIN_VALUE }
        )
        return MultiPanelPlan(targets, notInRange)
    }

    /**
     * Whether the run's end must call disconnect(): a link of the run to a panel other than [previous] is still
     * open ([linked]), or no link exists but one is still being opened (a scheduled GATT retry, [status]
     * "Connecting…"), which would otherwise reopen that panel later. disconnect() also cancels the retry.
     */
    fun closeLinkAfterRun(linked: String?, previous: String?, status: String): Boolean =
        if (linked != null) linked != previous else status.startsWith("Connecting")

    fun summary(results: List<PanelSendResult>): MultiPanelSummary =
        MultiPanelSummary(results.count { it.outcome.succeeded }, results.size)
}
