package com.batoh.core.domain.usecase

import com.batoh.core.domain.repository.CategoryPreferencesRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveMonthlyTrendingCategoryQueriesUseCase @Inject constructor(
    private val repository: CategoryPreferencesRepository
) {
    operator fun invoke(limit: Int = 8): Flow<List<String>> {
        val now = LocalDate.now()
        val monthStartEpochMillis = now
            .withDayOfMonth(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        return repository.observeUsedSince(monthStartEpochMillis, limit)
    }
}

