package com.mynavisccooter.capture

import org.junit.Assert.*
import org.junit.Test

class X3SettingsReadoutTest {
    private fun row(address: Int, hex: String, device: Int = 0x16) = RegisterCapture(device, address, "setting", 2, hex)

    @Test fun displaysCapturedSettingsWithoutConstructingWrites() {
        val lines = X3SettingsReadout.lines(listOf(row(0x17, "5901"), row(0x42, "0000"),
            row(0x49, "0500"), row(0x82, "5000", 0x07), row(0x48, "1416")))
        assertTrue(lines.contains("VCU: 1.5.9"))
        assertTrue(lines.any { it.contains("sem impulso: ativo") })
        assertTrue(lines.contains("Desligar automaticamente: 5 min"))
        assertTrue(lines.contains("Limite de carga: 80 %"))
        assertTrue(lines.any { it.contains("1614 (hex)") })
    }

    @Test fun missingMalformedOrAmbiguousValuesStayUnavailable() {
        assertTrue(X3SettingsReadout.lines(emptyList()).isEmpty())
        assertTrue(X3SettingsReadout.lines(listOf(row(0x42, "00"))).isEmpty())
        assertTrue(X3SettingsReadout.lines(listOf(row(0x42, "FFFF"))).isEmpty())
        assertTrue(X3SettingsReadout.lines(listOf(row(0x42, "0000"), row(0x42, "0300"))).isEmpty())
        assertTrue(X3SettingsReadout.lines(listOf(row(0x42, "0000", 0x07))).isEmpty())
    }
}
