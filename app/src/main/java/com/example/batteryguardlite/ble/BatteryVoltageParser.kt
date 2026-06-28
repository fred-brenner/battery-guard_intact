package com.example.batteryguardlite.ble

data class BatteryVoltage(
    val millivolts: Int,
    val volts: Double,
    val rawHex: String,
    val batteryType: BatteryType
)

enum class BatteryType {
    SIX_V,
    TWELVE_V,
    TWENTY_FOUR_V,
    UNKNOWN
}

object BatteryVoltageParser {
    fun parse(payload: ByteArray): BatteryVoltage? {
        if (payload.size < 3) return null

        val lo = payload[1].toInt() and 0xFF
        val hi = payload[2].toInt() and 0xFF
        val mv = lo or (hi shl 8)

        if (mv < 3000 || mv > 32000) return null

        val type = when {
            mv < 9000 -> BatteryType.SIX_V
            mv < 18000 -> BatteryType.TWELVE_V
            else -> BatteryType.TWENTY_FOUR_V
        }

        return BatteryVoltage(
            millivolts = mv,
            volts = mv / 1000.0,
            rawHex = payload.joinToString(" ") { "%02X".format(it) },
            batteryType = type
        )
    }
}
