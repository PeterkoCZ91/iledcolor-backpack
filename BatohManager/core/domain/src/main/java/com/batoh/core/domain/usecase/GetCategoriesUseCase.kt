package com.batoh.core.domain.usecase

import com.batoh.core.common.Result
import com.batoh.core.domain.model.CuratedCategories
import com.batoh.core.domain.model.GifCategory
import com.batoh.core.domain.repository.GiphyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetCategoriesUseCase @Inject constructor(
    private val repository: GiphyRepository
) {
    operator fun invoke(): Flow<Result<List<GifCategory>>> = repository.getCategories().map { result ->
        when (result) {
            is Result.Success -> {
                // Mix Curated at the top
                Result.Success(CuratedCategories.list + result.data)
            }
            else -> result
        }
    }
}
