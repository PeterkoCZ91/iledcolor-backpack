package com.batoh.core.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SearchHistoryEntity::class,
        CachedGifEntity::class,
        CategoryPreferenceEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class BatohDatabase : RoomDatabase() {
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun cachedGifDao(): CachedGifDao
    abstract fun categoryPreferenceDao(): CategoryPreferenceDao
}
