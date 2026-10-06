package com.batoh.core.domain.model

data class Gif(
    val id: String,
    val title: String,
    val thumbnailUrl: String, // Fixed height small
    val originalUrl: String,  // Full GIF for download
    val mp4Url: String = "",  // Giphy MP4 version (better for conversion to 64×64)
    val width: Int,
    val height: Int,
    val source: String = "giphy" // "giphy", "sticker", "klipy"
)
