package net.n2yti.midgley.auto.data.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID

sealed class Obd2ConnectionState {
    object Disconnected : Obd2ConnectionState()
    object Scanning : Obd2ConnectionState()
    data class Connecting(val deviceName: String) : Obd2ConnectionState()
    data class Connected(val deviceName: String, val address: String, val protocol: String = "BLE") : Obd2ConnectionState()
    data class Error(val message: String) : Obd2ConnectionState()
}

data class Obd2DeviceInfo(
    val name: String,
    val address: String,
    val isBonded: Boolean = true,
    val type: String = "Classic SPP"
)

/**
 * Manages dual-mode Bluetooth (Classic SPP RFCOMM & BLE GATT) discovery,
 * connection lifecycle, and periodic Mode 01 PID 0x2F fuel level polling.
 */
class Obd2BleManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    companion object {
        // Standard SPP (Serial Port Profile) UUID for Bluetooth Classic ELM327 adapters
        val UUID_SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

        // Standard BLE GATT UUIDs
        val UUID_NORDIC_UART_SERVICE: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val UUID_NORDIC_TX_CHAR: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val UUID_NORDIC_RX_CHAR: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

        val UUID_GENERIC_SERVICE: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val UUID_GENERIC_CHAR: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        val UUID_CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val SCAN_TIMEOUT_MILLIS = 15000L
        val KNOWN_OBD_NAME_PREFIXES = listOf(
            "OBD", "VEEPEAK", "VGATE", "OBDLINK", "IOS-VLINK", "CAR_OBD", "KONNWEI", "LELINK", "BAFX", "VIEOCAR"
        )
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _connectionState = MutableStateFlow<Obd2ConnectionState>(Obd2ConnectionState.Disconnected)
    val connectionState: StateFlow<Obd2ConnectionState> = _connectionState.asStateFlow()

    private val _latestTelemetry = MutableStateFlow<Obd2Telemetry?>(null)
    val latestTelemetry: StateFlow<Obd2Telemetry?> = _latestTelemetry.asStateFlow()

    private var activeGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var classicSocket: BluetoothSocket? = null
    private var classicJob: Job? = null
    private var currentTankCapacity: Double = 15.0

    private val mainHandler = Handler(Looper.getMainLooper())
    private val responseBuffer = StringBuilder()

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<Obd2DeviceInfo> {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return emptyList()
        return try {
            bluetoothAdapter.bondedDevices?.map { device ->
                val name = device.name ?: "Unknown OBD Device"
                val type = when (device.type) {
                    BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
                    BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual (Classic/BLE)"
                    else -> "Classic SPP"
                }
                Obd2DeviceInfo(name, device.address, isBonded = true, type = type)
            }?.sortedByDescending { device ->
                KNOWN_OBD_NAME_PREFIXES.any { prefix -> device.name.uppercase().contains(prefix) }
            } ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToPairedDevice(address: String, tankCapacityGallons: Double = 15.0) {
        currentTankCapacity = tankCapacityGallons
        disconnect()

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _connectionState.value = Obd2ConnectionState.Error("Bluetooth is disabled")
            return
        }

        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: Exception) {
            _connectionState.value = Obd2ConnectionState.Error("Invalid device address: ${e.message}")
            return
        }

        if (device == null) {
            _connectionState.value = Obd2ConnectionState.Error("Device not found")
            return
        }

        val deviceName = try { device.name ?: "OBD2 Device" } catch (_: SecurityException) { "OBD2 Device" }

        // Route BLE-only devices to GATT connection
        if (device.type == BluetoothDevice.DEVICE_TYPE_LE) {
            connectToDevice(device)
            return
        }

        _connectionState.value = Obd2ConnectionState.Connecting(deviceName)

        classicJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                val socket = try {
                    device.createRfcommSocketToServiceRecord(UUID_SPP)
                } catch (e: Exception) {
                    device.createInsecureRfcommSocketToServiceRecord(UUID_SPP)
                }

                socket.connect()
                classicSocket = socket

                _connectionState.value = Obd2ConnectionState.Connected(deviceName, address, "Bluetooth Classic SPP")

                val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.US_ASCII))
                val writer = OutputStreamWriter(socket.outputStream, Charsets.US_ASCII)

                // ELM327 Init Sequence
                val initCommands = listOf("ATZ\r", "ATE0\r", "ATL0\r", "ATH0\r", "ATSP0\r")
                for (cmd in initCommands) {
                    if (!socket.isConnected || !isActive) break
                    writer.write(cmd)
                    writer.flush()
                    delay(300)
                    while (socket.inputStream.available() > 0) {
                        reader.readLine()
                    }
                }

                // Periodic Mode 01 PID 2F Loop
                while (isActive && socket.isConnected) {
                    writer.write("012F\r")
                    writer.flush()
                    delay(500)

                    val responseBuilder = StringBuilder()
                    var attempts = 0
                    while (attempts < 15 && !responseBuilder.contains(">")) {
                        if (socket.inputStream.available() > 0) {
                            val ch = socket.inputStream.read()
                            if (ch != -1) {
                                responseBuilder.append(ch.toChar())
                            }
                        } else {
                            delay(100)
                            attempts++
                        }
                    }

                    val rawResponse = responseBuilder.toString()
                    val telemetry = Obd2PidDecoder.decodeFuelLevelResponse(rawResponse, currentTankCapacity)
                    if (telemetry != null) {
                        _latestTelemetry.value = telemetry
                    }

                    delay(15000L) // Poll every 15s
                }
            } catch (e: Exception) {
                _connectionState.value = Obd2ConnectionState.Error(e.localizedMessage ?: "Connection closed")
                disconnect()
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            val name = try { device.name ?: "" } catch (_: SecurityException) { "" }
            val isKnown = KNOWN_OBD_NAME_PREFIXES.any { prefix -> name.uppercase().contains(prefix) }
            if (isKnown) {
                stopScan()
                connectToDevice(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _connectionState.value = Obd2ConnectionState.Error("BLE scan failed with code $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                val name = try { gatt?.device?.name ?: "BLE OBD2 Dongle" } catch (_: SecurityException) { "BLE OBD2 Dongle" }
                val address = gatt?.device?.address ?: "00:00:00:00:00:00"
                _connectionState.value = Obd2ConnectionState.Connected(name, address, "BLE GATT")
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = Obd2ConnectionState.Disconnected
                activeGatt?.close()
                activeGatt = null
                writeCharacteristic = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) return

            var readChar: BluetoothGattCharacteristic? = null

            val nordicService = gatt.getService(UUID_NORDIC_UART_SERVICE)
            if (nordicService != null) {
                writeCharacteristic = nordicService.getCharacteristic(UUID_NORDIC_TX_CHAR)
                readChar = nordicService.getCharacteristic(UUID_NORDIC_RX_CHAR)
            }

            if (writeCharacteristic == null) {
                val genericService = gatt.getService(UUID_GENERIC_SERVICE)
                if (genericService != null) {
                    val genericChar = genericService.getCharacteristic(UUID_GENERIC_CHAR)
                    writeCharacteristic = genericChar
                    readChar = genericChar
                }
            }

            readChar?.let { characteristic ->
                gatt.setCharacteristicNotification(characteristic, true)
                val descriptor = characteristic.getDescriptor(UUID_CCCD)
                if (descriptor != null) {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
            }

            mainHandler.postDelayed({ queryFuelLevel() }, 1000L)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            val bytes = characteristic?.value ?: return
            val text = String(bytes, Charsets.US_ASCII)
            responseBuffer.append(text)

            if (text.contains(">") || text.contains("\r") || responseBuffer.contains("412F") || responseBuffer.contains("41 2F")) {
                val fullResponse = responseBuffer.toString()
                val telemetry = Obd2PidDecoder.decodeFuelLevelResponse(fullResponse, currentTankCapacity)
                if (telemetry != null) {
                    _latestTelemetry.value = telemetry
                    responseBuffer.clear()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(tankCapacityGallons: Double = 15.0) {
        currentTankCapacity = tankCapacityGallons
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _connectionState.value = Obd2ConnectionState.Error("Bluetooth is disabled or unavailable")
            return
        }

        try {
            val scanner = bluetoothAdapter.bluetoothLeScanner
            if (scanner == null) {
                _connectionState.value = Obd2ConnectionState.Error("BLE scanner unavailable")
                return
            }

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            _connectionState.value = Obd2ConnectionState.Scanning
            scanner.startScan(null, settings, scanCallback)

            mainHandler.postDelayed({
                if (_connectionState.value == Obd2ConnectionState.Scanning) {
                    stopScan()
                    _connectionState.value = Obd2ConnectionState.Disconnected
                }
            }, SCAN_TIMEOUT_MILLIS)
        } catch (e: SecurityException) {
            _connectionState.value = Obd2ConnectionState.Error("Missing Bluetooth permissions: ${e.localizedMessage}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {}
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        try {
            val name = try { device.name ?: "OBD2 Device" } catch (_: SecurityException) { "OBD2 Device" }
            _connectionState.value = Obd2ConnectionState.Connecting(name)
            activeGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            _connectionState.value = Obd2ConnectionState.Error("Connection failed: ${e.localizedMessage}")
        }
    }

    @SuppressLint("MissingPermission")
    fun queryFuelLevel() {
        val gatt = activeGatt ?: return
        val char = writeCharacteristic ?: return

        try {
            char.value = Obd2PidDecoder.PID_FUEL_LEVEL_REQUEST.toByteArray(Charsets.US_ASCII)
            gatt.writeCharacteristic(char)
        } catch (_: SecurityException) {}
    }

    fun injectSimulatedTelemetry(
        fuelPercent: Double = 35.0,
        tankCapacityGallons: Double = 15.0
    ) {
        currentTankCapacity = tankCapacityGallons
        _connectionState.value = Obd2ConnectionState.Connected("Simulated OBD2", "DEMO:00:11:22:33", "Virtual Driver")
        _latestTelemetry.value = Obd2PidDecoder.createSimulatedTelemetry(fuelPercent, tankCapacityGallons)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopScan()
        classicJob?.cancel()
        classicJob = null
        try {
            classicSocket?.close()
        } catch (_: Exception) {}
        classicSocket = null

        try {
            activeGatt?.disconnect()
            activeGatt?.close()
        } catch (_: SecurityException) {}
        activeGatt = null
        writeCharacteristic = null
        _connectionState.value = Obd2ConnectionState.Disconnected
    }
}
