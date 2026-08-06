package com.example.batteryguardlite.history

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class CsvImportResult(
    val readings: List<DailyVoltageReading>,
    val importedCount: Int,
    val skippedOverlappingCount: Int
)

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
            millivolts = millivolts,
            timestampMillis = timestampMillis
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
            .joinToString(separator = "\n") { reading ->
                listOfNotNull(
                    reading.dateKey,
                    reading.millivolts.toString(),
                    reading.timestampMillis?.toString()
                ).joinToString(",")
            }
    }

    fun deserialize(raw: String): List<DailyVoltageReading> {
        return raw.lineSequence()
            .mapNotNull { line ->
                val parts = line.trim().split(",")
                if (parts.size !in 2..3) return@mapNotNull null

                val dateKey = parts[0]
                val millivolts = parts[1].toIntOrNull() ?: return@mapNotNull null
                if (millivolts !in MIN_MILLIVOLTS..MAX_MILLIVOLTS) return@mapNotNull null
                val timestampMillis = parts.getOrNull(2)?.toLongOrNull()
                if (parts.size == 3 && timestampMillis == null) return@mapNotNull null

                readingFromDateKey(dateKey, millivolts, timestampMillis)
            }
            .toList()
            .distinctByLatestDate()
            .sortedLimited()
    }

    fun toCsv(readings: List<DailyVoltageReading>): String {
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
        return buildString {
            appendLine(CSV_HEADER)
            readings.sortedLimited().forEach { reading ->
                val time = reading.timestampMillis?.let { timeFormat.format(Date(it)) }.orEmpty()
                append(reading.dateKey)
                append(',')
                append(time)
                append(',')
                append(String.format(Locale.US, "%.3f", reading.volts))
                append(',')
                append(reading.millivolts)
                append(',')
                append(reading.timestampMillis ?: "")
                appendLine()
            }
        }
    }

    fun importCsv(
        existingReadings: List<DailyVoltageReading>,
        rawCsv: String
    ): CsvImportResult {
        val lines = rawCsv.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.isNotEmpty()) { "The CSV file is empty" }

        val header = parseCsvLine(lines.first()).mapIndexed { index, value ->
            val cleaned = if (index == 0) value.removePrefix("\uFEFF") else value
            cleaned.trim().lowercase(Locale.US)
        }
        val dateIndex = header.indexOf("date")
        val timeIndex = header.indexOf("time")
        val voltageIndex = listOf("voltage_v", "voltage", "volts")
            .firstNotNullOfOrNull { name -> header.indexOf(name).takeIf { it >= 0 } } ?: -1
        val millivoltsIndex = listOf("millivolts", "voltage_mv")
            .firstNotNullOfOrNull { name -> header.indexOf(name).takeIf { it >= 0 } } ?: -1
        val timestampIndex = listOf("timestamp_millis", "timestamp")
            .firstNotNullOfOrNull { name -> header.indexOf(name).takeIf { it >= 0 } } ?: -1

        require(dateIndex >= 0 && (millivoltsIndex >= 0 || voltageIndex >= 0)) {
            "CSV must contain a date column and a voltage or millivolts column"
        }

        val existingDates = existingReadings.mapTo(mutableSetOf()) { it.dateKey }
        var skippedOverlappingCount = 0
        val importedByDate = linkedMapOf<String, DailyVoltageReading>()

        lines.drop(1).forEach { line ->
            val columns = parseCsvLine(line)
            val dateKey = columns.valueAt(dateIndex).trim()
            if (dateKey in existingDates) {
                skippedOverlappingCount += 1
                return@forEach
            }
            if (dateKey in importedByDate) return@forEach

            val millivolts = columns.valueAt(millivoltsIndex).toIntOrNull()
                ?: columns.valueAt(voltageIndex).toDoubleOrNull()?.times(1000.0)?.roundToInt()
                ?: return@forEach
            if (millivolts !in MIN_MILLIVOLTS..MAX_MILLIVOLTS) return@forEach

            val explicitTimestamp = columns.valueAt(timestampIndex).toLongOrNull()
            val parsedTimestamp = explicitTimestamp ?: parseCsvTimestamp(
                dateKey = dateKey,
                time = columns.valueAt(timeIndex)
            )
            val reading = readingFromDateKey(dateKey, millivolts, parsedTimestamp)
                ?: return@forEach
            importedByDate[dateKey] = reading
        }

        val combined = (existingReadings + importedByDate.values).sortedLimited()
        val retainedImportedDates = combined.mapTo(mutableSetOf()) { it.dateKey }
            .intersect(importedByDate.keys)
        return CsvImportResult(
            readings = combined,
            importedCount = retainedImportedDates.size,
            skippedOverlappingCount = skippedOverlappingCount
        )
    }

    private fun List<DailyVoltageReading>.sortedLimited(): List<DailyVoltageReading> {
        return sortedBy { it.dayIndex }.takeLast(MAX_READINGS)
    }

    private fun List<DailyVoltageReading>.distinctByLatestDate(): List<DailyVoltageReading> {
        return groupBy { it.dateKey }.values.map { sameDate -> sameDate.last() }
    }

    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val value = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && line.getOrNull(index + 1) == '"' -> {
                    value.append('"')
                    index += 1
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    values += value.toString()
                    value.clear()
                }
                else -> value.append(char)
            }
            index += 1
        }
        require(!inQuotes) { "CSV contains an unterminated quoted value" }
        values += value.toString()
        return values
    }

    private fun List<String>.valueAt(index: Int): String {
        return if (index >= 0) getOrNull(index).orEmpty().trim() else ""
    }

    private fun parseCsvTimestamp(dateKey: String, time: String): Long? {
        if (time.isBlank()) return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                isLenient = false
            }.parse("$dateKey ${time.trim()}")?.time
        }.getOrNull()
    }

    private fun readingFromDateKey(
        dateKey: String,
        millivolts: Int,
        timestampMillis: Long?
    ): DailyVoltageReading? {
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
            millivolts = millivolts,
            timestampMillis = timestampMillis
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

    private const val CSV_HEADER = "date,time,voltage_v,millivolts,timestamp_millis"
}
