package com.batoh.core.data.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Test

class PickedPanelsTest {
    private val a = "AA:AA:AA:AA:AA:01"
    private val b = "BB:BB:BB:BB:BB:02"
    private val c = "CC:CC:CC:CC:CC:03"

    // A stored `backpack_connection` snapshot: two picked panels, one only seen in a scan, one flag set to false.
    private val prefs: Map<String, Any?> = mapOf(
        PickedPanels.key(a) to true,
        "family_$a" to "IPixel",
        "name_$a" to "LED_BLE_1",
        PickedPanels.key(b) to true,
        "family_$b" to "Backpack",
        "adv_$c" to "0201",
        "ipixel_type_$c" to 0x05,
        "picked_DD:DD:DD:DD:DD:04" to false,
        "last_device_address" to c
    )

    @Test fun addresses_onlyPickedTrue() {
        assertEquals(listOf(a, b), PickedPanels.addresses(prefs))
    }

    @Test fun unpick_removesFromSet() {
        val after = prefs - PickedPanels.key(a)
        assertEquals(listOf(b), PickedPanels.addresses(after))
        // Removing a panel from "my panels" keeps what is cached about it.
        assertEquals("IPixel", after["family_$a"])
        assertEquals("LED_BLE_1", after["name_$a"])
    }

    @Test fun unpickKeys_lastPanelIsForgottenToo() {
        assertEquals(listOf(PickedPanels.key(c), "last_device_address"), PickedPanels.unpickKeys(c, lastAddress = c))
    }

    @Test fun unpickKeys_otherLastPanelIsKept() {
        assertEquals(listOf(PickedPanels.key(a)), PickedPanels.unpickKeys(a, lastAddress = c))
        assertEquals(listOf(PickedPanels.key(a)), PickedPanels.unpickKeys(a, lastAddress = null))
    }

    @Test fun addresses_ignoresInvalidAddresses() {
        assertEquals(emptyList<String>(), PickedPanels.addresses(mapOf("picked_" to true, "picked_x" to true)))
    }
}
