package com.batoh.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batoh.core.common.Result
import com.batoh.core.domain.model.Gif
import com.batoh.core.domain.model.GifCategory
import com.batoh.core.domain.repository.GiphyRepository
import com.batoh.core.domain.usecase.GetCategoriesUseCase
import com.batoh.core.domain.usecase.MarkCategoryUsedUseCase
import com.batoh.core.domain.usecase.ObserveMonthlyTrendingCategoryQueriesUseCase
import com.batoh.core.domain.usecase.ObservePinnedCategoryQueriesUseCase
import com.batoh.core.domain.usecase.ObserveRecentCategoryQueriesUseCase
import com.batoh.core.domain.usecase.TogglePinnedCategoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

sealed interface CategoriesUiState {
    object Loading : CategoriesUiState
    data class Success(
        val pinnedCategories: List<GifCategory>,
        val monthlyTrendingCategories: List<GifCategory>,
        val recentCategories: List<GifCategory>,
        val categories: List<GifCategory>,
        val pinnedQueries: Set<String>
    ) : CategoriesUiState
    data class Error(val error: LoadError) : CategoriesUiState
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModel @Inject constructor(
    getCategoriesUseCase: GetCategoriesUseCase,
    observePinnedCategoryQueriesUseCase: ObservePinnedCategoryQueriesUseCase,
    observeMonthlyTrendingCategoryQueriesUseCase: ObserveMonthlyTrendingCategoryQueriesUseCase,
    observeRecentCategoryQueriesUseCase: ObserveRecentCategoryQueriesUseCase,
    private val markCategoryUsedUseCase: MarkCategoryUsedUseCase,
    private val togglePinnedCategoryUseCase: TogglePinnedCategoryUseCase,
    private val giphyRepository: GiphyRepository
) : ViewModel() {

    private val categoryReloads = MutableStateFlow(0)
    private var retryInFlight = false
    private val _previews = MutableStateFlow<Map<String, Gif>>(emptyMap())
    private var previewsFetched = false

    val uiState: StateFlow<CategoriesUiState> = combine(
        categoryReloads.flatMapLatest {
            getCategoriesUseCase().onEach { result ->
                if (result is Result.Success || result is Result.Error) retryInFlight = false
            }
        },
        observePinnedCategoryQueriesUseCase(),
        observeMonthlyTrendingCategoryQueriesUseCase(limit = 10),
        observeRecentCategoryQueriesUseCase(limit = 8),
        _previews
    ) { result, pinnedQueries, monthlyTrendingQueries, recentQueries, previews ->
            when (result) {
                is Result.Loading -> CategoriesUiState.Loading
                is Result.Success -> {
                    val allCategories = result.data.distinctBy { normalizeQuery(it.nameEncoded) }
                        .map { it.enrichWithPreview(previews) }
                    val byQuery = allCategories.associateBy { normalizeQuery(it.nameEncoded) }

                    val pinnedSet = pinnedQueries.map(::normalizeQuery).toSet()
                    val pinnedCategories = pinnedQueries
                        .map { query -> byQuery[normalizeQuery(query)] ?: query.toCategoryFallback().enrichWithPreview(previews) }
                        .distinctBy { normalizeQuery(it.nameEncoded) }

                    val monthlyTrendingCategories = monthlyTrendingQueries
                        .map { query -> byQuery[normalizeQuery(query)] ?: query.toCategoryFallback().enrichWithPreview(previews) }
                        .distinctBy { normalizeQuery(it.nameEncoded) }
                        .filterNot { normalizeQuery(it.nameEncoded) in pinnedSet }
                        .take(10)

                    val monthlyTrendingSet =
                        monthlyTrendingCategories.map { normalizeQuery(it.nameEncoded) }.toSet()

                    val recentCategories = recentQueries
                        .map { query -> byQuery[normalizeQuery(query)] ?: query.toCategoryFallback().enrichWithPreview(previews) }
                        .distinctBy { normalizeQuery(it.nameEncoded) }
                        .filterNot {
                            val key = normalizeQuery(it.nameEncoded)
                            key in pinnedSet || key in monthlyTrendingSet
                        }
                        .take(8)

                    val recentSet = recentCategories.map { normalizeQuery(it.nameEncoded) }.toSet()
                    val discoverCategories = allCategories.filter { category ->
                        val key = normalizeQuery(category.nameEncoded)
                        key !in pinnedSet && key !in monthlyTrendingSet && key !in recentSet
                    }

                    if (!previewsFetched) {
                        previewsFetched = true
                        fetchMissingPreviews(result.data)
                    }

                    CategoriesUiState.Success(
                        pinnedCategories = pinnedCategories,
                        monthlyTrendingCategories = monthlyTrendingCategories,
                        recentCategories = recentCategories,
                        categories = discoverCategories,
                        pinnedQueries = pinnedSet
                    )
                }
                is Result.Error -> CategoriesUiState.Error(result.exception.toLoadError())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CategoriesUiState.Loading
        )

    fun onCategoryOpened(query: String) {
        viewModelScope.launch {
            markCategoryUsedUseCase(query)
        }
    }

    fun retry() {
        if (uiState.value !is CategoriesUiState.Error || retryInFlight) return
        retryInFlight = true
        categoryReloads.update { it + 1 }
    }

    fun togglePinned(query: String) {
        viewModelScope.launch {
            togglePinnedCategoryUseCase(query)
        }
    }

    private fun fetchMissingPreviews(categories: List<GifCategory>) {
        viewModelScope.launch {
            val semaphore = Semaphore(6)
            categories.filter { it.previewGif == null }
                .map { category ->
                    async {
                        semaphore.withPermit {
                            try {
                                val result = giphyRepository.searchGifs(
                                    query = category.nameEncoded,
                                    limit = 1
                                ).first { it is Result.Success || it is Result.Error }
                                if (result is Result.Success && result.data.isNotEmpty()) {
                                    _previews.update { map ->
                                        map + (normalizeQuery(category.nameEncoded) to result.data.first())
                                    }
                                }
                            } catch (_: Exception) {
                                // Ignore preview fetch failures
                            }
                        }
                    }
                }
                .awaitAll()
        }
    }

    private fun GifCategory.enrichWithPreview(previews: Map<String, Gif>): GifCategory {
        if (previewGif != null) return this
        val preview = previews[normalizeQuery(nameEncoded)] ?: return this
        return copy(previewGif = preview)
    }

    private fun normalizeQuery(query: String): String = query.trim().lowercase(Locale.ROOT)

    private fun String.toCategoryFallback(): GifCategory {
        val normalized = trim()
        val words = normalized
            .replace('_', ' ')
            .replace('-', ' ')
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        val displayName = words.joinToString(" ") { word ->
            word.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.ROOT) else ch.toString()
            }
        }.ifBlank { normalized }

        return GifCategory(
            name = displayName,
            nameEncoded = normalized
        )
    }
}
