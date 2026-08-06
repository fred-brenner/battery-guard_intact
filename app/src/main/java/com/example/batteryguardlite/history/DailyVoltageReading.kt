package com.example.batteryguardlite.history

data class DailyVoltageReading(
    val dateKey: String,
    val dayIndex: Int,
    val label: String,
    val millivolts: Int,
    val timestampMillis: Long? = null
) {
    val volts: Double = millivolts / 1000.0
}
