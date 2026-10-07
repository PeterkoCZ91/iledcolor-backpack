package com.batoh.core.storage.db

import androidx.room.Entity

/** Stored list of Giphy categories (including basic fields of the preview GIF). */
@Entity(
    tableName = "cached_categories",
    primaryKeys = ["nameEncoded"]
)
data class CachedCategoryEntity(
    val nameEncoded: String,
    val name: String,
    val position: Int, // position in the API response
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
