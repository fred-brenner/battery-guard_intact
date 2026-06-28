package com.example.batteryguardlite.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryVoltageParserTest {
    @Test
    fun parsesValidTwelveVoltPayload() {
        val parsed = BatteryVoltageParser.parse(byteArrayOf(0x00, 0x34, 0x30))

        requireNotNull(parsed)
        assertEquals(12340, parsed.millivolts)
        assertEquals(12.34, parsed.volts, 0.0)
        assertEquals(BatteryType.TWELVE_V, parsed.batteryType)
        assertEquals("00 34 30", parsed.rawHex)
    }

    @Test
    fun parsesValidSixVoltRange() {
        val parsed = BatteryVoltageParser.parse(byteArrayOf(0x00, 0x2E, 0x16))

        requireNotNull(parsed)
        assertEquals(5678, parsed.millivolts)
        assertEquals(BatteryType.SIX_V, parsed.batteryType)
    }

    @Test
    fun parsesValidTwentyFourVoltRange() {
        val parsed = BatteryVoltageParser.parse(byteArrayOf(0x00, 0x10, 0x5E))

        requireNotNull(parsed)
        assertEquals(24080, parsed.millivolts)
        assertEquals(BatteryType.TWENTY_FOUR_V, parsed.batteryType)
    }

    @Test
    fun returnsNullForShortPayload() {
        val parsed = BatteryVoltageParser.parse(byteArrayOf(0x00, 0x34))

        assertNull(parsed)
    }

    @Test
    fun returnsNullForLowOutOfRangePayload() {
        val parsed = BatteryVoltageParser.parse(byteArrayOf(0x00, 0x01, 0x00))

        assertNull(parsed)
    }

    @Test
    fun parsesLittleEndianMillivolts() {
        val parsed = BatteryVoltageParser.parse(byteArrayOf(0x00, 0x01, 0x30))

        requireNotNull(parsed)
        assertEquals(0x3001, parsed.millivolts)
    }
}
