package com.batoh.core.domain.usecase

import com.batoh.core.domain.model.Gif
import com.batoh.core.domain.model.GifFileProblem
import com.batoh.core.domain.repository.LocalMediaRepository
import javax.inject.Inject

/** Returns null when the GIF is readable and valid for editing/upload, otherwise the problem. */
class InspectGifUseCase @Inject constructor(
    private val repository: LocalMediaRepository
) {
    suspend operator fun invoke(gif: Gif): GifFileProblem? = repository.inspectGif(gif)
}
