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
import com.batoh.core.storage.db.CachedCategoryDao
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
    private val cachedCategoryDao: CachedCategoryDao,
    @ApplicationContext private val context: Context
) : GiphyRepository {

    private val prefs by lazy {
        context.getSharedPreferences(UiPreferences.PREFS_NAME, Context.MODE_PRIVATE)
    }

    // A key entered in Settings wins; the build-time key (local.properties) is the fallback.
    private val API_KEY: String
        get() = (prefs.getString(UiPreferences.PREF_GIPHY_KEY, "") ?: "").ifBlank { BuildConfig.GIPHY_API_KEY }

    private companion object {
        // The process keeps the last successful category list in memory (without Room).
        @Volatile var lastCategories: List<GifCategory>? = null
    }

    override fun searchGifs(query: String, filter: GifFilter, offset: Int, limit: Int): Flow<Result<List<Gif>>> = flow {
        // 1. Check Cache
        if (offset == 0) {
            val cacheKey = GifCachePolicy.giphySearchKey(query, filter)
            val cached = cachedGifDao.getGifsForQuery(cacheKey, GifCachePolicy.minValidCachedAt(System.currentTimeMillis()))
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
                saveToCache(GifCachePolicy.giphySearchKey(query, filter), gifs)
            }
            
            emit(Result.Success(gifs))
        } catch (e: Exception) {
            // If network fails but we had cache, we already emitted it, but let's emit error if it was empty
            emit(Result.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getTrendingGifs(filter: GifFilter, offset: Int, limit: Int): Flow<Result<List<Gif>>> = flow {
        if (offset == 0) {
            val cacheKey = GifCachePolicy.giphyTrendingKey(filter)
            val cached = cachedGifDao.getGifsForQuery(cacheKey, GifCachePolicy.minValidCachedAt(System.currentTimeMillis()))
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
                saveToCache(GifCachePolicy.giphyTrendingKey(filter), gifs)
            }
            
            emit(Result.Success(gifs))
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getCategories(): Flow<Result<List<GifCategory>>> = flow {
        val now = System.currentTimeMillis()
        val stored = runCatching {
            cachedCategoryDao.getAll(CategoryCachePolicy.minValidCachedAt(now)).map { it.toDomain() }
        }.getOrNull()
        val lastKnown = CategoryCachePolicy.initial(lastCategories, stored)
        if (lastKnown != null) {
            lastCategories = lastKnown
            emit(Result.Success(lastKnown))
        }
        if (CategoryCachePolicy.shouldEmitLoading(lastKnown)) emit(Result.Loading)
        try {
            val response = api.getCategories(apiKey = API_KEY)
            val categories = response.data.map { dto ->
                GifCategory(
                    name = dto.name,
                    nameEncoded = dto.nameEncoded,
                    previewGif = dto.gif.toDomain()
                )
            }
            if (categories.isNotEmpty()) {
                lastCategories = categories
                val saved = System.currentTimeMillis()
                runCatching {
                    cachedCategoryDao.replaceAll(categories.mapIndexed { i, c -> c.toCachedEntity(i, saved) })
                }
            }
            emit(Result.Success(categories))
        } catch (e: Exception) {
            // Offline: prefer the last known list over an error (already emitted above).
            if (CategoryCachePolicy.shouldEmitError(lastKnown)) emit(Result.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun saveToCache(key: String, gifs: List<Gif>) {
        val now = System.currentTimeMillis()
        cachedGifDao.replaceForQuery(key, gifs.mapIndexed { i, g -> g.toCachedEntity(key, i, now) })
        cachedGifDao.deleteOlderThan(GifCachePolicy.minValidCachedAt(now))
    }
}
