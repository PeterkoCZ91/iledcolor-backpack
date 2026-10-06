package com.batoh.core.data.remote

import com.batoh.core.data.remote.model.GiphyCategoriesResponseDto
import com.batoh.core.data.remote.model.GiphyResponseDto
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface GiphyApi {
    @GET("search")
    suspend fun searchGifs(
        @Query("api_key") apiKey: String,
        @Query("q") query: String,
        @Query("limit") limit: Int = 25,
        @Query("offset") offset: Int = 0,
        @Query("rating") rating: String = "g",
        @Query("lang") lang: String = "cs"
    ): GiphyResponseDto

    @GET("trending")
    suspend fun getTrendingGifs(
        @Query("api_key") apiKey: String,
        @Query("limit") limit: Int = 25,
        @Query("offset") offset: Int = 0,
        @Query("rating") rating: String = "g"
    ): GiphyResponseDto

    // Stickers — full URL to bypass base URL (base is /v1/gifs/)
    @GET
    suspend fun searchStickers(
        @Url url: String = "https://api.giphy.com/v1/stickers/search",
        @Query("api_key") apiKey: String,
        @Query("q") query: String,
        @Query("limit") limit: Int = 25,
        @Query("offset") offset: Int = 0,
        @Query("rating") rating: String = "g",
        @Query("lang") lang: String = "cs"
    ): GiphyResponseDto

    @GET
    suspend fun getTrendingStickers(
        @Url url: String = "https://api.giphy.com/v1/stickers/trending",
        @Query("api_key") apiKey: String,
        @Query("limit") limit: Int = 25,
        @Query("offset") offset: Int = 0,
        @Query("rating") rating: String = "g"
    ): GiphyResponseDto

    // Categories
    @GET
    suspend fun getCategories(
        @Url url: String = "https://api.giphy.com/v1/gifs/categories",
        @Query("api_key") apiKey: String
    ): GiphyCategoriesResponseDto
}
