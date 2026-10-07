package com.batoh.core.domain.usecase

import com.batoh.core.common.Result
import com.batoh.core.domain.model.LibraryEntry
import com.batoh.core.domain.repository.LocalMediaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetLibraryEntriesUseCase @Inject constructor(
    private val repository: LocalMediaRepository
) {
    operator fun invoke(): Flow<Result<List<LibraryEntry>>> = repository.getLibraryEntries()
}
