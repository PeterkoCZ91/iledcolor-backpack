package com.batoh.core.domain.usecase

import com.batoh.core.common.Result
import com.batoh.core.domain.model.Gif
import com.batoh.core.domain.model.GifFilter
import com.batoh.core.domain.repository.KlipyRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchKlipyGifsUseCase @Inject constructor(
    private val repository: KlipyRepository
) {
    operator fun invoke(query: String, filter: GifFilter = GifFilter(), offset: String? = null): Flow<Result<List<Gif>>> {
        return if (query.isBlank()) {
            repository.getTrendingGifs(filter = filter, offset = offset)
        } else {
            repository.searchGifs(query, filter = filter, offset = offset)
        }
    }
}
