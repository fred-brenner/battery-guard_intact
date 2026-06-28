package com.example.batteryguardlite.ble

sealed interface BleState {
    data object Idle : BleState
    data object PermissionMissing : BleState
    data object BluetoothOff : BleState
    data object Scanning : BleState
    data class DeviceFound(val address: String, val name: String?) : BleState
    data class Connecting(val address: String) : BleState
    data class Discovering(val address: String) : BleState
    data class Subscribing(val address: String) : BleState
    data class Receiving(
        val address: String,
        val voltage: Double,
        val millivolts: Int,
        val lastUpdateMillis: Long,
        val rawHex: String,
        val batteryType: BatteryType
    ) : BleState
    data class Disconnected(val reason: String) : BleState
    data class Error(val message: String) : BleState
}
