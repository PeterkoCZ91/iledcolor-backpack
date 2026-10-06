package com.batoh.core.domain.usecase

import android.net.Uri
import com.batoh.core.domain.repository.LocalMediaRepository
import javax.inject.Inject

class ImportGifUseCase @Inject constructor(private val repository: LocalMediaRepository) {
    suspend operator fun invoke(uri: Uri) = repository.importGif(uri)
}
