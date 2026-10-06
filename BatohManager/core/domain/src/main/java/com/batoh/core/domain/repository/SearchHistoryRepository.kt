package com.batoh.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface SearchHistoryRepository {
    fun getRecentHistory(): Flow<List<String>>
    suspend fun saveQuery(query: String)
    suspend fun deleteQuery(query: String)
    suspend fun clearHistory()
}
