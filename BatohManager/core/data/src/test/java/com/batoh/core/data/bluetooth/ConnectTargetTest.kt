package com.batoh.core.data.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectTargetTest {
    private val backpack = NearbyPanel("AA:AA:AA:AA:AA:01", "iledcolor-0001", PanelSpec.BACKPACK_DEFAULT, -60)
    private val ipixel = NearbyPanel("BB:BB:BB:BB:BB:02", "LED_BLE_12345678", PanelSpec(PanelFamily.IPixel, 64, 16, true), -40)

    @Test fun connectTarget_neverAutoPicksUnpickedIPixel() {
        assertNull(ConnectTarget.choose(null, null, false, listOf(ipixel)))
    }

    @Test fun connectTarget_reconnectsPickedIPixel() {
        assertEquals(ipixel.address, ConnectTarget.choose(ipixel.address, PanelFamily.IPixel, true, listOf(backpack, ipixel)))
    }

    @Test fun connectTarget_unpickedLastIPixelIsIgnored() {
        assertNull(ConnectTarget.choose(ipixel.address, PanelFamily.IPixel, false, listOf(backpack)))
    }

    @Test fun connectTarget_unpickedLastIPixelFallsBackToBackpack() {
        // "Remove from my panels" on the last panel also forgets it as last panel, so first use applies again.
        val removed = PickedPanels.unpickKeys(ipixel.address, lastAddress = ipixel.address)
        val last = ipixel.address.takeUnless { "last_device_address" in removed }
        assertEquals(backpack.address, ConnectTarget.choose(last, null, false, listOf(ipixel, backpack)))
    }

    @Test fun connectTarget_firstUsePrefersBackpack() {
        assertEquals(backpack.address, ConnectTarget.choose(null, null, false, listOf(ipixel, backpack)))
    }

    @Test fun connectTarget_lastBackpackAsToday() {
        // Today a known backpack is connected directly, advertising or not; older installs have no family key.
        assertEquals(backpack.address, ConnectTarget.choose(backpack.address, PanelFamily.Backpack, false, emptyList()))
        assertEquals(backpack.address, ConnectTarget.choose(backpack.address, null, false, listOf(ipixel)))
    }
}
