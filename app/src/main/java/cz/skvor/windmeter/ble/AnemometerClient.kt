package cz.skvor.windmeter.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class FoundDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    /** True when the device advertises the FFF0 service the WT87B uses. */
    val advertisesService: Boolean,
) {
    /** The WT87B advertises itself as "Anemometer" (per the user manual). */
    val isWt87b: Boolean get() = name.trim().equals(Wt87bProtocol.ADVERTISED_NAME, ignoreCase = true)
    val looksLikeAnemometer: Boolean get() = isWt87b || advertisesService
}

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val name: String) : ConnectionState
    data class Connected(val name: String) : ConnectionState
}

/**
 * Talks to one WT87B over Bluetooth LE. Callers must hold the Bluetooth permissions
 * before calling [startScan] or [connect]; the UI asks for them first.
 */
@SuppressLint("MissingPermission")
class AnemometerClient(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter

    private val _devices = MutableStateFlow<List<FoundDevice>>(emptyList())
    val devices: StateFlow<List<FoundDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _readings = MutableSharedFlow<RawReading>(extraBufferCapacity = 16)
    val readings: SharedFlow<RawReading> = _readings.asSharedFlow()

    private var gatt: BluetoothGatt? = null
    private var pollingJob: Job? = null
    private var scanStopJob: Job? = null

    val isBluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name ?: return
            val advertisesService = result.scanRecord?.serviceUuids
                ?.contains(ParcelUuid(Wt87bProtocol.SERVICE)) == true
            val device = FoundDevice(result.device.address, name, result.rssi, advertisesService)
            _devices.update { list ->
                (list.filterNot { it.address == device.address } + device)
                    .sortedWith(compareByDescending<FoundDevice> { it.isWt87b }.thenByDescending { it.looksLikeAnemometer }.thenByDescending { it.rssi })
            }
        }
    }

    fun startScan(durationMillis: Long = 10_000) {
        val scanner = adapter?.bluetoothLeScanner ?: return
        if (_isScanning.value) return
        _devices.value = emptyList()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(null, settings, scanCallback)
        _isScanning.value = true
        scanStopJob = scope.launch {
            delay(durationMillis)
            stopScan()
        }
    }

    fun stopScan() {
        scanStopJob?.cancel()
        if (_isScanning.value) adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        _isScanning.value = false
    }

    fun connect(address: String, name: String) {
        stopScan()
        disconnect()
        val device = adapter?.getRemoteDevice(address) ?: return
        _connection.value = ConnectionState.Connecting(name)
        gatt = device.connectGatt(appContext, false, GattCallback(name), BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        pollingJob?.cancel()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _connection.value = ConnectionState.Disconnected
    }

    private inner class GattCallback(private val name: String) : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> gatt.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    pollingJob?.cancel()
                    gatt.close()
                    if (this@AnemometerClient.gatt == gatt) this@AnemometerClient.gatt = null
                    _connection.value = ConnectionState.Disconnected
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val notify = gatt.findCharacteristic(Wt87bProtocol.NOTIFY_CHARACTERISTIC)
            val write = gatt.findCharacteristic(Wt87bProtocol.WRITE_CHARACTERISTIC)
            if (status != BluetoothGatt.GATT_SUCCESS || notify == null || write == null) {
                disconnect()
                return
            }
            gatt.setCharacteristicNotification(notify, true)
            val descriptor = notify.getDescriptor(Wt87bProtocol.CLIENT_CONFIG_DESCRIPTOR)
            if (descriptor != null) {
                gatt.writeDescriptorCompat(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                startPolling(gatt, write)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            val write = gatt.findCharacteristic(Wt87bProtocol.WRITE_CHARACTERISTIC) ?: return
            startPolling(gatt, write)
        }

        private fun startPolling(gatt: BluetoothGatt, write: BluetoothGattCharacteristic) {
            _connection.value = ConnectionState.Connected(name)
            pollingJob?.cancel()
            pollingJob = scope.launch {
                while (isActive) {
                    gatt.writeCharacteristicCompat(write, Wt87bProtocol.REQUEST_LIVE_READING)
                    delay(1_000)
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleNotification(value)
        }

        @Deprecated("Called instead of the ByteArray overload before Android 13.")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                handleNotification(characteristic.value ?: return)
            }
        }

        private fun handleNotification(value: ByteArray) {
            Wt87bProtocol.parseLiveReading(value)?.let { _readings.tryEmit(it) }
        }
    }
}

private fun BluetoothGatt.findCharacteristic(uuid: java.util.UUID): BluetoothGattCharacteristic? =
    services.firstNotNullOfOrNull { it.getCharacteristic(uuid) }

@SuppressLint("MissingPermission")
private fun BluetoothGatt.writeDescriptorCompat(descriptor: BluetoothGattDescriptor, value: ByteArray) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        writeDescriptor(descriptor, value)
    } else {
        @Suppress("DEPRECATION")
        descriptor.value = value
        @Suppress("DEPRECATION")
        writeDescriptor(descriptor)
    }
}

@SuppressLint("MissingPermission")
private fun BluetoothGatt.writeCharacteristicCompat(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
    // The vendor app writes without response; the meter answers through a notification instead.
    val writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        writeCharacteristic(characteristic, value, writeType)
    } else {
        @Suppress("DEPRECATION")
        characteristic.writeType = writeType
        @Suppress("DEPRECATION")
        characteristic.value = value
        @Suppress("DEPRECATION")
        writeCharacteristic(characteristic)
    }
}
