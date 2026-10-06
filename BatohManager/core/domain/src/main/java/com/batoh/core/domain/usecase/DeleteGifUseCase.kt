package com.batoh.core.domain.usecase

import com.batoh.core.common.Result
import com.batoh.core.domain.model.Gif
import com.batoh.core.domain.repository.LocalMediaRepository
import javax.inject.Inject

class DeleteGifUseCase @Inject constructor(
    private val repository: LocalMediaRepository
) {
    suspend operator fun invoke(gif: Gif): Result<Unit> {
        return repository.deleteGif(gif)
    }
}
