package com.batoh.core.data.repository

import com.batoh.core.common.Result
import com.batoh.core.data.BuildConfig
import com.batoh.core.data.remote.GiphyApi
import com.batoh.core.data.remote.model.toCachedEntity
import com.batoh.core.data.remote.model.toDomain
import com.batoh.core.domain.model.Gif
import com.batoh.core.domain.model.GifCategory
import com.batoh.core.domain.model.GifFilter
import com.batoh.core.domain.model.GifType
import com.batoh.core.domain.repository.GiphyRepository
import com.batoh.core.storage.db.CachedGifDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import android.content.Context
import com.batoh.core.common.UiPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class GiphyRepositoryImpl @Inject constructor(
    private val api: GiphyApi,
    private val cachedGifDao: CachedGifDao,
    @ApplicationContext private val context: Context
) : GiphyRepository {

    private val prefs by lazy {
        context.getSharedPreferences(UiPreferences.PREFS_NAME, Context.MODE_PRIVATE)
    }

    // A key entered in Settings wins; the build-time key (local.properties) is the fallback.
    private val API_KEY: String
        get() = (prefs.getString(UiPreferences.PREF_GIPHY_KEY, "") ?: "").ifBlank { BuildConfig.GIPHY_API_KEY }

    override fun searchGifs(query: String, filter: GifFilter, offset: Int, limit: Int): Flow<Result<List<Gif>>> = flow {
        // 1. Check Cache
        if (offset == 0) {
            val cached = cachedGifDao.getGifsForQuery("giphy_$query")
            if (cached.isNotEmpty()) {
                emit(Result.Success(cached.map { it.toDomain() }))
            }
        }

        emit(Result.Loading)
        try {
            val response = if (filter.type == GifType.STICKER) {
                api.searchStickers(
                    apiKey = API_KEY,
                    query = query,
                    limit = limit,
                    offset = offset,
                    rating = filter.safety.giphyValue
                )
            } else {
                api.searchGifs(
                    apiKey = API_KEY,
                    query = query,
                    limit = limit,
                    offset = offset,
                    rating = filter.safety.giphyValue
                )
            }
            
            val gifs = response.data.map { 
                it.toDomain().copy(source = if (filter.type == GifType.STICKER) "sticker" else "giphy") 
            }.smartFilterAndRank(filter)
            
            // 2. Update Cache if it's the first page
            if (offset == 0 && gifs.isNotEmpty()) {
                cachedGifDao.clearForQuery("giphy_$query")
                cachedGifDao.insertAll(gifs.map { it.toCachedEntity("giphy_$query") })
            }
            
            emit(Result.Success(gifs))
        } catch (e: Exception) {
            // If network fails but we had cache, we already emitted it, but let's emit error if it was empty
            emit(Result.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getTrendingGifs(filter: GifFilter, offset: Int, limit: Int): Flow<Result<List<Gif>>> = flow {
        if (offset == 0) {
            val cached = cachedGifDao.getGifsForQuery("giphy_trending")
            if (cached.isNotEmpty()) {
                emit(Result.Success(cached.map { it.toDomain() }))
            }
        }

        emit(Result.Loading)
        try {
            val response = if (filter.type == GifType.STICKER) {
                api.getTrendingStickers(
                    apiKey = API_KEY,
                    limit = limit,
                    offset = offset,
                    rating = filter.safety.giphyValue
                )
            } else {
                api.getTrendingGifs(
                    apiKey = API_KEY,
                    limit = limit,
                    offset = offset,
                    rating = filter.safety.giphyValue
                )
            }
            
            val gifs = response.data.map { 
                it.toDomain().copy(source = if (filter.type == GifType.STICKER) "sticker" else "giphy") 
            }.smartFilterAndRank(filter)

            if (offset == 0 && gifs.isNotEmpty()) {
                cachedGifDao.clearForQuery("giphy_trending")
                cachedGifDao.insertAll(gifs.map { it.toCachedEntity("giphy_trending") })
            }
            
            emit(Result.Success(gifs))
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getCategories(): Flow<Result<List<GifCategory>>> = flow {
        emit(Result.Loading)
        try {
            val response = api.getCategories(apiKey = API_KEY)
            emit(Result.Success(response.data.map { dto ->
                GifCategory(
                    name = dto.name,
                    nameEncoded = dto.nameEncoded,
                    previewGif = dto.gif.toDomain()
                )
            }))
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }.flowOn(Dispatchers.IO)

}
