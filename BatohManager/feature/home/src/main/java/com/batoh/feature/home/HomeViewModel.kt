package com.batoh.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batoh.core.common.Result
import com.batoh.core.data.bluetooth.BluetoothLeManager
import com.batoh.core.domain.usecase.GetLibraryEntriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Exposes the backpack connection state and the most recent local collection items for the
 * home screen. Read-only: never starts BLE work and never modifies the collection.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    bluetoothManager: BluetoothLeManager,
    getLibraryEntries: GetLibraryEntriesUseCase
) : ViewModel() {

    val backpackStatus: StateFlow<HomeBackpackStatus> = combine(
        bluetoothManager.connectionStatus,
        bluetoothManager.deviceName,
        bluetoothManager.advertisement
    ) { status, name, adv ->
        HomeBackpackStatus.from(status, name, adv?.versionCode)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeBackpackStatus.from(
            bluetoothManager.connectionStatus.value,
            bluetoothManager.deviceName.value,
            bluetoothManager.advertisement.value?.versionCode
        )
    )

    /** Newest GIFs of the local collection (empty while loading, on error or when none exist). */
    val recentGifs: StateFlow<List<HomeRecentGif>> = getLibraryEntries()
        .map { result ->
            if (result is Result.Success) HomeRecentGif.recentOf(result.data) else emptyList()
        }
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
}
