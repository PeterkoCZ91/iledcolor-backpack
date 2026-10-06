package com.batoh.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface CategoryPreferencesRepository {
    fun observePinnedQueries(): Flow<List<String>>
    fun observeRecentQueries(limit: Int = 8): Flow<List<String>>
    fun observeUsedSince(sinceEpochMillis: Long, limit: Int = 8): Flow<List<String>>
    suspend fun togglePinned(query: String)
    suspend fun markCategoryUsed(query: String)
}
