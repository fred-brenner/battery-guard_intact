package com.example.batteryguardlite.ble

import android.Manifest
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
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class BatteryBleManager(
    private val context: Context
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _state = MutableStateFlow<BleState>(BleState.Idle)
    val state: StateFlow<BleState> = _state.asStateFlow()

    private var currentGatt: BluetoothGatt? = null
    private var isUserStopped = true
    private var isScanning = false

    private val scanTimeoutRunnable = Runnable {
        if (isScanning) {
            stopScan()
            _state.value = BleState.Disconnected("Device not found")
        }
    }

    private val reconnectRunnable = Runnable {
        if (!isUserStopped) start()
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::handleScanResult)
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            _state.value = BleState.Error("BLE scan failed: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device?.address.orEmpty()
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                _state.value = BleState.Discovering(address)
                gatt.discoverServices()
                return
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                closeGatt(gatt)
                _state.value = BleState.Disconnected("Disconnected; retrying...")
                scheduleReconnect()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failProtocol(gatt, "Service discovery failed: $status")
                return
            }

            val service = gatt.getService(VOLTAGE_SERVICE_UUID)
            val characteristic = service?.getCharacteristic(VOLTAGE_CHARACTERISTIC_UUID)
            if (characteristic == null) {
                failProtocol(gatt, "Voltage service or characteristic missing")
                return
            }

            val descriptor = characteristic.getDescriptor(CCCD_UUID)
            if (descriptor == null) {
                failProtocol(gatt, "Notification descriptor missing")
                return
            }

            _state.value = BleState.Subscribing(gatt.device?.address.orEmpty())
            enableNotifications(gatt, characteristic, descriptor)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (descriptor.uuid != CCCD_UUID) return

            if (status != BluetoothGatt.GATT_SUCCESS) {
                failProtocol(gatt, "Notification subscription failed: $status")
                return
            }

            _state.value = BleState.Subscribing(gatt.device?.address.orEmpty())
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleNotification(gatt, characteristic.uuid, value)
        }

        @Deprecated("Used by Android 12 and older")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            handleNotification(gatt, characteristic.uuid, characteristic.value ?: return)
        }
    }

    fun hasBluetoothAdapter(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun hasRequiredPermissions(): Boolean {
        return requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun markPermissionMissing() {
        _state.value = BleState.PermissionMissing
    }

    fun markBluetoothOff() {
        _state.value = BleState.BluetoothOff
    }

    @SuppressLint("MissingPermission")
    fun start() {
        isUserStopped = false
        mainHandler.removeCallbacks(reconnectRunnable)

        if (!hasRequiredPermissions()) {
            _state.value = BleState.PermissionMissing
            return
        }

        if (!hasBluetoothAdapter()) {
            _state.value = BleState.Error("Bluetooth is not available on this device")
            return
        }

        if (!isBluetoothEnabled()) {
            _state.value = BleState.BluetoothOff
            return
        }

        if (isScanning || currentGatt != null) return

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            _state.value = BleState.Error("BLE scanner is unavailable")
            return
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(VOLTAGE_SERVICE_UUID))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        _state.value = BleState.Scanning
        isScanning = true
        scanner.startScan(filters, settings, scanCallback)
        mainHandler.postDelayed(scanTimeoutRunnable, SCAN_TIMEOUT_MILLIS)
    }

    fun stop() {
        isUserStopped = true
        mainHandler.removeCallbacks(scanTimeoutRunnable)
        mainHandler.removeCallbacks(reconnectRunnable)
        stopScan()
        closeGatt(currentGatt)
        _state.value = BleState.Idle
    }

    fun destroy() {
        stop()
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return
        val scanName = result.scanRecord?.deviceName
        val deviceName = runCatching { device.name }.getOrNull()
        val name = scanName ?: deviceName

        if (name != DEVICE_NAME) return

        stopScan()
        _state.value = BleState.DeviceFound(device.address, name)
        connect(device)
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        if (currentGatt != null) return

        _state.value = BleState.Connecting(device.address)
        currentGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!isScanning) return
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
        mainHandler.removeCallbacks(scanTimeoutRunnable)
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        descriptor: BluetoothGattDescriptor
    ) {
        val notificationEnabled = gatt.setCharacteristicNotification(characteristic, true)
        if (!notificationEnabled) {
            failProtocol(gatt, "Could not enable local notifications")
            return
        }

        val writeStarted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }

        if (!writeStarted) {
            failProtocol(gatt, "Could not write notification descriptor")
        }
    }

    private fun handleNotification(
        gatt: BluetoothGatt,
        characteristicUuid: UUID,
        value: ByteArray
    ) {
        if (characteristicUuid != VOLTAGE_CHARACTERISTIC_UUID) return

        val parsed = BatteryVoltageParser.parse(value) ?: return
        _state.value = BleState.Receiving(
            address = gatt.device?.address.orEmpty(),
            voltage = parsed.volts,
            millivolts = parsed.millivolts,
            lastUpdateMillis = System.currentTimeMillis(),
            rawHex = parsed.rawHex,
            batteryType = parsed.batteryType
        )
    }

    private fun scheduleReconnect() {
        if (isUserStopped) return
        mainHandler.removeCallbacks(reconnectRunnable)
        mainHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MILLIS)
    }

    private fun failProtocol(gatt: BluetoothGatt, message: String) {
        closeGatt(gatt)
        _state.value = BleState.Error(message)
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt(gatt: BluetoothGatt?) {
        if (gatt == null) return
        runCatching {
            gatt.disconnect()
            gatt.close()
        }
        if (currentGatt === gatt) currentGatt = null
    }

    companion object {
        const val DEVICE_NAME = "BLE-VoltageCheck"
        private const val SCAN_TIMEOUT_MILLIS = 15_000L
        private const val RECONNECT_DELAY_MILLIS = 2_000L
        val VOLTAGE_SERVICE_UUID: UUID = UUID.fromString("00001814-0000-1000-8000-00805f9b34fb")
        val VOLTAGE_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002a53-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
