package com.batoh.core.domain.usecase

import com.batoh.core.common.Result
import com.batoh.core.domain.repository.LocalMediaRepository
import javax.inject.Inject

class SaveGifUseCase @Inject constructor(
    private val repository: LocalMediaRepository
) {
    suspend operator fun invoke(url: String, title: String): Result<String> {
        return repository.saveGif(url, title)
    }
}
