package com.batoh.core.data.repository

import com.batoh.core.common.Result
import com.batoh.core.domain.model.Gif
import com.batoh.core.domain.model.GifFilter
import com.batoh.core.domain.model.GifSource
import com.batoh.core.domain.repository.GiphyRepository
import com.batoh.core.domain.repository.LospecRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class LospecRepositoryImpl @Inject constructor(
    private val giphyRepository: GiphyRepository
) : LospecRepository {

    override fun searchGifs(query: String, filter: GifFilter, offset: Int): Flow<Result<List<Gif>>> {
        // Lospec Mode: We force "pixel art" and "square" aspect ratio
        val enhancedQuery = if (query.isBlank()) "pixel art animation" else "pixel art $query"
        val enhancedFilter = filter.copy(
            source = GifSource.GIPHY,
            aspectRatio = com.batoh.core.domain.model.AspectRatio.SQUARE
        )
        
        return giphyRepository.searchGifs(enhancedQuery, enhancedFilter, offset).map { result ->
            when (result) {
                is Result.Success -> {
                    // Tag them as Lospec source
                    Result.Success(result.data.map { it.copy(source = "lospec") })
                }
                else -> result
            }
        }.flowOn(Dispatchers.Default)
    }
}
