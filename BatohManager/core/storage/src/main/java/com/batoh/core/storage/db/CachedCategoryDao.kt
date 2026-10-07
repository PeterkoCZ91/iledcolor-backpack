package com.batoh.core.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CachedCategoryDao {
    /** Only entries newer than [minCachedAt] (TTL), in API response order. */
    @Query("SELECT * FROM cached_categories WHERE cachedAt >= :minCachedAt ORDER BY position ASC")
    suspend fun getAll(minCachedAt: Long = 0L): List<CachedCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CachedCategoryEntity>)

    @Query("DELETE FROM cached_categories")
    suspend fun clearAll()

    /** Atomically replaces the whole stored list. */
    @Transaction
    suspend fun replaceAll(items: List<CachedCategoryEntity>) {
        clearAll()
        insertAll(items)
    }
}
