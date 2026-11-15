package com.example.batteryguardlite.ble

data class BatteryVoltage(
    val millivolts: Int,
    val volts: Double,
    val rawHex: String,
    val batteryType: BatteryType,
)

enum class BatteryType {
    SIX_V,
    TWELVE_V,
    TWENTY_FOUR_V,
    UNKNOWN,
}

object BatteryVoltageParser {
    private const val MIN_PAYLOAD_BYTES = 3
    private const val BYTE_MASK = 0xFF
    private const val BIT_SHIFT = 8
    private const val MIN_MILLIVOLTS = 3_000
    private const val MAX_MILLIVOLTS = 32_000
    private const val SIX_V_MAX_MILLIVOLTS = 9_000
    private const val TWELVE_V_MAX_MILLIVOLTS = 18_000
    private const val MILLIVOLTS_PER_VOLT = 1_000.0

    fun parse(payload: ByteArray): BatteryVoltage? {
        if (payload.size < MIN_PAYLOAD_BYTES) return null

        val lo = payload[1].toInt() and BYTE_MASK
        val hi = payload[2].toInt() and BYTE_MASK
        val mv = lo or (hi shl BIT_SHIFT)
        if (mv !in MIN_MILLIVOLTS..MAX_MILLIVOLTS) return null

        return BatteryVoltage(
            millivolts = mv,
            volts = mv / MILLIVOLTS_PER_VOLT,
            rawHex = payload.joinToString(" ") { "%02X".format(it) },
            batteryType = classify(mv),
        )
    }

    private fun classify(millivolts: Int): BatteryType =
        when {
            millivolts < SIX_V_MAX_MILLIVOLTS -> BatteryType.SIX_V
            millivolts < TWELVE_V_MAX_MILLIVOLTS -> BatteryType.TWELVE_V
            else -> BatteryType.TWENTY_FOUR_V
        }
}
