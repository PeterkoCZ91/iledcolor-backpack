package com.batoh.core.domain.usecase

import com.batoh.core.common.Result
import com.batoh.core.domain.model.Gif
import com.batoh.core.domain.repository.LocalMediaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetLocalGifsUseCase @Inject constructor(
    private val repository: LocalMediaRepository
) {
    operator fun invoke(): Flow<Result<List<Gif>>> {
        return repository.getLocalGifs()
    }
}
