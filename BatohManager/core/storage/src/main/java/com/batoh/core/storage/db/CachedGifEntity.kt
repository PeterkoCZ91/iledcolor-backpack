package com.batoh.core.storage.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "cached_gifs", indices = [Index("query")])
data class CachedGifEntity(
    @PrimaryKey val id: String,
    val query: String, // Ke kterému vyhledávání patří
    val title: String,
    val thumbnailUrl: String,
    val originalUrl: String,
    val mp4Url: String,
    val width: Int,
    val height: Int,
    val source: String,
    val cachedAt: Long = System.currentTimeMillis()
)
