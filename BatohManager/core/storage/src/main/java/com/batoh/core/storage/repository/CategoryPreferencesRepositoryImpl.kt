package com.batoh.core.storage.repository

import com.batoh.core.domain.repository.CategoryPreferencesRepository
import com.batoh.core.storage.db.CategoryPreferenceDao
import com.batoh.core.storage.db.CategoryPreferenceEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class CategoryPreferencesRepositoryImpl @Inject constructor(
    private val dao: CategoryPreferenceDao
) : CategoryPreferencesRepository {

    override fun observePinnedQueries(): Flow<List<String>> = dao.observePinnedQueries()

    override fun observeRecentQueries(limit: Int): Flow<List<String>> = dao.observeRecentQueries(limit)

    override fun observeUsedSince(sinceEpochMillis: Long, limit: Int): Flow<List<String>> =
        dao.observeUsedSince(sinceEpochMillis, limit)

    override suspend fun togglePinned(query: String) {
        val normalized = query.trim()
        if (normalized.isBlank()) return

        val existing = dao.getByQuery(normalized)
        val now = System.currentTimeMillis()
        val updated = if (existing == null) {
            CategoryPreferenceEntity(
                query = normalized,
                pinned = true,
                lastUsedAt = now
            )
        } else {
            existing.copy(
                pinned = !existing.pinned,
                lastUsedAt = if (existing.lastUsedAt == 0L) now else existing.lastUsedAt
            )
        }
        dao.upsert(updated)
    }

    override suspend fun markCategoryUsed(query: String) {
        val normalized = query.trim()
        if (normalized.isBlank()) return

        val existing = dao.getByQuery(normalized)
        dao.upsert(
            if (existing == null) {
                CategoryPreferenceEntity(
                    query = normalized,
                    pinned = false,
                    lastUsedAt = System.currentTimeMillis()
                )
            } else {
                existing.copy(lastUsedAt = System.currentTimeMillis())
            }
        )
    }
}
