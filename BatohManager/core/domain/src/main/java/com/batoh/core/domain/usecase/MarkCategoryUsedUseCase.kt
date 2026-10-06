package com.batoh.core.domain.usecase

import com.batoh.core.domain.repository.CategoryPreferencesRepository
import javax.inject.Inject

class MarkCategoryUsedUseCase @Inject constructor(
    private val repository: CategoryPreferencesRepository
) {
    suspend operator fun invoke(query: String) = repository.markCategoryUsed(query)
}

