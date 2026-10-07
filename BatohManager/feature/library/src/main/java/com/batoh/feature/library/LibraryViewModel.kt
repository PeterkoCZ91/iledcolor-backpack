package com.batoh.feature.library

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import com.batoh.core.common.Result
import com.batoh.core.domain.model.Gif
import com.batoh.core.domain.usecase.DeleteGifUseCase
import com.batoh.core.domain.model.GifRenameException
import com.batoh.core.domain.model.LibraryEntry
import com.batoh.core.domain.usecase.GetLibraryEntriesUseCase
import com.batoh.core.domain.usecase.RenameGifUseCase
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import com.batoh.core.domain.usecase.ImportGifUseCase
import com.batoh.core.domain.usecase.InspectGifUseCase
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

sealed interface LibraryUiState {
    object Loading : LibraryUiState
    /**
     * The collection is not empty. [entries] are already filtered by [query] and sorted;
     * they may be empty when nothing matches the search.
     */
    data class Success(
        val entries: List<LibraryEntry>,
        val summary: LibrarySummary,
        val query: String = "",
        val sort: LibrarySort = LibrarySort.DEFAULT
    ) : LibraryUiState {
        val gifs: List<Gif> get() = entries.map { it.gif }
    }
    data class Error(val message: String) : LibraryUiState
    object Empty : LibraryUiState
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    application: Application,
    private val getLibraryEntriesUseCase: GetLibraryEntriesUseCase,
    private val savedStateHandle: SavedStateHandle,
    private val deleteGifUseCase: DeleteGifUseCase,
    private val renameGifUseCase: RenameGifUseCase,
    private val importGifUseCase: ImportGifUseCase,
    private val inspectGifUseCase: InspectGifUseCase
) : AndroidViewModel(application) {
    private val _previewDiagnoses = MutableStateFlow<Map<String, PreviewDiagnosis>>(emptyMap())
    /** Keyed by [Gif.id]; only tiles whose preview failed have an entry. */
    val previewDiagnoses = _previewDiagnoses.asStateFlow()
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage = _actionMessage.asStateFlow()
    private val _importing = MutableStateFlow(false)
    val importing = _importing.asStateFlow()
    private val _deleteConsent = MutableStateFlow<IntentSender?>(null)
    val deleteConsent = _deleteConsent.asStateFlow()
    private var pendingDelete: Gif? = null
    fun showMessage(message: String) { _actionMessage.value = message }

    // Bumped by retry() to re-subscribe to the library source after a load error.
    private val reloadTrigger = MutableStateFlow(0)

    private val prefs = application.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
    /** Search text; kept in saved state so it survives process death, not app restarts. */
    val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")
    private val _sort = MutableStateFlow(LibrarySort.fromPref(prefs.getString(PREF_SORT, null)))
    /** Sort order, persisted across app restarts. */
    val sort = _sort.asStateFlow()

    fun setQuery(value: String) { savedStateHandle[KEY_QUERY] = value.take(MAX_QUERY_LENGTH) }

    fun setSort(value: LibrarySort) {
        _sort.value = value
        prefs.edit().putString(PREF_SORT, value.prefValue).apply()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LibraryUiState> = combine(
        reloadTrigger.flatMapLatest {
            // MediaStore refreshes (e.g. after a rename) emit Loading again; keep showing the
            // current collection until fresh data arrives instead of flashing the skeleton.
            var loaded = false
            getLibraryEntriesUseCase().filter { result ->
                if (result is Result.Loading) !loaded else { loaded = true; true }
            }
        },
        query,
        _sort
    ) { result, query, sort ->
            when (result) {
                is Result.Loading -> LibraryUiState.Loading
                is Result.Success -> {
                    val all = result.data
                    if (all.isEmpty()) LibraryUiState.Empty
                    else {
                        val shown = LibraryTools.filterAndSort(all, query, sort)
                        LibraryUiState.Success(shown, LibraryTools.summarize(shown, all), query, sort)
                    }
                }
                is Result.Error -> LibraryUiState.Error(localizedString(R.string.library_load_failed))
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LibraryUiState.Loading
        )

    // Výběr GIFů ke spojení (ids v pořadí výběru); přežije zabití procesu, smazané GIFy se odfiltrují.
    private val rawSelection: StateFlow<ArrayList<String>> =
        savedStateHandle.getStateFlow(KEY_SELECTION, ArrayList<String>())
    /** Celá sbírka bez ohledu na hledání (vybraný GIF může být právě odfiltrovaný); null = ještě nenačteno. */
    private val allGifs: StateFlow<List<Gif>?> = getLibraryEntriesUseCase()
        .filter { it is Result.Success }
        .map { (it as Result.Success).data.map { entry -> entry.gif } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val selection: StateFlow<List<String>> = combine(rawSelection, allGifs) { ids, gifs ->
        if (gifs == null) ids else LibrarySelection.prune(ids, gifs.map { it.id }.toSet())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun setSelection(ids: List<String>) { savedStateHandle[KEY_SELECTION] = ArrayList(ids) }

    /** Dlouhý klik: zapne výběr s tímto GIFem (už vybraný zůstane). */
    fun startSelection(gif: Gif) {
        if (gif.id !in selection.value) toggleSelection(gif)
    }

    fun toggleSelection(gif: Gif) {
        val current = selection.value
        val next = LibrarySelection.toggle(current, gif.id)
        if (next == current) {
            _actionMessage.value = localizedString(R.string.library_select_max, LibrarySelection.MAX_TO_CHAIN)
            return
        }
        _actionMessage.value = null
        setSelection(next)
    }

    fun clearSelection() = setSelection(emptyList())

    /** URI vybraných GIFů v pořadí výběru, nebo prázdný seznam, pokud jich je méně než 2. */
    fun chainUris(): List<String> {
        val gifs = allGifs.value ?: return emptyList()
        val sel = selection.value
        if (!LibrarySelection.canChain(sel)) return emptyList()
        return LibrarySelection.uris(sel, gifs)
    }

    /** Recovery action for [LibraryUiState.Error]: reload the local library. */
    fun retry() { reloadTrigger.value++ }

    fun deleteGif(gif: Gif) {
        if (pendingDelete != null) return
        viewModelScope.launch {
            when (val result = deleteGifUseCase(gif)) {
                is Result.Success -> _actionMessage.value = localizedString(R.string.library_delete_success)
                is Result.Error -> {
                    val exception = result.exception
                    try {
                        val sender = when {
                            Build.VERSION.SDK_INT >= 30 && exception is SecurityException ->
                                MediaStore.createDeleteRequest(getApplication<Application>().contentResolver,
                                    listOf(Uri.parse(gif.originalUrl))).intentSender
                            Build.VERSION.SDK_INT == 29 && exception is android.app.RecoverableSecurityException ->
                                exception.userAction.actionIntent.intentSender
                            else -> null
                        }
                        if (sender != null) {
                            pendingDelete = gif
                            _deleteConsent.value = sender
                        } else _actionMessage.value = localizedString(R.string.library_delete_failed)
                    } catch (e: Exception) {
                        _actionMessage.value = localizedString(R.string.library_delete_failed)
                    }
                }
                Result.Loading -> _actionMessage.value = localizedString(R.string.library_delete_incomplete)
            }
        }
    }

    /** Called when Coil cannot show a tile; reads the file to explain why and gate edit/send. */
    fun onPreviewFailed(gif: Gif) {
        if (_previewDiagnoses.value.containsKey(gif.id)) return
        _previewDiagnoses.value = _previewDiagnoses.value + (gif.id to PreviewDiagnosis.Checking)
        viewModelScope.launch {
            val diagnosis = try {
                inspectGifUseCase(gif).toDiagnosis()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                PreviewDiagnosis.Broken(com.batoh.core.domain.model.GifFileProblem.UNREADABLE)
            }
            if (_previewDiagnoses.value[gif.id] == PreviewDiagnosis.Checking) {
                _previewDiagnoses.value = _previewDiagnoses.value + (gif.id to diagnosis)
            }
        }
    }

    fun onPreviewLoaded(gif: Gif) {
        if (_previewDiagnoses.value.containsKey(gif.id)) _previewDiagnoses.value = _previewDiagnoses.value - gif.id
    }

    private val _renameConsent = MutableStateFlow<IntentSender?>(null)
    /** Write-access request to launch when Android refuses the rename (file of an older install). */
    val renameConsent = _renameConsent.asStateFlow()
    // Kept in SavedStateHandle: the system consent dialog can outlive the process.
    private val pendingRenameStore = PendingRenameStore(savedStateHandle)
    /** True between launching the system consent dialog and receiving its result. */
    private var renameConsentInFlight = false
    private val _renaming = MutableStateFlow(false)
    val renaming = _renaming.asStateFlow()

    fun renameGif(gif: Gif, newName: String) {
        // A pending rename blocks a new one only while its consent dialog is really open/launching;
        // a stale one (launch failed, result lost) is dropped so rename cannot stay "busy" forever.
        val consentOpen = _renameConsent.value != null || renameConsentInFlight
        if (_renaming.value || (pendingRenameStore.peek() != null && consentOpen)) {
            _actionMessage.value = localizedString(R.string.library_rename_busy)
            return
        }
        pendingRenameStore.take()
        _renaming.value = true
        viewModelScope.launch {
            try { handleRename(gif, newName, mayAskConsent = true) } finally { _renaming.value = false }
        }
    }

    private suspend fun handleRename(gif: Gif, newName: String, mayAskConsent: Boolean) {
        when (val result = renameGifUseCase(gif, newName)) {
            is Result.Success -> _actionMessage.value = localizedString(R.string.library_rename_success, result.data)
            is Result.Error -> {
                val exception = result.exception
                if (exception is GifRenameException) {
                    _actionMessage.value = localizedString(exception.failure.messageRes())
                    return
                }
                val sender = if (mayAskConsent && exception is SecurityException) {
                    try {
                        when {
                            Build.VERSION.SDK_INT >= 30 -> MediaStore.createWriteRequest(
                                getApplication<Application>().contentResolver,
                                listOf(Uri.parse(gif.originalUrl))).intentSender
                            Build.VERSION.SDK_INT == 29 && exception is android.app.RecoverableSecurityException ->
                                exception.userAction.actionIntent.intentSender
                            else -> null
                        }
                    } catch (e: Exception) { null }
                } else null
                if (sender != null) {
                    pendingRenameStore.save(gif, newName)
                    _renameConsent.value = sender
                } else {
                    _actionMessage.value = localizedString(
                        if (exception is SecurityException) R.string.library_rename_no_permission
                        else R.string.library_rename_failed)
                }
            }
            Result.Loading -> _actionMessage.value = localizedString(R.string.library_rename_failed)
        }
    }

    fun renameConsentLaunched() {
        _renameConsent.value = null
        renameConsentInFlight = true
    }
    fun renameConsentResult(approved: Boolean) {
        renameConsentInFlight = false
        val pending = pendingRenameStore.take()
        if (pending == null) {
            // Nothing to retry (e.g. state lost); tell the user instead of silently doing nothing.
            _actionMessage.value = localizedString(R.string.library_rename_failed)
            return
        }
        if (!approved) {
            _actionMessage.value = localizedString(R.string.library_rename_cancelled)
            return
        }
        _renaming.value = true
        viewModelScope.launch {
            // Retry once; a second refusal is reported instead of asking again.
            try { handleRename(pending.first, pending.second, mayAskConsent = false) } finally { _renaming.value = false }
        }
    }

    fun deleteConsentLaunched() { _deleteConsent.value = null }
    fun deleteConsentResult(approved: Boolean) {
        val gif = pendingDelete
        pendingDelete = null
        if (!approved) _actionMessage.value = localizedString(R.string.library_delete_cancelled)
        else if (Build.VERSION.SDK_INT == 29 && gif != null) deleteGif(gif)
        else _actionMessage.value = localizedString(R.string.library_delete_success)
    }

    fun importGif(uri: Uri) {
        if (_importing.value) return
        _importing.value = true
        _actionMessage.value = localizedString(R.string.library_importing_message)
        viewModelScope.launch {
            try {
                _actionMessage.value = when (val result = importGifUseCase(uri)) {
                    is Result.Success -> localizedString(R.string.library_import_success)
                    is Result.Error -> localizedString(R.string.library_import_failed)
                    Result.Loading -> localizedString(R.string.library_import_incomplete)
                }
            } finally { _importing.value = false }
        }
    }

    private fun localizedString(id: Int, vararg args: Any): String {
        val context = ContextCompat.getContextForLanguage(getApplication<Application>())
        return if (args.isEmpty()) context.getString(id) else context.getString(id, *args)
    }

    private companion object {
        const val PREFS_NAME = "library_prefs"
        const val PREF_SORT = "sort"
        const val KEY_QUERY = "library_query"
        const val KEY_SELECTION = "library_chain_selection"
        const val MAX_QUERY_LENGTH = 100
    }
}
