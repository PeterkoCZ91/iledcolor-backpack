package com.batoh.core.domain.usecase

import android.net.Uri
import com.batoh.core.common.Result
import com.batoh.core.domain.repository.LocalMediaRepository
import javax.inject.Inject

class SaveGifBytesUseCase @Inject constructor(
    private val repository: LocalMediaRepository
) {
    suspend operator fun invoke(bytes: ByteArray, title: String): Result<Uri> =
        repository.saveGifBytes(bytes, title)
}
