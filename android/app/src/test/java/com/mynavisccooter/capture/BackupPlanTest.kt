package com.mynavisccooter.capture

import org.junit.Assert.*
import org.junit.Test

class BackupPlanTest {
    @Test fun requiredRegistersAreUniqueAndReadFramesCarryRequestedCounter() {
        assertEquals(Zt3BackupPlan.required.size,
            Zt3BackupPlan.required.map { it.device to it.register }.distinct().size)
        assertTrue(Zt3BackupPlan.required.all { it.length > 0 })
        val frame = Encryption2Probe.buildReadRegisterFrame(
            "101112131415161718191A1B1C1D1E1F",
            "000102030405060708090A0B0C0D0E0F", 0x16, 0x42, 2, 4)
        assertEquals(4, frame.last().toInt() and 0xFF)
        assertEquals(14, frame.size)
    }
}
