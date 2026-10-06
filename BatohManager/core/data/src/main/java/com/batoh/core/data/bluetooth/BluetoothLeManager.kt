package com.batoh.core.data.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothLeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val connectionPreferences = context.getSharedPreferences("backpack_connection", Context.MODE_PRIVATE)
    
    private val _scannedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scannedDevices = _scannedDevices.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus = _connectionStatus.asStateFlow()
    
    private val _servicesLog = MutableStateFlow<String>("")
    val servicesLog = _servicesLog.asStateFlow()
    
    // Ack Flow
    private val _lastAckIndex = MutableStateFlow<Int>(-1)
    val lastAckIndex = _lastAckIndex.asStateFlow()

    private val commandMutex = Mutex()
    private val pendingCommand = PendingCommandResponse()

    // Auth response: stores last AE02 notification payload for auth handshake
    private val _lastAuthResponse = MutableStateFlow<ByteArray?>(null)
    val lastAuthResponse = _lastAuthResponse.asStateFlow()

    // Auth responses as events — writeAuthSuspend subscribes BEFORE writing so a fast
    // response can't be missed, and a stale response from a previous step can't be reused
    private val _authResponses = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)

    // Status byte (data[8]) of the most recent data-chunk ACK; only 0x01 means OK
    @Volatile
    var lastAckStatus: Int = -1
        private set

    // Negotiated ATT MTU — data chunk size is mtu - 25 (487 B at MTU 512)
    @Volatile
    var mtu: Int = 23
        private set

    // Auth completed flag — auth is per-connection, skip on subsequent uploads
    @Volatile
    private var _authCompleted = false
    val authCompleted: Boolean get() = _authCompleted

    // BLE log ring buffer (200 entries max) for in-app debugging
    private val _bleLog = MutableStateFlow<List<String>>(emptyList())
    val bleLog = _bleLog.asStateFlow()

    fun addBleLog(entry: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
        val line = "[$ts] $entry"
        val current = _bleLog.value.toMutableList()
        current.add(line)
        if (current.size > 200) current.removeAt(0)
        _bleLog.value = current
    }


    private var scanCallback: ScanCallback? = null

    // Capabilities from the backpack's advertisement (funCode etc.). Cached per address because
    // direct connects skip scanning; null until the target has been seen in any scan.
    private val _advertisement = MutableStateFlow(targetAddress()?.let(::cachedAdvertisement))
    val advertisement = _advertisement.asStateFlow()
    private var capabilityScan: ScanCallback? = null
    // Advertised name of the target ("iledcolor-XXXX"), cached per address like the advertisement.
    private val _deviceName = MutableStateFlow(targetAddress()?.let { connectionPreferences.getString("name_$it", null) })
    val deviceName = _deviceName.asStateFlow()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Last backpack this phone connected to; null until the first successful connection. */
    private fun targetAddress(): String? =
        connectionPreferences.getString("last_device_address", null)
            ?.takeIf { BluetoothAdapter.checkBluetoothAddress(it) }

    private fun cachedAdvertisement(address: String): BackpackAdvertisement? =
        connectionPreferences.getString("adv_$address", null)?.let { hex ->
            runCatching { BackpackAdvertisement.parse(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()) }.getOrNull()
        }

    /** Parses and caches a scan record; returns true when it belongs to a backpack. */
    private fun rememberAdvertisement(device: BluetoothDevice, record: ByteArray): Boolean {
        val adv = BackpackAdvertisement.parse(record) ?: return false
        val raw = record.joinToString("") { "%02X".format(it) }
        @SuppressLint("MissingPermission")
        val name = device.name
        val editor = connectionPreferences.edit().putString("adv_${device.address}", raw)
        if (name != null) editor.putString("name_${device.address}", name)
        editor.apply()
        if (name != null) _deviceName.value = name
        if (_advertisement.value != adv) {
            _advertisement.value = adv
            val info = "ADV $name: funCode=0x%04X version=%d size=%dx%d color=%d customer=%d rotation=%b raw=%s"
                .format(adv.funCode, adv.versionCode, adv.width, adv.height, adv.colorType, adv.customerId, adv.supportsRotation, raw)
            Log.i("BackpackBLE", info)
            addBleLog(info)
        }
        return true
    }
    @Volatile
    private var gatt: BluetoothGatt? = null
    
    // Write serialization: writeMutex allows only one GATT write in flight at a time;
    // pendingWrite is completed by onCharacteristicWrite only for the matching characteristic
    private val writeMutex = Mutex()
    @Volatile
    private var pendingWrite: Pair<java.util.UUID, CompletableDeferred<Int>>? = null

    private val discoveryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: android.content.Intent) {
            when(intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let {
                        Log.d("BackpackBLE", "Classic Device Found: ${it.name} [${it.address}]")
                        addDevice(it)
                    }
                }
            }
        }
    }

    private fun addDevice(device: BluetoothDevice) {
        val currentList = _scannedDevices.value.toMutableList()
        if (currentList.none { it.address == device.address }) {
            currentList.add(device)
            _scannedDevices.value = currentList
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasPermissions()) {
            _connectionStatus.value = "Missing Permissions"
            return
        }
        // Discovery must not overwrite an active connection's state or interrupt a transfer.
        if (gatt != null) return

        if (scanCallback != null) stopScan()

        // 1. Start BLE Scan
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                Log.d("BackpackBLE", "BLE Device Found: ${device.name} [${device.address}] RSSI: ${result.rssi}")
                addDevice(device)
                result.scanRecord?.bytes?.let { rememberAdvertisement(device, it) }
            }
            
            override fun onScanFailed(errorCode: Int) {
                Log.e("BackpackBLE", "BLE Scan Failed: $errorCode")
            }
        }
        
        adapter?.bluetoothLeScanner?.startScan(scanCallback)
        
        // 2. Start Classic Discovery
        try {
            val filter = android.content.IntentFilter(BluetoothDevice.ACTION_FOUND)
            context.registerReceiver(discoveryReceiver, filter)
            adapter?.startDiscovery()
            Log.d("BackpackBLE", "Started Hybrid Scan (BLE + Classic)")
        } catch (e: Exception) {
            Log.e("BackpackBLE", "Failed to start Classic discovery", e)
        }
        
        _connectionStatus.value = "Scanning (Hybrid)..."
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!hasPermissions() || adapter?.isEnabled != true) return
        
        // Stop BLE
        scanCallback?.let {
            adapter?.bluetoothLeScanner?.stopScan(it)
        }
        scanCallback = null
        
        // Stop Classic
        try {
            adapter?.cancelDiscovery()
            context.unregisterReceiver(discoveryReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
        
        if (gatt == null && _connectionStatus.value.startsWith("Scanning")) {
            _connectionStatus.value = "Scan Stopped"
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToTarget() {
        if (!hasPermissions() || adapter == null) return
        stopScan()
        if (capabilityScan != null) return
        val address = targetAddress()
        val cached = address?.let(::cachedAdvertisement)
        val scanner = adapter.bluetoothLeScanner
        if (address != null && (cached != null || scanner == null)) {
            cached?.let { _advertisement.value = it }
            connect(adapter.getRemoteDevice(address))
            return
        }
        if (scanner == null) {
            _connectionStatus.value = "Disconnected"
            return
        }
        // Capabilities live only in the advertisement. With a known backpack, catch it with a short
        // scan filtered to its address; on first use, connect to the first backpack advertising nearby.
        _connectionStatus.value = "Connecting (reading capabilities)..."
        var found: BluetoothDevice? = null
        lateinit var finish: Runnable
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val record = result.scanRecord?.bytes ?: return
                if (address != null && result.device.address != address) return
                if (rememberAdvertisement(result.device, record)) {
                    found = result.device
                    mainHandler.post(finish)
                }
            }
            override fun onScanFailed(errorCode: Int) {
                Log.w("BackpackBLE", "Capability scan failed: $errorCode")
                mainHandler.post(finish)
            }
        }
        // Runs once: on the first backpack advertisement or after the timeout; a disconnect()
        // in between clears capabilityScan and turns it into a no-op.
        finish = Runnable {
            if (capabilityScan !== callback) return@Runnable
            stopCapabilityScan()
            val target = found ?: address?.let { adapter.getRemoteDevice(it) }
            if (target != null) {
                connect(target)
            } else {
                Log.w("BackpackBLE", "No backpack advertising nearby")
                addBleLog("No backpack advertising nearby")
                _connectionStatus.value = "Disconnected"
            }
        }
        capabilityScan = callback
        val filters = address?.let { listOf(android.bluetooth.le.ScanFilter.Builder().setDeviceAddress(it).build()) }
        val settings = android.bluetooth.le.ScanSettings.Builder()
            .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(filters, settings, callback)
        mainHandler.postDelayed(finish, if (address != null) CAPABILITY_SCAN_MS else DISCOVERY_SCAN_MS)
    }

    @SuppressLint("MissingPermission")
    private fun stopCapabilityScan() {
        val callback = capabilityScan ?: return
        capabilityScan = null
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        if (!hasPermissions()) return
        stopScan()

        pendingCommand.disconnect()
        pendingWrite?.second?.complete(BluetoothGatt.GATT_FAILURE)
        // Close any existing GATT connection before opening a new one
        val previousGatt = gatt
        gatt = null
        previousGatt?.disconnect()
        previousGatt?.close()

        _connectionStatus.value = "Connecting to ${device.name ?: "Unknown"}..."
        _deviceName.value = device.name ?: connectionPreferences.getString("name_${device.address}", null)
        _authCompleted = false
        mtu = 23
        // TRANSPORT_LE forces BLE transport and avoids status=62 stale-cache errors
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopCapabilityScan()
        val previousGatt = gatt
        gatt = null
        previousGatt?.disconnect()
        previousGatt?.close()
        _connectionStatus.value = "Disconnected"
        _authCompleted = false
        mtu = 23
        pendingCommand.disconnect()
        pendingWrite?.second?.complete(BluetoothGatt.GATT_FAILURE)
    }

    companion object {
        private const val CAPABILITY_SCAN_MS = 3000L
        private const val DISCOVERY_SCAN_MS = 8000L
        val SERVICE_UUID = java.util.UUID.fromString("0000a950-0000-1000-8000-00805f9b34fb")
        val CHAR_CTRL_UUID = java.util.UUID.fromString("0000a951-0000-1000-8000-00805f9b34fb")
        val CHAR_DATA_UUID = java.util.UUID.fromString("0000a952-0000-1000-8000-00805f9b34fb")
        val NOTIFY_UUID = java.util.UUID.fromString("0000a953-0000-1000-8000-00805f9b34fb")

        // Security/Auth service AE00
        val SERVICE_AUTH_UUID = java.util.UUID.fromString("0000ae00-0000-1000-8000-00805f9b34fb")
        val CHAR_AUTH_WRITE_UUID = java.util.UUID.fromString("0000ae01-0000-1000-8000-00805f9b34fb")
        val CHAR_AUTH_NOTIFY_UUID = java.util.UUID.fromString("0000ae02-0000-1000-8000-00805f9b34fb")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (this@BluetoothLeManager.gatt != gatt) return
            Log.d("BackpackBLE", "Connection State Change: status=$status newState=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionStatus.value = "Connected! Discovering services..."
                Log.d("BackpackBLE", "Starting service discovery...")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w("BackpackBLE", "Disconnected (status=$status) — closing GATT client")
                _connectionStatus.value = "Disconnected"
                // close() releases the BLE client slot — without it every unexpected
                // disconnect leaks a client until the app can no longer connect at all
                gatt.close()
                this@BluetoothLeManager.gatt = null
                _authCompleted = false
                mtu = 23
                // Fail fast any coroutine waiting on an in-flight write
                pendingWrite?.second?.complete(BluetoothGatt.GATT_FAILURE)
                pendingWrite = null
                pendingCommand.disconnect()
            } else {
                 _connectionStatus.value = "Connection state: $newState (Status: $status)"
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (this@BluetoothLeManager.gatt != gatt) return
            Log.d("BackpackBLE", "onServicesDiscovered status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    val ctrlChar = service.getCharacteristic(CHAR_CTRL_UUID)
                    val dataChar = service.getCharacteristic(CHAR_DATA_UUID)
                    if (ctrlChar != null && dataChar != null) {
                         Log.d("BackpackBLE", "Target Service & Characteristics Found!")
                         
                         // Log characteristic properties for debug
                         Log.d("BackpackBLE", "CTRL props: ${ctrlChar.properties} DATA props: ${dataChar.properties}")
                         val authService = gatt.getService(SERVICE_AUTH_UUID)
                         if (authService != null) {
                             val authWriteChar = authService.getCharacteristic(CHAR_AUTH_WRITE_UUID)
                             val authNotifyChar = authService.getCharacteristic(CHAR_AUTH_NOTIFY_UUID) 
                             Log.d("BackpackBLE", "AUTH_WRITE props: ${authWriteChar?.properties} AUTH_NOTIFY props: ${authNotifyChar?.properties}")
                         }
                         
                         // Manufacturer sequence: enable A953, enable AE02, then requestMtu(512)
                         // They do it all in rapid succession
                         enableNotification(gatt, service, NOTIFY_UUID)
                    } else {
                        _connectionStatus.value = "Target Characteristics Not Found"
                        Log.e("BackpackBLE", "Characteristics $CHAR_CTRL_UUID or $CHAR_DATA_UUID not found")
                    }
                } else {
                    _connectionStatus.value = "Target Service Not Found"
                    Log.e("BackpackBLE", "Service $SERVICE_UUID not found")
                }
                
                // Keep logging all for debug
                val logBuilder = StringBuilder()
                logBuilder.append("Services Discovered:\n")
                gatt.services.forEach { s ->
                    logBuilder.append("S: ${s.uuid}\n")
                    s.characteristics.forEach { c ->
                         logBuilder.append("  C: ${c.uuid} props: ${c.properties}\n")
                    }
                }
                _servicesLog.value = logBuilder.toString()
            } else {
                Log.e("BackpackBLE", "Service Discovery Failed: $status")
                _connectionStatus.value = "Service Discovery Failed: $status"
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (this@BluetoothLeManager.gatt != gatt) return
            handleNotification(characteristic, value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (this@BluetoothLeManager.gatt != gatt) return
            @Suppress("DEPRECATION")
            handleNotification(characteristic, characteristic.value)
        }
        
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (this@BluetoothLeManager.gatt != gatt) return
            val charUuid = descriptor.characteristic.uuid
            Log.d("BackpackBLE", "onDescriptorWrite: char=${charUuid.toString().substring(4,8)} status=$status")
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (charUuid == NOTIFY_UUID) {
                    // A953 enabled, now enable AE02 (manufacturer does both quickly)
                    Log.d("BackpackBLE", "A953 enabled, now enabling AE02...")
                    val authService = gatt.getService(SERVICE_AUTH_UUID)
                    if (authService != null) {
                        enableNotification(gatt, authService, CHAR_AUTH_NOTIFY_UUID)
                    } else {
                        Log.w("BackpackBLE", "Auth Service AE00 not found - requesting MTU without it")
                        gatt.requestMtu(512)
                    }
                } else if (charUuid == CHAR_AUTH_NOTIFY_UUID) {
                    // AE02 enabled, now request MTU 512 (manufacturer sequence)
                    Log.d("BackpackBLE", "AE02 enabled. Requesting MTU 512...")
                    gatt.requestMtu(512)
                }
            } else {
                Log.e("BackpackBLE", "Failed to enable notifications: $status")
                _connectionStatus.value = "Failed to enable notifications: $status"
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (this@BluetoothLeManager.gatt != gatt) return
            connectionPreferences.edit().putString("last_device_address", gatt.device.address).apply()
            Log.d("BackpackBLE", "onMtuChanged: mtu=$mtu status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                this@BluetoothLeManager.mtu = mtu
                _connectionStatus.value = "Ready (MTU=$mtu)"
            } else {
                _connectionStatus.value = "Ready (MTU negotiation failed, using default)"
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Log.d("BackpackBLE", "onCharacteristicWrite: char=${characteristic.uuid.toString().substring(4,8)} status=$status")
            if (this@BluetoothLeManager.gatt != gatt) return
            val pending = pendingWrite
            if (pending != null && pending.first == characteristic.uuid) {
                pending.second.complete(status)
            } else {
                // Stale callback (after timeout) or callback for a different characteristic —
                // completing the wrong deferred would attribute this status to the wrong write
                Log.w("BackpackBLE", "Ignoring unexpected write callback for ${characteristic.uuid.toString().substring(4,8)}")
            }
        }
    } // Close gattCallback

    @SuppressLint("MissingPermission")
    private fun enableNotification(gatt: BluetoothGatt, service: BluetoothGattService, charUuid: java.util.UUID) {
        val notifyChar = service.getCharacteristic(charUuid)
        if (notifyChar != null) {
            gatt.setCharacteristicNotification(notifyChar, true)
            val descriptor = notifyChar.getDescriptor(java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            if (descriptor != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
                Log.d("BackpackBLE", "Enabling notifications on $charUuid")
            }
        }
    }

    private fun handleNotification(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (characteristic.uuid == NOTIFY_UUID || characteristic.uuid == CHAR_AUTH_NOTIFY_UUID) {
            val hex = value.joinToString("") { "%02X".format(it) }
            Log.d("BackpackBLE", "RX←${characteristic.uuid.toString().substring(4,8)} [${value.size}B] $hex")
            addBleLog("RX←${characteristic.uuid.toString().substring(4,8)} [${value.size}B] $hex")
            _receivedData.tryEmit(value)
            parseAck(value, characteristic.uuid)
        }
    }
    
    private fun parseAck(data: ByteArray, sourceChar: java.util.UUID) {
        // Auth characteristic (AE02) — store full payload for auth handshake
        if (sourceChar == CHAR_AUTH_NOTIFY_UUID) {
             val copy = data.copyOf()
             _lastAuthResponse.value = copy
             _authResponses.tryEmit(copy)
             val hex = data.joinToString("") { "%02X".format(it) }
             Log.d("BackpackBLE", "Auth Response [${data.size}B]: $hex")
             return
        }

        if (sourceChar != NOTIFY_UUID || !BackpackFrame.isValidNotification(data)) return
        val cmd = data[1].toInt() and 0xFF
        if (cmd == 0x00 && data.size >= 11) {
            val index = ((data[4].toInt() and 0xFF) shl 24) or
                ((data[5].toInt() and 0xFF) shl 16) or
                ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
            lastAckStatus = data[8].toInt() and 0xFF
            _lastAckIndex.value = index
        }
        // Match only validated A953 responses and the requested opcode. Auth/data/ready
        // events cannot acknowledge an unrelated settings command.
        pendingCommand.acceptA953Notification(data)
    }

    /** One control transaction, returning its validated A953 response or null. */
    suspend fun sendCommand(
        frame: ByteArray,
        timeoutMs: Long = 2000L,
        charUuid: java.util.UUID = CHAR_CTRL_UUID
    ): ByteArray? = commandMutex.withLock {
        require(BackpackFrame.isValid(frame)) { "Invalid command frame" }
        val response = pendingCommand.register(frame[1].toInt() and 0xFF)
        try {
            if (!writeDataSuspend(frame, charUuid)) return@withLock null
            withTimeoutOrNull(timeoutMs) { response.await() }
        } finally {
            pendingCommand.clear(response)
        }
    }

    private val _receivedData = kotlinx.coroutines.flow.MutableSharedFlow<ByteArray>(extraBufferCapacity = 10)
    val receivedData = _receivedData.asSharedFlow() // Expose as SharedFlow

    @SuppressLint("MissingPermission")
    fun writeData(data: ByteArray, charUuid: java.util.UUID = CHAR_DATA_UUID, serviceUuid: java.util.UUID = SERVICE_UUID): Boolean {
        if (gatt == null) {
            Log.e("BackpackBLE", "Gatt is null - cannot write")
            return false
        }
        val service = gatt?.getService(serviceUuid)
        val characteristic = service?.getCharacteristic(charUuid)

        if (characteristic != null) {
            // Use the characteristic's native default writeType (set by Android from GATT properties)
            // Do NOT override — manufacturer app (iledcolor) also uses the default
            val nativeWriteType = characteristic.writeType
            Log.d("BackpackBLE", "writeData to ${charUuid.toString().substring(4, 8)} nativeType=${if (nativeWriteType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) "NO_RESP" else "DEFAULT"} props=${characteristic.properties}")

            val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt?.writeCharacteristic(characteristic, data, nativeWriteType)
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = data
                @Suppress("DEPRECATION")
                if (gatt?.writeCharacteristic(characteristic) == true) 0 else 1
            }

            if (status != 0) {
                Log.e("BackpackBLE", "Write ERROR to ${charUuid.toString().substring(4, 8)} size: ${data.size} code: $status")
            } else {
                Log.d("BackpackBLE", "Write OK to ${charUuid.toString().substring(4, 8)} size: ${data.size}")
            }
            return status == 0
        } else {
            Log.e("BackpackBLE", "Characteristic $charUuid not found in service $serviceUuid")
            return false
        }
    }

    /**
     * Suspend version of writeData that waits for onCharacteristicWrite callback.
     * Uses the characteristic's NATIVE default writeType (set by Android from GATT properties).
     * This matches iledcolor manufacturer app behavior — it never overrides writeType.
     *
     * Android sets default writeType based on properties:
     * - PROPERTY_WRITE_NO_RESPONSE (0x04) → WRITE_TYPE_NO_RESPONSE
     * - PROPERTY_WRITE (0x08) → WRITE_TYPE_DEFAULT
     * If both are present, Android defaults to NO_RESPONSE.
     */
    @SuppressLint("MissingPermission")
    suspend fun writeDataSuspend(data: ByteArray, charUuid: java.util.UUID = CHAR_DATA_UUID, serviceUuid: java.util.UUID = SERVICE_UUID): Boolean {
        val writingGatt = gatt
        if (writingGatt == null) {
            Log.e("BackpackBLE", "Gatt is null - cannot write")
            return false
        }
        val service = writingGatt.getService(serviceUuid)
        val characteristic = service?.getCharacteristic(charUuid)

        if (characteristic == null) {
            Log.e("BackpackBLE", "Characteristic $charUuid not found in service $serviceUuid")
            return false
        }

        val props = characteristic.properties
        val hasWrite = (props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
        val hasNoResponse = (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0

        if (!hasWrite && !hasNoResponse) {
            Log.e("BackpackBLE", "Char ${charUuid.toString().substring(4,8)} has no writable property! props=$props")
            return false
        }

        // Use the characteristic's native default writeType — do NOT override
        // This matches manufacturer app (iledcolor) behavior
        val writeType = characteristic.writeType
        val isNoResponse = writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

        val hexPreview = data.take(32).joinToString("") { "%02X".format(it) } + if (data.size > 32) "..." else ""
        Log.d("BackpackBLE", "TX→${charUuid.toString().substring(4,8)} [${data.size}B] $hexPreview")
        addBleLog("TX→${charUuid.toString().substring(4,8)} [${data.size}B] $hexPreview")

        // Serialize: Android BLE allows only ONE outstanding GATT operation. The mutex
        // also prevents two coroutines (e.g. upload + auth) from overwriting each other's
        // pending deferred — previously the callback could complete the wrong write.
        return writeMutex.withLock {
            // A queued write must never send an old connection's characteristic to a new GATT.
            if (gatt !== writingGatt) return@withLock false
            val deferred = CompletableDeferred<Int>()
            pendingWrite = charUuid to deferred

            val enqueueOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                writingGatt.writeCharacteristic(characteristic, data, writeType) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = data
                @Suppress("DEPRECATION")
                writingGatt.writeCharacteristic(characteristic)
            }

            if (!enqueueOk) {
                Log.e("BackpackBLE", "Write ENQUEUE FAILED to ${charUuid.toString().substring(4,8)} size: ${data.size}")
                pendingWrite = null
                return@withLock false
            }

            // Wait for callback. For NO_RESPONSE, use shorter timeout + fallback.
            val timeoutMs = if (isNoResponse) 500L else 2000L
            val result = try {
                withTimeoutOrNull(timeoutMs) { deferred.await() }
            } finally {
                pendingWrite = null
            }

            if (result == null) {
                if (isNoResponse) {
                    // NO_RESPONSE callback might not fire on all Android versions — treat as success
                    Log.d("BackpackBLE", "Write NO_RESP timeout (expected on some devices) to ${charUuid.toString().substring(4,8)} size: ${data.size}")
                    return@withLock true
                }
                Log.e("BackpackBLE", "Write TIMEOUT to ${charUuid.toString().substring(4,8)} size: ${data.size}")
                return@withLock false
            }
            if (result != BluetoothGatt.GATT_SUCCESS) {
                Log.e("BackpackBLE", "Write REJECTED to ${charUuid.toString().substring(4,8)} size: ${data.size} gattStatus: $result")
                return@withLock false
            }
            Log.d("BackpackBLE", "Write CONFIRMED to ${charUuid.toString().substring(4,8)} size: ${data.size}")
            return@withLock true
        }
    }

    /**
     * Write to AE01 (auth characteristic) and wait for AE02 response.
     * Returns the response ByteArray, or null on timeout.
     */
    @SuppressLint("MissingPermission")
    suspend fun writeAuthSuspend(data: ByteArray, timeoutMs: Long = 3000L): ByteArray? = coroutineScope {
        // Subscribe BEFORE writing (UNDISPATCHED runs until first suspension = collect),
        // so a fast response can't be missed and a stale one can't satisfy this step
        val responseJob = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(timeoutMs) { _authResponses.first() }
        }

        val writeOk = writeDataSuspend(data, CHAR_AUTH_WRITE_UUID, SERVICE_AUTH_UUID)
        if (!writeOk) {
            Log.e("BackpackBLE", "Auth write to AE01 failed")
            responseJob.cancel()
            return@coroutineScope null
        }

        val response = responseJob.await()
        if (response == null) {
            Log.w("BackpackBLE", "Auth response timeout (${timeoutMs}ms)")
        }
        response
    }

    /** Reset auth response state (call before starting auth handshake). */
    fun resetAuthState() {
        _lastAuthResponse.value = null
    }

    /** Reset data-ACK index (call before each upload so a stale index can't match packet 0). */
    fun resetAckState() {
        _lastAckIndex.value = -1
        lastAckStatus = -1
    }

    /** Mark auth as completed for this connection. */
    fun markAuthCompleted() {
        _authCompleted = true
    }

    private fun hasPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        } else {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
               return false
            }
        }
        return true
    }
    
}
