package com.example.batteryguardlite.history

import android.content.Context

class BatteryHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun readings(): List<DailyVoltageReading> {
        return DailyVoltageHistory.deserialize(preferences.getString(KEY_READINGS, "").orEmpty())
    }

    fun saveReading(millivolts: Int, timestampMillis: Long): List<DailyVoltageReading> {
        val next = DailyVoltageHistory.upsert(
            readings = readings(),
            newReading = DailyVoltageHistory.readingFor(millivolts, timestampMillis)
        )
        preferences.edit()
            .putString(KEY_READINGS, DailyVoltageHistory.serialize(next))
            .apply()
        return next
    }

    companion object {
        private const val PREFERENCES_NAME = "battery_history"
        private const val KEY_READINGS = "daily_readings"
    }
}
