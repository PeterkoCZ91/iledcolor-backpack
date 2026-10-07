package com.batoh.feature.search

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batoh.core.common.Result
import com.batoh.core.common.UiPreferences
import com.batoh.core.domain.model.*
import com.batoh.core.domain.repository.SearchHistoryRepository
import com.batoh.core.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SearchUiState {
    object Loading : SearchUiState
    data class Success(
        val gifs: List<Gif>,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = true,
        val isTrending: Boolean = false,
        val isPersonalized: Boolean = false,
        val loadMoreError: LoadError? = null
    ) : SearchUiState
    data class Error(val error: LoadError) : SearchUiState
    object Empty : SearchUiState
}

private const val PAGE_SIZE = 25

private fun Gif.uniqueKey(): String = "$source|$id|$originalUrl"

@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val savedStateHandle: SavedStateHandle,
    private val searchGifsUseCase: SearchGifsUseCase,
    private val searchStickersUseCase: SearchStickersUseCase,
    private val searchKlipyGifsUseCase: SearchKlipyGifsUseCase,
    private val searchLospecGifsUseCase: SearchLospecGifsUseCase,
    private val saveGifUseCase: SaveGifUseCase,
    private val observeDownloadUseCase: ObserveDownloadUseCase,
    private val searchHistoryRepository: SearchHistoryRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow(
        savedStateHandle.get<String>("initialQuery") ?: ""
    )
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filter = MutableStateFlow(GifFilter())
    val filter: StateFlow<GifFilter> = _filter.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Loading)
    val uiState: StateFlow<SearchUiState> = _uiState

    private val downloads = SearchDownloads(
        scope = viewModelScope,
        save = { url, title -> saveGifUseCase(url, title) },
        observe = { id -> observeDownloadUseCase(id) },
        restoredWorkIds = savedStateHandle.get<HashMap<String, String>>("download_work_ids") ?: emptyMap(),
        onWorkQueued = { key, id ->
            val ids = HashMap(savedStateHandle.get<HashMap<String, String>>("download_work_ids") ?: emptyMap())
            ids[key] = id
            savedStateHandle["download_work_ids"] = ids
        }
    )
    val saveStates = downloads.states

    val searchHistory: StateFlow<List<String>> = searchHistoryRepository.getRecentHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var currentOffset = 0
    private var klipyNextOffset: String? = null
    private var failedPageOffset: Int? = null
    private var failedKlipyOffset: String? = null
    private var accumulatedGifs = listOf<Gif>()
    private val requests = SearchRequestOwner()

    init {
        @OptIn(FlowPreview::class)
        combine(_searchQuery, _filter) { query, filter ->
            query to filter
        }
        .debounce { (query, _) -> if (query.isBlank()) 0L else 350L }
        .distinctUntilChanged()
        .onEach { (query, filter) ->
            resetAndSearch(query, filter)
        }
        .launchIn(viewModelScope)
    }

    fun onQueryChanged(query: String) {
        if (_searchQuery.value == query) return
        invalidateSearch()
        _searchQuery.value = query
    }

    fun clearHistory() {
        viewModelScope.launch { searchHistoryRepository.clearHistory() }
    }

    fun deleteHistoryItem(query: String) {
        viewModelScope.launch { searchHistoryRepository.deleteQuery(query) }
    }

    fun setContentType(type: GifType) {
        if (_filter.value.type == type) return
        invalidateSearch()
        _filter.value = _filter.value.copy(
            type = type,
            source = if (type == GifType.STICKER && (_filter.value.source == GifSource.KLIPY || _filter.value.source == GifSource.LOSPEC)) {
                GifSource.GIPHY
            } else {
                _filter.value.source
            }
        )
    }

    fun setGifSource(source: GifSource) {
        if (_filter.value.source == source) return
        invalidateSearch()
        _filter.value = _filter.value.copy(
            source = source,
            type = if ((source == GifSource.KLIPY || source == GifSource.LOSPEC) && _filter.value.type == GifType.STICKER) {
                GifType.GIF
            } else {
                _filter.value.type
            }
        )
    }

    fun setAspectRatio(ratio: AspectRatio?) {
        if (_filter.value.aspectRatio == ratio) return
        invalidateSearch()
        _filter.value = _filter.value.copy(aspectRatio = ratio)
    }

    fun saveGif(gif: Gif) = downloads.save(gif)

    private fun invalidateSearch() {
        requests.invalidate()
        _uiState.value = SearchUiState.Loading
    }

    fun retry() {
        resetAndSearch(_searchQuery.value, _filter.value)
    }

    fun loadMore() = loadMoreInternal(retry = false)
    fun retryLoadMore() = loadMoreInternal(retry = true)

    private fun loadMoreInternal(retry: Boolean) {
        val state = _uiState.value as? SearchUiState.Success ?: return
        if (state.isLoadingMore || !state.hasMore || requests.pageRunning || requests.searchRunning || (!retry && state.loadMoreError != null)) return
        val query = _searchQuery.value
        val filter = _filter.value
        val nextOffset = if (retry) failedPageOffset ?: (currentOffset + PAGE_SIZE) else currentOffset + PAGE_SIZE
        val nextKlipyOffset = if (retry && failedPageOffset != null) failedKlipyOffset else klipyNextOffset
        _uiState.value = state.copy(isLoadingMore = true, loadMoreError = null)
        requests.page(viewModelScope, retry = retry) { token ->
            val flow = when {
                filter.source == GifSource.KLIPY -> searchKlipyGifsUseCase(query, filter = filter, offset = nextKlipyOffset)
                filter.source == GifSource.LOSPEC -> searchLospecGifsUseCase(query, filter = filter, offset = nextOffset)
                filter.type == GifType.STICKER -> searchStickersUseCase(query, filter = filter, offset = nextOffset)
                else -> searchGifsUseCase(query, filter = filter, offset = nextOffset)
            }
            flow.catch { error ->
                if (error is CancellationException) throw error
                emit(Result.Error(error))
            }.collect { result ->
                if (!requests.accepts(token)) return@collect
                when (result) {
                    Result.Loading -> Unit
                    is Result.Success -> {
                        accumulatedGifs = (accumulatedGifs + result.data).distinctBy { it.uniqueKey() }
                        currentOffset = nextOffset
                        failedPageOffset = null
                        failedKlipyOffset = null
                        if (filter.source == GifSource.KLIPY) {
                            klipyNextOffset = ((nextKlipyOffset?.toIntOrNull() ?: 0) + PAGE_SIZE).toString()
                        }
                        _uiState.value = state.copy(gifs = accumulatedGifs, isLoadingMore = false,
                            hasMore = result.data.size >= 10, loadMoreError = null)
                    }
                    is Result.Error -> {
                        failedPageOffset = nextOffset
                        failedKlipyOffset = nextKlipyOffset
                        val error = result.exception.toLoadError()
                        requests.failPage(token, error.name)
                        _uiState.value = state.copy(gifs = accumulatedGifs, isLoadingMore = false,
                            loadMoreError = error)
                    }
                }
            }
        }
    }

    private fun resetAndSearch(query: String, filter: GifFilter) {
        requests.invalidate()
        currentOffset = 0
        klipyNextOffset = null
        failedPageOffset = null
        failedKlipyOffset = null
        accumulatedGifs = emptyList()

        // Personalized trending: if no query and user has interests
        if (query.isBlank()) {
            val interestQueries = loadUserInterestQueries()
            if (interestQueries.isNotEmpty()) {
                loadPersonalized(interestQueries, filter)
                return
            }
        }

        val trending = query.isBlank()

        if (filter.source == GifSource.LOSPEC && query.isBlank()) {
            _uiState.value = SearchUiState.Empty
            return
        }

        // Remove the previous query immediately; cached results can populate this request.
        _uiState.value = SearchUiState.Loading

        val flow = when {
            filter.source == GifSource.KLIPY -> searchKlipyGifsUseCase(query, filter = filter, offset = null)
            filter.source == GifSource.LOSPEC -> searchLospecGifsUseCase(query, filter = filter, offset = 0)
            filter.type == GifType.STICKER -> searchStickersUseCase(query, filter = filter, offset = 0)
            else -> searchGifsUseCase(query, filter = filter, offset = 0)
        }

        requests.search(viewModelScope) { token ->
            var didSaveHistory = false
            flow.catch { error ->
                if (error is CancellationException) throw error
                emit(Result.Error(error))
            }.collect { result ->
                if (!requests.accepts(token)) return@collect
                when (result) {
                    is Result.Loading -> {
                        // Only show global loading if we don't have any data yet
                        if (accumulatedGifs.isEmpty()) {
                            _uiState.value = SearchUiState.Loading
                        }
                    }
                    is Result.Success -> {
                        accumulatedGifs = result.data.distinctBy { it.uniqueKey() }
                        if (!didSaveHistory && query.isNotBlank()) {
                            searchHistoryRepository.saveQuery(query)
                            didSaveHistory = true
                        }
                        if (!requests.accepts(token)) return@collect
                        if (filter.source == GifSource.KLIPY) {
                            klipyNextOffset = PAGE_SIZE.toString()
                        }
                        _uiState.value = if (result.data.isEmpty()) {
                            SearchUiState.Empty
                        } else {
                            SearchUiState.Success(
                                gifs = accumulatedGifs,
                                hasMore = result.data.size >= 10,
                                isTrending = trending
                            )
                        }
                    }
                    is Result.Error -> {
                        // Only show error if we have NO data (even from cache)
                        if (accumulatedGifs.isEmpty()) {
                            _uiState.value = SearchUiState.Error(result.exception.toLoadError())
                        }
                    }
                }
            }
        }
    }

    private fun loadUserInterestQueries(): List<String> {
        val prefs = appContext.getSharedPreferences(UiPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(UiPreferences.KEY_USER_INTERESTS, "") ?: ""
        val names = raw.split(",").filter { it.isNotBlank() }.toSet()
        return PredefinedInterests.all
            .filter { it.name in names }
            .map { it.searchQuery }
    }

    private fun loadPersonalized(interestQueries: List<String>, filter: GifFilter) {
        if (filter.source == GifSource.LOSPEC) {
            _uiState.value = SearchUiState.Empty
            return
        }

        _uiState.value = SearchUiState.Loading

        val selectedQueries = interestQueries.shuffled().take(3)

        requests.search(viewModelScope) { token ->
            val results = selectedQueries.map { q ->
                async {
                    var gifs = emptyList<Gif>()
                    val flow = when {
                        filter.source == GifSource.KLIPY -> searchKlipyGifsUseCase(q, filter = filter, offset = null)
                        filter.type == GifType.STICKER -> searchStickersUseCase(q, filter = filter, offset = 0)
                        else -> searchGifsUseCase(q, filter = filter, offset = 0)
                    }
                    flow.catch { error ->
                if (error is CancellationException) throw error
                emit(Result.Error(error))
            }.collect { result ->
                        if (result is Result.Success) gifs = result.data
                    }
                    gifs
                }
            }.awaitAll()

            if (!requests.accepts(token)) return@search
            accumulatedGifs = results.flatten().distinctBy { it.uniqueKey() }
            _uiState.value = if (accumulatedGifs.isEmpty()) {
                SearchUiState.Empty
            } else {
                SearchUiState.Success(
                    gifs = accumulatedGifs,
                    hasMore = false,
                    isPersonalized = true
                )
            }
        }
    }
}
