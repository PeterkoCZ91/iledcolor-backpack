package com.batoh.core.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CachedGifDao {
    /** Jen záznamy novější než [minCachedAt] (TTL), v pořadí z odpovědi API. */
    @Query("SELECT * FROM cached_gifs WHERE `query` = :query AND cachedAt >= :minCachedAt ORDER BY position ASC")
    suspend fun getGifsForQuery(query: String, minCachedAt: Long = 0L): List<CachedGifEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(gifs: List<CachedGifEntity>)

    @Query("DELETE FROM cached_gifs WHERE cachedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("DELETE FROM cached_gifs WHERE `query` = :query")
    suspend fun clearForQuery(query: String)

    /** Atomicky nahradí obsah dotazu novou odpovědí. */
    @Transaction
    suspend fun replaceForQuery(query: String, gifs: List<CachedGifEntity>) {
        clearForQuery(query)
        insertAll(gifs)
    }
}
