package com.mynavisccooter.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepScanPlanTest {
    @Test fun documentedPlanIsUniqueAndReadSized() {
        val rows = Zt3DeepScanPlan.documented
        assertEquals(rows.size, rows.map { it.device to it.register }.distinct().size)
        assertTrue(rows.all { it.length in 1..32 })
        assertTrue(rows.any { it.name == "vcu_serial" && it.length == 14 })
        assertTrue(rows.any { it.name == "vcu_uid" && it.length == 12 })
        assertTrue(rows.any { it.device == 0x04 && it.name == "ble_version" })
        assertTrue(rows.any { it.device == 0x07 && it.name == "charge_threshold" })
    }
}
