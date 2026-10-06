package com.batoh.core.domain.usecase

import com.batoh.core.domain.repository.CategoryPreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveRecentCategoryQueriesUseCase @Inject constructor(
    private val repository: CategoryPreferencesRepository
) {
    operator fun invoke(limit: Int = 8): Flow<List<String>> = repository.observeRecentQueries(limit)
}

