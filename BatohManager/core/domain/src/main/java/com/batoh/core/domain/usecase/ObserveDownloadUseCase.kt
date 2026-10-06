package com.batoh.core.domain.usecase

import com.batoh.core.domain.model.DownloadStatus
import com.batoh.core.domain.repository.LocalMediaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveDownloadUseCase @Inject constructor(
    private val repository: LocalMediaRepository
) {
    operator fun invoke(workId: String): Flow<DownloadStatus> {
        return repository.observeDownload(workId)
    }
}
