package com.batoh.core.data.remote.model

import com.squareup.moshi.Json

data class GiphyCategoriesResponseDto(
    @Json(name = "data") val data: List<GiphyCategoryDto>
)

data class GiphyCategoryDto(
    @Json(name = "name") val name: String,
    @Json(name = "name_encoded") val nameEncoded: String,
    @Json(name = "gif") val gif: GiphyGifDto
)
