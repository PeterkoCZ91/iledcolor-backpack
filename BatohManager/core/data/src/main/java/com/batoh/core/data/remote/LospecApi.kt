package com.batoh.core.data.remote

import com.batoh.core.data.remote.model.LospecResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface LospecApi {
    @GET("api/v1/palettes")
    suspend fun getPalettes(
        @Query("page") page: Int = 1
    ): LospecResponseDto
}
