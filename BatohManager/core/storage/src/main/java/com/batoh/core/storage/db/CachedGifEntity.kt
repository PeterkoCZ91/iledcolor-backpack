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
    val query: String, // Ke kterému vyhledávání patří (složený klíč s id)
    val title: String,
    val thumbnailUrl: String,
    val originalUrl: String,
    val mp4Url: String,
    val width: Int,
    val height: Int,
    val source: String,
    val cachedAt: Long = System.currentTimeMillis(),
    val position: Int = 0 // pořadí v odpovědi API
)
