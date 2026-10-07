package com.batoh.core.data.remote.model

import com.batoh.core.domain.model.Gif
import com.batoh.core.domain.model.GifCategory
import com.batoh.core.storage.db.CachedCategoryEntity
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

fun Gif.toCachedEntity(query: String, position: Int = 0, cachedAt: Long = System.currentTimeMillis()): CachedGifEntity {
    return CachedGifEntity(
        id = id,
        query = query,
        title = title,
        thumbnailUrl = thumbnailUrl,
        originalUrl = originalUrl,
        mp4Url = mp4Url,
        width = width,
        height = height,
        source = source,
        cachedAt = cachedAt,
        position = position
    )
}

fun GifCategory.toCachedEntity(position: Int, cachedAt: Long): CachedCategoryEntity {
    val g = previewGif
    return CachedCategoryEntity(
        nameEncoded = nameEncoded,
        name = name,
        position = position,
        previewId = g?.id.orEmpty(),
        previewTitle = g?.title.orEmpty(),
        previewThumbnailUrl = g?.thumbnailUrl.orEmpty(),
        previewOriginalUrl = g?.originalUrl.orEmpty(),
        previewMp4Url = g?.mp4Url.orEmpty(),
        previewWidth = g?.width ?: 0,
        previewHeight = g?.height ?: 0,
        previewSource = g?.source ?: "giphy",
        cachedAt = cachedAt
    )
}

fun CachedCategoryEntity.toDomain(): GifCategory {
    val hasPreview = previewThumbnailUrl.isNotBlank() || previewOriginalUrl.isNotBlank()
    return GifCategory(
        name = name,
        nameEncoded = nameEncoded,
        previewGif = if (hasPreview) Gif(
            id = previewId,
            title = previewTitle,
            thumbnailUrl = previewThumbnailUrl,
            originalUrl = previewOriginalUrl,
            mp4Url = previewMp4Url,
            width = previewWidth,
            height = previewHeight,
            source = previewSource
        ) else null
    )
}
