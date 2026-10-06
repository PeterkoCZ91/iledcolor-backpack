package com.batoh.feature.home

/** Coarse connection state of the backpack as shown on the home tile. */
enum class BackpackLinkState { NOT_CONNECTED, CONNECTING, CONNECTED }

/**
 * Read-only snapshot of the backpack for the home screen tile.
 *
 * [deviceName] and [firmwareVersion] come from the cached advertisement of the last
 * backpack and may be known even while disconnected.
 */
data class HomeBackpackStatus(
    val state: BackpackLinkState = BackpackLinkState.NOT_CONNECTED,
    val deviceName: String? = null,
    val firmwareVersion: Int? = null
) {
    companion object {
        /**
         * Maps the free-form status string of `BluetoothLeManager.connectionStatus` to a tile state.
         * Only "Ready…" means the link is usable; the GATT setup steps before it count as connecting.
         * Scanning, errors and intermediate "Connection state: …" reports count as not connected.
         */
        fun linkStateOf(status: String): BackpackLinkState = when {
            status.startsWith("Ready") -> BackpackLinkState.CONNECTED
            status.startsWith("Connecting") || status.startsWith("Connected!") -> BackpackLinkState.CONNECTING
            else -> BackpackLinkState.NOT_CONNECTED
        }

        fun from(status: String, deviceName: String?, firmwareVersion: Int?): HomeBackpackStatus =
            HomeBackpackStatus(
                state = linkStateOf(status),
                deviceName = deviceName?.trim()?.takeIf { it.isNotEmpty() },
                firmwareVersion = firmwareVersion?.takeIf { it >= 0 }
            )
    }
}
