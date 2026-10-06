package com.batoh.core.domain.usecase

import com.batoh.core.domain.repository.CategoryPreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObservePinnedCategoryQueriesUseCase @Inject constructor(
    private val repository: CategoryPreferencesRepository
) {
    operator fun invoke(): Flow<List<String>> = repository.observePinnedQueries()
}

