package com.batoh.core.data.usb

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsbSerialManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    
    private val _connectedDevice = MutableStateFlow<UsbDevice?>(null)
    val connectedDevice = _connectedDevice.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus = _connectionStatus.asStateFlow()

    private val permissionIntent = PendingIntent.getBroadcast(
        context, 
        0, 
        Intent(ACTION_USB_PERMISSION), 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
    )

    fun getAvailableDevices(): List<UsbDevice> {
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        return drivers.map { it.device }
    }

    fun hasPermission(device: UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }

    fun requestPermission(device: UsbDevice) {
        usbManager.requestPermission(device, permissionIntent)
    }

    private var serialPort: UsbSerialPort? = null
    private var ioScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    fun connect(device: UsbDevice) {
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
        if (driver == null) {
            _connectionStatus.value = "No driver found"
            return
        }
        
        val connection = usbManager.openDevice(driver.device)
        if (connection == null) {
             _connectionStatus.value = "Connection failed: Permission denied or device busy"
            return
        }

        try {
            val port = driver.ports[0] // Most devices have 1 port
            port.open(connection)
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            port.dtr = true
            port.rts = true // Some bridges need this
            
            serialPort = port
            _connectedDevice.value = device
            _connectionStatus.value = "Connected to ${device.deviceName}"

            // Start reading (if needed for 2-way)
            // For now, we focus on sending
        } catch (e: Exception) {
            _connectionStatus.value = "Error: ${e.message}"
            disconnect()
        }
    }

    fun disconnect() {
        try {
            serialPort?.close()
        } catch (e: Exception) {
            // Ignore
        }
        serialPort = null
        _connectedDevice.value = null
        _connectionStatus.value = "Disconnected"
    }

    suspend fun send(data: ByteArray) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            serialPort?.write(data, 2000)
        } catch (e: Exception) {
            _connectionStatus.value = "Write Error: ${e.message}"
            throw e
        }
    }

    companion object {
        const val ACTION_USB_PERMISSION = "com.batoh.manager.USB_PERMISSION"
    }
}
