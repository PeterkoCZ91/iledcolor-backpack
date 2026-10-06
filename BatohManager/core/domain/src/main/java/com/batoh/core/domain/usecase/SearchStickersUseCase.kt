package com.batoh.core.domain.usecase

import com.batoh.core.common.Result
import com.batoh.core.domain.model.Gif
import com.batoh.core.domain.model.GifFilter
import com.batoh.core.domain.model.GifType
import com.batoh.core.domain.repository.GiphyRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchStickersUseCase @Inject constructor(
    private val repository: GiphyRepository
) {
    operator fun invoke(query: String, filter: GifFilter = GifFilter(type = GifType.STICKER), offset: Int = 0): Flow<Result<List<Gif>>> {
        val stickerFilter = filter.copy(type = GifType.STICKER)
        if (query.isBlank()) return repository.getTrendingGifs(filter = stickerFilter, offset = offset)
        return repository.searchGifs(query, filter = stickerFilter, offset = offset)
    }
}
