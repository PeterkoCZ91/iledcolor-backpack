package com.batoh.core.data.remote.model

import com.batoh.core.domain.model.Gif
import com.squareup.moshi.Json

data class KlipyResponseDto(
    @Json(name = "results") val results: List<KlipyGifDto>,
    @Json(name = "next") val next: String?
)

data class KlipyGifDto(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "media_formats") val mediaFormats: KlipyMediaFormatsDto
)

data class KlipyMediaFormatsDto(
    @Json(name = "tinygifpreview") val tinyGifPreview: KlipyMediaDto?,
    @Json(name = "tinygif") val tinyGif: KlipyMediaDto?,
    @Json(name = "gifpreview") val gifPreview: KlipyMediaDto?,
    @Json(name = "gif") val gif: KlipyMediaDto?,
    @Json(name = "mp4") val mp4: KlipyMediaDto?
)

data class KlipyMediaDto(
    @Json(name = "url") val url: String,
    @Json(name = "dims") val dims: List<Int>? = null
)

fun KlipyGifDto.toDomain(): Gif {
    val thumb = mediaFormats.tinyGifPreview
        ?: mediaFormats.gifPreview
        ?: mediaFormats.tinyGif
        ?: mediaFormats.gif
    val original = mediaFormats.gif
    return Gif(
        id = "klipy_$id",
        title = title,
        thumbnailUrl = thumb?.url ?: "",
        originalUrl = original?.url ?: "",
        mp4Url = mediaFormats.mp4?.url ?: "",
        width = original?.dims?.getOrNull(0) ?: thumb?.dims?.getOrNull(0) ?: 0,
        height = original?.dims?.getOrNull(1) ?: thumb?.dims?.getOrNull(1) ?: 0,
        source = "klipy"
    )
}
