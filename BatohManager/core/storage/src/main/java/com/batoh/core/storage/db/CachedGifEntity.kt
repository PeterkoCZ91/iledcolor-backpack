package com.batoh.core.storage.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "cached_gifs",
    primaryKeys = ["query", "id"],
    indices = [Index("query")]
)
data class CachedGifEntity(
    val id: String,
    val query: String, // Which search it belongs to (composite key with id)
    val title: String,
    val thumbnailUrl: String,
    val originalUrl: String,
    val mp4Url: String,
    val width: Int,
    val height: Int,
    val source: String,
    val cachedAt: Long = System.currentTimeMillis(),
    val position: Int = 0 // position in the API response
)
