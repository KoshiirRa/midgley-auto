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
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

sealed class Obd2ConnectionState {
    object Disconnected : Obd2ConnectionState()
    object Scanning : Obd2ConnectionState()
    data class Connecting(val deviceName: String) : Obd2ConnectionState()
    data class Connected(val deviceName: String, val address: String) : Obd2ConnectionState()
    data class Error(val message: String) : Obd2ConnectionState()
}

/**
 * Manages Bluetooth Low Energy (BLE) peripheral discovery, GATT connection,
 * and periodic Mode 01 PID 0x2F fuel level polling for vehicle OBD2 dongles.
 */
class Obd2BleManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    companion object {
        // Standard BLE GATT UUIDs used across ELM327 / STN / OBD2 adapters
        val UUID_NORDIC_UART_SERVICE: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val UUID_NORDIC_TX_CHAR: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val UUID_NORDIC_RX_CHAR: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

        val UUID_GENERIC_SERVICE: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val UUID_GENERIC_CHAR: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        val UUID_CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val SCAN_TIMEOUT_MILLIS = 15000L
        private val KNOWN_OBD_NAME_PREFIXES = listOf(
            "OBD", "VEEPEAK", "VGATE", "OBDLINK", "IOS-VLINK", "CAR_OBD", "KONNWEI", "LELINK"
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
    private var currentTankCapacity: Double = 15.0

    private val mainHandler = Handler(Looper.getMainLooper())
    private val responseBuffer = StringBuilder()

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
                val name = try { gatt?.device?.name ?: "OBD2 Dongle" } catch (_: SecurityException) { "OBD2 Dongle" }
                val address = gatt?.device?.address ?: "00:00:00:00:00:00"
                _connectionState.value = Obd2ConnectionState.Connected(name, address)
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

            // Check Nordic UART service
            val nordicService = gatt.getService(UUID_NORDIC_UART_SERVICE)
            if (nordicService != null) {
                writeCharacteristic = nordicService.getCharacteristic(UUID_NORDIC_TX_CHAR)
                readChar = nordicService.getCharacteristic(UUID_NORDIC_RX_CHAR)
            }

            // Check Generic FFE0 service fallback
            if (writeCharacteristic == null) {
                val genericService = gatt.getService(UUID_GENERIC_SERVICE)
                if (genericService != null) {
                    val genericChar = genericService.getCharacteristic(UUID_GENERIC_CHAR)
                    writeCharacteristic = genericChar
                    readChar = genericChar
                }
            }

            // Enable notifications on receive characteristic
            readChar?.let { characteristic ->
                gatt.setCharacteristicNotification(characteristic, true)
                val descriptor = characteristic.getDescriptor(UUID_CCCD)
                if (descriptor != null) {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
            }

            // Send initial Mode 01 PID 2F fuel level query
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

    /**
     * Starts BLE scanning for nearby supported OBD2 peripherals.
     */
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

            // Auto timeout scan after 15s to save battery
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

    /**
     * Stops any in-progress BLE scanning.
     */
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

    /**
     * Transmits Mode 01 PID 0x2F query over GATT write characteristic.
     */
    @SuppressLint("MissingPermission")
    fun queryFuelLevel() {
        val gatt = activeGatt ?: return
        val char = writeCharacteristic ?: return

        try {
            char.value = Obd2PidDecoder.PID_FUEL_LEVEL_REQUEST.toByteArray(Charsets.US_ASCII)
            gatt.writeCharacteristic(char)
        } catch (_: SecurityException) {}
    }

    /**
     * Injects synthetic telemetry for testing, demos, or DHU emulator runs.
     */
    fun injectSimulatedTelemetry(
        fuelPercent: Double = 35.0,
        tankCapacityGallons: Double = 15.0
    ) {
        currentTankCapacity = tankCapacityGallons
        _connectionState.value = Obd2ConnectionState.Connected("Simulated OBD2", "DEMO:00:11:22:33")
        _latestTelemetry.value = Obd2PidDecoder.createSimulatedTelemetry(fuelPercent, tankCapacityGallons)
    }

    /**
     * Cleans up and disconnects active GATT connection.
     */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopScan()
        try {
            activeGatt?.disconnect()
            activeGatt?.close()
        } catch (_: SecurityException) {}
        activeGatt = null
        writeCharacteristic = null
        _connectionState.value = Obd2ConnectionState.Disconnected
    }
}