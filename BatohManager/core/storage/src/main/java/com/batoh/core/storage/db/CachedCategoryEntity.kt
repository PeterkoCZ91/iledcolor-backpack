package com.batoh.core.storage.db

import androidx.room.Entity

/** Uložený seznam kategorií Giphy (včetně základních polí náhledového GIFu). */
@Entity(
    tableName = "cached_categories",
    primaryKeys = ["nameEncoded"]
)
data class CachedCategoryEntity(
    val nameEncoded: String,
    val name: String,
    val position: Int, // pořadí v odpovědi API
    val previewId: String,
    val previewTitle: String,
    val previewThumbnailUrl: String,
    val previewOriginalUrl: String,
    val previewMp4Url: String,
    val previewWidth: Int,
    val previewHeight: Int,
    val previewSource: String,
    val cachedAt: Long
)
