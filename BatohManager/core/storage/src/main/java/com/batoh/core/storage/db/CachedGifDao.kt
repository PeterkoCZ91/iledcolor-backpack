package com.batoh.core.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CachedGifDao {
    @Query("SELECT * FROM cached_gifs WHERE `query` = :query ORDER BY cachedAt DESC")
    suspend fun getGifsForQuery(query: String): List<CachedGifEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(gifs: List<CachedGifEntity>)

    @Query("DELETE FROM cached_gifs WHERE cachedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("DELETE FROM cached_gifs WHERE `query` = :query")
    suspend fun clearForQuery(query: String)
}
