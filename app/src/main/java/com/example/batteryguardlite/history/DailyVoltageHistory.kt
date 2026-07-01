package com.example.batteryguardlite.history

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object DailyVoltageHistory {
    private const val MAX_READINGS = 20
    private const val MIN_MILLIVOLTS = 3_000
    private const val MAX_MILLIVOLTS = 32_000

    fun readingFor(millivolts: Int, timestampMillis: Long): DailyVoltageReading {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestampMillis
        }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val dateKey = "%04d-%02d-%02d".format(Locale.US, year, month, day)
        val label = "%02d.%02d".format(Locale.US, day, month)
        return DailyVoltageReading(
            dateKey = dateKey,
            dayIndex = dayIndex(year, month, day),
            label = label,
            millivolts = millivolts
        )
    }

    fun upsert(
        readings: List<DailyVoltageReading>,
        newReading: DailyVoltageReading
    ): List<DailyVoltageReading> {
        if (newReading.millivolts !in MIN_MILLIVOLTS..MAX_MILLIVOLTS) {
            return readings.sortedLimited()
        }

        return (readings.filterNot { it.dateKey == newReading.dateKey } + newReading)
            .sortedLimited()
    }

    fun serialize(readings: List<DailyVoltageReading>): String {
        return readings.sortedLimited()
            .joinToString(separator = "\n") { "${it.dateKey},${it.millivolts}" }
    }

    fun deserialize(raw: String): List<DailyVoltageReading> {
        return raw.lineSequence()
            .mapNotNull { line ->
                val parts = line.trim().split(",")
                if (parts.size != 2) return@mapNotNull null

                val dateKey = parts[0]
                val millivolts = parts[1].toIntOrNull() ?: return@mapNotNull null
                if (millivolts !in MIN_MILLIVOLTS..MAX_MILLIVOLTS) return@mapNotNull null

                readingFromDateKey(dateKey, millivolts)
            }
            .toList()
            .distinctByLatestDate()
            .sortedLimited()
    }

    private fun List<DailyVoltageReading>.sortedLimited(): List<DailyVoltageReading> {
        return sortedBy { it.dayIndex }.takeLast(MAX_READINGS)
    }

    private fun List<DailyVoltageReading>.distinctByLatestDate(): List<DailyVoltageReading> {
        return groupBy { it.dateKey }.values.map { sameDate -> sameDate.last() }
    }

    private fun readingFromDateKey(dateKey: String, millivolts: Int): DailyVoltageReading? {
        val parts = dateKey.split("-")
        if (parts.size != 3) return null

        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null

        val strictDate = runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                isLenient = false
            }.parse(dateKey)
        }.getOrNull() ?: return null

        val calendar = Calendar.getInstance().apply {
            time = strictDate
        }
        val parsedYear = calendar.get(Calendar.YEAR)
        val parsedMonth = calendar.get(Calendar.MONTH) + 1
        val parsedDay = calendar.get(Calendar.DAY_OF_MONTH)

        return DailyVoltageReading(
            dateKey = dateKey,
            dayIndex = dayIndex(parsedYear, parsedMonth, parsedDay),
            label = "%02d.%02d".format(Locale.US, parsedDay, parsedMonth),
            millivolts = millivolts
        )
    }

    private fun dayIndex(year: Int, month: Int, day: Int): Int {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        return 365 * y + y / 4 - y / 100 + y / 400 + (153 * (m - 3) + 2) / 5 + day - 1
    }
}
