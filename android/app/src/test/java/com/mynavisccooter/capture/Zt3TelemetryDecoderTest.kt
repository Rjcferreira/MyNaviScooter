package com.mynavisccooter.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class Zt3TelemetryDecoderTest {
    @Test fun decodesVerifiedX3Units() {
        val rows = listOf(
            RegisterCapture(0x02, 0x86, "mcu_speed", 2, "FA00"),
            RegisterCapture(0x07, 0x8F, "battery_soc", 2, "4E00"),
            RegisterCapture(0x07, 0x8C, "battery_voltage", 2, "D714"),
            RegisterCapture(0x07, 0x8D, "battery_current", 2, "38FF"),
            RegisterCapture(0x16, 0x5F, "remaining_range", 2, "040B"),
            RegisterCapture(0x16, 0x62, "odometer", 4, "3F310000"),
            RegisterCapture(0x07, 0xA0, "cell_voltages", 26, "0910091009100910091009100910091009100910091009100910")
        )
        val value = Zt3TelemetryDecoder.decode(rows)
        assertEquals(25f, value.speedKmh!!, 0.001f)
        assertEquals(78, value.batteryPercent)
        assertEquals(53.35f, value.voltageV!!, 0.001f)
        assertEquals(-2f, value.currentA!!, 0.001f)
        assertEquals(28.2f, value.rangeKm!!, 0.001f)
        assertEquals(126.07f, value.odometerKm!!, 0.001f)
        assertEquals(13, value.cellVoltagesMv.size)
        assertEquals(4105, value.cellVoltagesMv.first())
    }
}
