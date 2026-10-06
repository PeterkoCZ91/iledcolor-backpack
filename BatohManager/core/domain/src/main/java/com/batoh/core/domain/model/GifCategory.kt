package com.batoh.core.domain.model

data class GifCategory(
    val name: String,
    val nameEncoded: String,
    val previewGif: Gif? = null
)
