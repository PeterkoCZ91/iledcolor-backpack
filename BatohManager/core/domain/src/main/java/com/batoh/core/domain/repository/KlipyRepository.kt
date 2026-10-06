package com.batoh.core.domain.repository

import com.batoh.core.common.Result
import com.batoh.core.domain.model.Gif
import com.batoh.core.domain.model.GifFilter
import kotlinx.coroutines.flow.Flow

interface KlipyRepository {
    fun searchGifs(query: String, filter: GifFilter = GifFilter(), offset: String? = null, limit: Int = 25): Flow<Result<List<Gif>>>
    fun getTrendingGifs(filter: GifFilter = GifFilter(), offset: String? = null, limit: Int = 25): Flow<Result<List<Gif>>>
}
