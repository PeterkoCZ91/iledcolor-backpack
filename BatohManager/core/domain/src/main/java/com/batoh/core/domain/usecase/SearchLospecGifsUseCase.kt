package com.batoh.core.domain.usecase

import com.batoh.core.common.Result
import com.batoh.core.domain.model.Gif
import com.batoh.core.domain.model.GifFilter
import com.batoh.core.domain.repository.LospecRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchLospecGifsUseCase @Inject constructor(
    private val repository: LospecRepository
) {
    operator fun invoke(query: String, filter: GifFilter = GifFilter(), offset: Int = 0): Flow<Result<List<Gif>>> {
        return repository.searchGifs(query, filter = filter, offset = offset)
    }
}
