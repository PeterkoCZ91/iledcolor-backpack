package com.batoh.core.domain.usecase

import com.batoh.core.domain.repository.LocalMediaRepository
import javax.inject.Inject

/** Local collection URI of a GIF whose download work (id from [SaveGifUseCase]) has succeeded. */
class GetDownloadedGifUriUseCase @Inject constructor(
    private val repository: LocalMediaRepository
) {
    suspend operator fun invoke(workId: String): String? = repository.downloadedUri(workId)
}
