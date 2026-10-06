package com.batoh.core.domain.repository

import com.batoh.core.common.Result
import com.batoh.core.domain.model.Gif
import com.batoh.core.domain.model.GifFilter
import kotlinx.coroutines.flow.Flow

interface LospecRepository {
    fun searchGifs(query: String, filter: GifFilter = GifFilter(), offset: Int = 0): Flow<Result<List<Gif>>>
}
