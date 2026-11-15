package com.example.batteryguardlite.history

data class DailyVoltageReading(
    val dateKey: String,
    val dayIndex: Int,
    val label: String,
    val millivolts: Int,
    val timestampMillis: Long? = null,
) {
    val volts: Double = millivolts / MILLIVOLTS_PER_VOLT

    companion object {
        const val MILLIVOLTS_PER_VOLT = 1_000.0
    }
}
