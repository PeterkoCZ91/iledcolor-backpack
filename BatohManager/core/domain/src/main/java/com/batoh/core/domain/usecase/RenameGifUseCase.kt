package com.batoh.core.domain.usecase

import com.batoh.core.common.Result
import com.batoh.core.domain.model.Gif
import com.batoh.core.domain.repository.LocalMediaRepository
import javax.inject.Inject

class RenameGifUseCase @Inject constructor(
    private val repository: LocalMediaRepository
) {
    /** Returns the new display name; see [LocalMediaRepository.renameGif] for errors. */
    suspend operator fun invoke(gif: Gif, newName: String): Result<String> =
        repository.renameGif(gif, newName)
}
