package com.batoh.core.storage.repository

import com.batoh.core.domain.repository.SearchHistoryRepository
import com.batoh.core.storage.db.SearchHistoryDao
import com.batoh.core.storage.db.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SearchHistoryRepositoryImpl @Inject constructor(
    private val dao: SearchHistoryDao
) : SearchHistoryRepository {
    override fun getRecentHistory(): Flow<List<String>> = 
        dao.getRecentHistory().map { list -> list.map { it.query } }

    override suspend fun saveQuery(query: String) {
        if (query.isBlank()) return
        dao.insert(SearchHistoryEntity(query.trim()))
    }

    override suspend fun deleteQuery(query: String) = dao.delete(query)
    override suspend fun clearHistory() = dao.clearAll()
}
