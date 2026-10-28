package com.batoh.core.data.bluetooth

enum class PanelFamily { Backpack, IPixel }

/** What GifPack needs to know about a panel to render for it; [verified] = size confirmed on real hardware. */
data class PanelSpec(
    val family: PanelFamily,
    val width: Int,
    val height: Int,
    val verified: Boolean,
) {
    companion object {
        val BACKPACK_DEFAULT = PanelSpec(PanelFamily.Backpack, 64, 64, true)
    }
}
