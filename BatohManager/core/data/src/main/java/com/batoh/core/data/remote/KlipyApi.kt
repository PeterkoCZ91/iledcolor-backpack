package com.batoh.core.data.remote

import com.batoh.core.data.remote.model.KlipyResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface KlipyApi {
    @GET("search")
    suspend fun searchGifs(
        @Query("key") apiKey: String,
        @Query("q") query: String,
        @Query("limit") limit: Int = 25,
        @Query("pos") pos: String? = null,
        @Query("contentfilter") contentFilter: String = "medium",
        @Query("media_filter") mediaFilter: String = "tinygifpreview,tinygif,gifpreview,gif,mp4"
    ): KlipyResponseDto

    @GET("featured")
    suspend fun getTrendingGifs(
        @Query("key") apiKey: String,
        @Query("limit") limit: Int = 25,
        @Query("pos") pos: String? = null,
        @Query("contentfilter") contentFilter: String = "medium",
        @Query("media_filter") mediaFilter: String = "tinygifpreview,tinygif,gifpreview,gif,mp4"
    ): KlipyResponseDto
}
