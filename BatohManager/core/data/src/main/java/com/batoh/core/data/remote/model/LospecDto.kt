package com.batoh.core.data.remote.model

import com.batoh.core.domain.model.Gif
import com.squareup.moshi.Json

data class LospecResponseDto(
    @Json(name = "palettes") val palettes: List<LospecPaletteDto>? = null
)

data class LospecPaletteDto(
    @Json(name = "name") val name: String,
    @Json(name = "slug") val slug: String,
    @Json(name = "colors") val colors: List<String>
)

// Since Lospec has limited public GIF API, we will use a "Pixel Art" search 
// override in Giphy when Lospec is selected, or a curated list for now.
// For the sake of "adding a library", let's assume we use their palette preview 
// or a specific endpoint if found.
