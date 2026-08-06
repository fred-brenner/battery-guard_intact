package com.example.batteryguardlite.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DailyVoltageHistoryTest {
    @Test
    fun sameDayReadingOverwritesPreviousValue() {
        val initial = DailyVoltageHistory.deserialize("2026-06-29,12340")
        val next = DailyVoltageHistory.upsert(
            readings = initial,
            newReading = DailyVoltageHistory.deserialize("2026-06-29,12480").single()
        )

        assertEquals(1, next.size)
        assertEquals(12480, next.single().millivolts)
    }

    @Test
    fun differentDaysCreateSeparateSortedEntries() {
        val next = DailyVoltageHistory.deserialize(
            """
            2026-06-30,12480
            2026-06-28,12100
            2026-06-29,12340
            """.trimIndent()
        )

        assertEquals(listOf("2026-06-28", "2026-06-29", "2026-06-30"), next.map { it.dateKey })
    }

    @Test
    fun moreThanTwentyDaysKeepsLatestTwenty() {
        val raw = (1..25).joinToString("\n") { day ->
            "2026-06-%02d,12%03d".format(day, day)
        }

        val readings = DailyVoltageHistory.deserialize(raw)

        assertEquals(20, readings.size)
        assertEquals("2026-06-06", readings.first().dateKey)
        assertEquals("2026-06-25", readings.last().dateKey)
    }

    @Test
    fun corruptStoredEntriesAreIgnored() {
        val readings = DailyVoltageHistory.deserialize(
            """
            bad
            2026-02-31,12300
            2026-06-29,abc
            2026-06-30,0010
            2026-07-01,12480
            """.trimIndent()
        )

        assertEquals(1, readings.size)
        assertEquals("2026-07-01", readings.single().dateKey)
    }

    @Test
    fun sparseDatesPreserveActualDayGaps() {
        val readings = DailyVoltageHistory.deserialize(
            """
            2026-06-01,12100
            2026-06-10,12400
            2026-07-01,12600
            """.trimIndent()
        )

        assertEquals(9, readings[1].dayIndex - readings[0].dayIndex)
        assertEquals(21, readings[2].dayIndex - readings[1].dayIndex)
    }

    @Test
    fun timestampSurvivesSerialization() {
        val timestampMillis = 1_750_000_123_456L
        val reading = DailyVoltageHistory.readingFor(12480, timestampMillis)

        val restored = DailyVoltageHistory.deserialize(
            DailyVoltageHistory.serialize(listOf(reading))
        ).single()

        assertEquals(timestampMillis, restored.timestampMillis)
        assertEquals(12480, restored.millivolts)
    }

    @Test
    fun legacyReadingWithoutTimestampRemainsSupported() {
        val reading = DailyVoltageHistory.deserialize("2026-06-29,12340").single()

        assertEquals(null, reading.timestampMillis)
    }

    @Test
    fun csvBackupAndRestoreKeepsVoltageAndTimestamp() {
        val original = DailyVoltageReading(
            dateKey = "2026-06-29",
            dayIndex = 739_796,
            label = "29.06",
            millivolts = 12480,
            timestampMillis = 1_751_200_123_456L
        )

        val csv = DailyVoltageHistory.toCsv(listOf(original))
        val result = DailyVoltageHistory.importCsv(emptyList(), csv)

        assertEquals(1, result.importedCount)
        assertEquals(12480, result.readings.single().millivolts)
        assertEquals(1_751_200_123_456L, result.readings.single().timestampMillis)
    }

    @Test
    fun csvRestoreDoesNotOverwriteExistingDate() {
        val existing = DailyVoltageHistory.deserialize("2026-06-29,12100,1000")
        val csv = """
            date,time,voltage_v,millivolts,timestamp_millis
            2026-06-29,12:00:00,12.800,12800,2000
            2026-06-30,13:00:00,12.500,12500,3000
        """.trimIndent()

        val result = DailyVoltageHistory.importCsv(existing, csv)

        assertEquals(2, result.readings.size)
        assertEquals(12100, result.readings.first().millivolts)
        assertEquals(1, result.importedCount)
        assertEquals(1, result.skippedOverlappingCount)
    }

    @Test
    fun csvRestoreKeepsOnlyNewestTwentyDates() {
        val rows = (1..25).joinToString("\n") { day ->
            "2026-07-%02d,12:00:00,12.400,12400,%d".format(day, day.toLong())
        }
        val csv = "date,time,voltage_v,millivolts,timestamp_millis\n$rows"

        val result = DailyVoltageHistory.importCsv(emptyList(), csv)

        assertEquals(20, result.readings.size)
        assertEquals("2026-07-06", result.readings.first().dateKey)
        assertEquals("2026-07-25", result.readings.last().dateKey)
        assertEquals(20, result.importedCount)
    }

    @Test
    fun csvRestoreRejectsNonHistoryCsv() {
        assertThrows(IllegalArgumentException::class.java) {
            DailyVoltageHistory.importCsv(
                existingReadings = emptyList(),
                rawCsv = "name,value\nexample,12"
            )
        }
    }
}
