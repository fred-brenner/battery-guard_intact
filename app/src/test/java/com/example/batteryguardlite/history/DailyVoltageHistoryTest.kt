package com.example.batteryguardlite.history

import org.junit.Assert.assertEquals
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
}
