package com.batoh.core.domain.model

data class GifFilter(
    val query: String = "",
    val source: GifSource = GifSource.GIPHY,
    val type: GifType = GifType.GIF,
    val safety: ContentSafety = ContentSafety.FAMILY,
    val aspectRatio: AspectRatio? = AspectRatio.SQUARE
)

enum class GifSource {
    GIPHY, KLIPY, LOSPEC, ALL
}

enum class GifType(val value: String) {
    GIF("gifs"), STICKER("stickers")
}

enum class ContentSafety(val giphyValue: String, val klipyValue: String) {
    FAMILY("g", "low"),
    TEEN("pg-13", "medium"),
    ADULT("r", "off")
}

enum class AspectRatio {
    SQUARE, WIDE, TALL
}
