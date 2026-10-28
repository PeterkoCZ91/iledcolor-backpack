package com.batoh.feature.backpack

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Permission and Bluetooth-on gate shared by every user-initiated send entry. */
internal class SendPreflight(
    val request: (() -> Unit) -> Unit,
    val retry: () -> Unit,
    val permissionDenied: Boolean,
    val bluetoothOff: Boolean,
    val isWaitingForSystem: () -> Boolean
)

@Composable
internal fun rememberSendPreflight(): SendPreflight {
    val context = LocalContext.current
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
    fun hasPermissions() = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    fun bluetoothOn() = runCatching {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true
    }.getOrDefault(false)

    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    var bluetoothOff by remember { mutableStateOf(false) }
    var waitingForSystem by remember { mutableStateOf(false) }
    fun complete() {
        waitingForSystem = false
        val action = pending
        pending = null
        action?.invoke()
    }

    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        waitingForSystem = false
        when {
            !hasPermissions() -> permissionDenied = true
            bluetoothOn() -> complete()
            else -> bluetoothOff = true
        }
    }
    fun finishPermissions() {
        waitingForSystem = false
        if (!hasPermissions()) {
            permissionDenied = true
            return
        }
        if (bluetoothOn()) complete()
        else {
            waitingForSystem = true
            enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        finishPermissions()
    }
    fun request(action: () -> Unit) {
        permissionDenied = false
        bluetoothOff = false
        pending = action
        if (hasPermissions()) finishPermissions()
        else {
            waitingForSystem = true
            permissionLauncher.launch(permissions)
        }
    }
    return SendPreflight(
        request = ::request,
        retry = { pending?.let { request(it) } },
        permissionDenied = permissionDenied,
        bluetoothOff = bluetoothOff,
        isWaitingForSystem = { waitingForSystem }
    )
}
