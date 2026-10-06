package com.batoh.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batoh.core.data.bluetooth.BluetoothLeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Exposes the backpack connection state for the home tile. Read-only: never starts BLE work. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    bluetoothManager: BluetoothLeManager
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
}
