package com.batoh.core.data.remote.model

import com.batoh.core.domain.model.Gif
import com.batoh.core.storage.db.CachedGifEntity

fun CachedGifEntity.toDomain(): Gif {
    return Gif(
        id = id,
        title = title,
        thumbnailUrl = thumbnailUrl,
        originalUrl = originalUrl,
        mp4Url = mp4Url,
        width = width,
        height = height,
        source = source
    )
}

fun Gif.toCachedEntity(query: String): CachedGifEntity {
    return CachedGifEntity(
        id = id,
        query = query,
        title = title,
        thumbnailUrl = thumbnailUrl,
        originalUrl = originalUrl,
        mp4Url = mp4Url,
        width = width,
        height = height,
        source = source
    )
}
