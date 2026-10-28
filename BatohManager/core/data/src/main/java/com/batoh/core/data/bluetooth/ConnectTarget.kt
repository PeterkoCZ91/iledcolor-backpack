package com.batoh.core.data.bluetooth

/** A supported panel seen in a scan; [spec] is null for an iPixel panel of unknown type (listed, not sendable). */
data class NearbyPanel(val address: String, val name: String?, val spec: PanelSpec?, val rssi: Int)

/** Which panel an automatic connect may open. An iPixel panel is never chosen unless the user picked it in GifPack. */
object ConnectTarget {
    /**
     * [lastFamily] null means the address was stored before panel families existed, i.e. a backpack.
     * A known backpack is returned whether it advertises or not (today's direct reconnect).
     */
    fun choose(lastAddress: String?, lastFamily: PanelFamily?, lastPicked: Boolean, advertising: List<NearbyPanel>): String? {
        if (lastAddress != null) {
            return if (lastFamily == PanelFamily.IPixel && !lastPicked) null else lastAddress
        }
        return advertising.firstOrNull { it.spec?.family == PanelFamily.Backpack }?.address
    }
}
