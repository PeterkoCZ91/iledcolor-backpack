package com.batoh.core.domain

import com.batoh.core.common.Result
import com.batoh.core.domain.model.CuratedCategories
import com.batoh.core.domain.model.Gif
import com.batoh.core.domain.model.GifCategory
import com.batoh.core.domain.model.GifFilter
import com.batoh.core.domain.model.LibraryEntry
import com.batoh.core.domain.repository.CategoryPreferencesRepository
import com.batoh.core.domain.repository.GiphyRepository
import com.batoh.core.domain.repository.LocalMediaRepository
import com.batoh.core.domain.usecase.GetCategoriesUseCase
import com.batoh.core.domain.usecase.GetLibraryEntriesUseCase
import com.batoh.core.domain.usecase.ObserveMonthlyTrendingCategoryQueriesUseCase
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UseCasesTest {
    private class FakeGiphy(private val categories: Result<List<GifCategory>>) : GiphyRepository {
        override fun searchGifs(query: String, filter: GifFilter, offset: Int, limit: Int): Flow<Result<List<Gif>>> =
            error("unused")
        override fun getTrendingGifs(filter: GifFilter, offset: Int, limit: Int): Flow<Result<List<Gif>>> =
            error("unused")
        override fun getCategories(): Flow<Result<List<GifCategory>>> = flowOf(categories)
    }

    @Test fun categoriesPutCuratedFirstAndKeepRemoteOrder() = runBlocking {
        val remote = listOf(GifCategory("A", "a"), GifCategory("B", "b"))
        val result = GetCategoriesUseCase(FakeGiphy(Result.Success(remote)))().first()
        val data = (result as Result.Success).data
        assertEquals(CuratedCategories.list.size + 2, data.size)
        assertEquals(CuratedCategories.list, data.take(CuratedCategories.list.size))
        assertEquals(remote, data.takeLast(2))
    }

    @Test fun categoriesPassErrorAndLoadingThroughUnchanged() = runBlocking {
        val error = Result.Error(IllegalStateException("x"), "msg")
        assertSame(error, GetCategoriesUseCase(FakeGiphy(error))().first())
        assertSame(Result.Loading, GetCategoriesUseCase(FakeGiphy(Result.Loading))().first())
    }

    @Test fun libraryEntriesDelegateToRepositoryWithoutChange() = runBlocking {
        val entries = Result.Success(
            listOf(LibraryEntry(Gif("1", "t", "th", "orig", width = 64, height = 64), 10L, 20L))
        )
        val repo = Proxy.newProxyInstance(
            LocalMediaRepository::class.java.classLoader,
            arrayOf(LocalMediaRepository::class.java)
        ) { _, method, _ ->
            if (method.name == "getLibraryEntries") flowOf(entries) else error("unexpected ${method.name}")
        } as LocalMediaRepository
        assertSame(entries, GetLibraryEntriesUseCase(repo)().first())
    }

    @Test fun monthlyTrendingUsesStartOfCurrentMonthAndForwardsLimit() = runBlocking {
        var since = -1L
        var usedLimit = -1
        val repo = object : CategoryPreferencesRepository {
            override fun observePinnedQueries() = error("unused")
            override fun observeRecentQueries(limit: Int) = error("unused")
            override fun observeUsedSince(sinceEpochMillis: Long, limit: Int): Flow<List<String>> {
                since = sinceEpochMillis
                usedLimit = limit
                return flowOf(listOf("neon"))
            }
            override suspend fun togglePinned(query: String) = error("unused")
            override suspend fun markCategoryUsed(query: String) = error("unused")
        }
        val before = LocalDate.now()
        assertEquals(listOf("neon"), ObserveMonthlyTrendingCategoryQueriesUseCase(repo)(5).first())
        val after = LocalDate.now()
        assertEquals(5, usedLimit)
        val sinceDate = java.time.Instant.ofEpochMilli(since).atZone(ZoneId.systemDefault()).toLocalDate()
        assertEquals(1, sinceDate.dayOfMonth)
        assertTrue(sinceDate == before.withDayOfMonth(1) || sinceDate == after.withDayOfMonth(1))
        ObserveMonthlyTrendingCategoryQueriesUseCase(repo)().first()
        assertEquals(8, usedLimit)
    }
}
