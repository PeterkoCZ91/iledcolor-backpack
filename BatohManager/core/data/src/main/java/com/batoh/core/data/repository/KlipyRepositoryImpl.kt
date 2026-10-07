package com.batoh.core.data.repository

import android.content.Context
import com.batoh.core.common.Result
import com.batoh.core.common.UiPreferences
import com.batoh.core.data.BuildConfig
import com.batoh.core.data.remote.KlipyApi
import com.batoh.core.data.remote.RateLimiter
import com.batoh.core.data.remote.model.toCachedEntity
import com.batoh.core.data.remote.model.toDomain
import com.batoh.core.domain.model.*
import com.batoh.core.domain.repository.KlipyRepository
import com.batoh.core.storage.db.CachedGifDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class KlipyRepositoryImpl @Inject constructor(
    private val api: KlipyApi,
    private val cachedGifDao: CachedGifDao,
    @ApplicationContext private val context: Context
) : KlipyRepository {

    private val prefs by lazy {
        context.getSharedPreferences(UiPreferences.PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val apiKey: String
        get() {
            val fromPrefs = prefs.getString(UiPreferences.PREF_KLIPY_KEY, "") ?: ""
            return fromPrefs.ifBlank { BuildConfig.KLIPY_API_KEY }
        }

    // Klipy API limit: ~100 req/min, we cap at 80 for safety
    private val rateLimiter = RateLimiter(maxRequests = 80, windowMs = 60_000L)

    override fun searchGifs(query: String, filter: GifFilter, offset: String?, limit: Int): Flow<Result<List<Gif>>> = flow {
        if (offset == null) {
            val cacheKey = GifCachePolicy.klipySearchKey(query, filter)
            val cached = cachedGifDao.getGifsForQuery(cacheKey, GifCachePolicy.minValidCachedAt(System.currentTimeMillis()))
            if (cached.isNotEmpty()) {
                emit(Result.Success(cached.map { it.toDomain() }))
            }
        }

        emit(Result.Loading)
        if (apiKey.isBlank() || apiKey == "MISSING_KEY") {
            emit(Result.Error(Exception("Chybí Klipy API Klíč.")))
            return@flow
        }
        try {
            rateLimiter.acquire()
            val response = api.searchGifs(
                apiKey = apiKey,
                query = query,
                limit = limit,
                pos = offset,
                contentFilter = filter.safety.klipyValue
            )
            val gifs = response.results.map { it.toDomain() }.smartFilterAndRank(filter)

            if (offset == null && gifs.isNotEmpty()) {
                saveToCache(GifCachePolicy.klipySearchKey(query, filter), gifs)
            }

            emit(Result.Success(gifs))
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getTrendingGifs(filter: GifFilter, offset: String?, limit: Int): Flow<Result<List<Gif>>> = flow {
        if (offset == null) {
            val cacheKey = GifCachePolicy.klipyTrendingKey(filter)
            val cached = cachedGifDao.getGifsForQuery(cacheKey, GifCachePolicy.minValidCachedAt(System.currentTimeMillis()))
            if (cached.isNotEmpty()) {
                emit(Result.Success(cached.map { it.toDomain() }))
            }
        }

        emit(Result.Loading)
        try {
            rateLimiter.acquire()
            val response = api.getTrendingGifs(
                apiKey = apiKey,
                limit = limit,
                pos = offset,
                contentFilter = filter.safety.klipyValue
            )
            val gifs = response.results.map { it.toDomain() }.smartFilterAndRank(filter)

            if (offset == null && gifs.isNotEmpty()) {
                saveToCache(GifCachePolicy.klipyTrendingKey(filter), gifs)
            }

            emit(Result.Success(gifs))
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun saveToCache(key: String, gifs: List<Gif>) {
        val now = System.currentTimeMillis()
        cachedGifDao.replaceForQuery(key, gifs.mapIndexed { i, g -> g.toCachedEntity(key, i, now) })
        cachedGifDao.deleteOlderThan(GifCachePolicy.minValidCachedAt(now))
    }
}
