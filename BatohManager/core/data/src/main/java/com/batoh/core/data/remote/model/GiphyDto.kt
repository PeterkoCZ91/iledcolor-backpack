package com.batoh.core.data.remote.model

import com.batoh.core.domain.model.Gif
import com.squareup.moshi.Json

data class GiphyResponseDto(
    @Json(name = "data") val data: List<GiphyGifDto>
)

data class GiphyGifDto(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "images") val images: GiphyImagesDto
)

data class GiphyImagesDto(
    @Json(name = "fixed_height") val fixedHeight: GiphyImageDetailsDto?,
    @Json(name = "fixed_height_still") val fixedHeightStill: GiphyImageDetailsDto?,
    @Json(name = "original_still") val originalStill: GiphyImageDetailsDto?,
    @Json(name = "original") val original: GiphyImageDetailsDto?
)

data class GiphyImageDetailsDto(
    @Json(name = "url") val url: String,
    @Json(name = "width") val width: String,
    @Json(name = "height") val height: String,
    @Json(name = "mp4") val mp4: String? = null   // Giphy serves all GIFs also as MP4
)

// Mapper extension
fun GiphyGifDto.toDomain(): Gif {
    val thumbnail = images.fixedHeightStill?.url
        ?: images.fixedHeight?.url
        ?: images.originalStill?.url
        ?: images.original?.url
        ?: ""

    return Gif(
        id = id,
        title = title,
        thumbnailUrl = thumbnail,
        originalUrl = images.original?.url ?: "",
        mp4Url = images.original?.mp4 ?: "",
        width = images.original?.width?.toIntOrNull() ?: 0,
        height = images.original?.height?.toIntOrNull() ?: 0
    )
}
