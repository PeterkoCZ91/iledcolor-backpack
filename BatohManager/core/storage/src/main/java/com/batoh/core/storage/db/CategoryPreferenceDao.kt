package com.batoh.core.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryPreferenceDao {
    @Query("SELECT query FROM category_preferences WHERE pinned = 1 ORDER BY lastUsedAt DESC")
    fun observePinnedQueries(): Flow<List<String>>

    @Query("SELECT query FROM category_preferences ORDER BY lastUsedAt DESC LIMIT :limit")
    fun observeRecentQueries(limit: Int): Flow<List<String>>

    @Query("SELECT query FROM category_preferences WHERE lastUsedAt >= :sinceEpochMillis ORDER BY lastUsedAt DESC LIMIT :limit")
    fun observeUsedSince(sinceEpochMillis: Long, limit: Int): Flow<List<String>>

    @Query("SELECT * FROM category_preferences WHERE query = :query LIMIT 1")
    suspend fun getByQuery(query: String): CategoryPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CategoryPreferenceEntity)
}
