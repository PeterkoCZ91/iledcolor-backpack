package com.batoh.core.data.bluetooth

/** A panel the user picked in GifPack ("my panels"); [spec] is null while its family or type is unknown. */
data class MyPanel(val address: String, val name: String?, val spec: PanelSpec?)

/**
 * "My panels" as stored in the `backpack_connection` preferences: exactly the addresses whose `picked_<address>`
 * flag is true. Panels only seen in a scan are never part of it. Pure so it is unit-testable.
 */
object PickedPanels {
    private const val PREFIX = "picked_"

    fun key(address: String): String = PREFIX + address

    /**
     * Preference keys to remove when [address] leaves "my panels": its picked flag and, when it is the last panel,
     * `last_device_address` too, so the automatic connect falls back to an advertising backpack.
     */
    fun unpickKeys(address: String, lastAddress: String?): List<String> =
        listOfNotNull(key(address), "last_device_address".takeIf { address == lastAddress })

    /** Picked addresses in a stable order; other keys and flags set to false are ignored. */
    fun addresses(all: Map<String, *>): List<String> = all
        .filter { (key, value) -> key.startsWith(PREFIX) && value == true }
        .keys.map { it.removePrefix(PREFIX) }
        .filter { ADDRESS.matches(it) }
        .sorted()

    private val ADDRESS = Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}")
}
