package com.mynavisccooter.capture

import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class ProbeTest {
    // Synthetic device/challenge fixture, independently encoded with .NET AES.
    private val name = "TEST0000000001"
    private fun fixture() = "5AA51E42E13F5AF781F2877B54BA6AB52AA6374AD26A54A3C5A3D04F618C5D8D139C0C76EE0000C8FB0000"
        .chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun acceptsResponseDirectionAndSerial() {
        val result = Encryption2Probe.parsePreCommFrame(fixture(), name)!!
        assertEquals(name, result.reportedSerial)
        assertEquals(1, result.index)
        assertEquals("000102030405060708090A0B0C0D0E0F", result.authParameterHex)
    }
    @Test fun rejectsCorruptionWrongNameTruncationAndRequestEcho() {
        val changed = fixture().also { it[15] = (it[15].toInt() xor 1).toByte() }
        assertNull(Encryption2Probe.parsePreCommFrame(changed, name))
        assertNull(Encryption2Probe.parsePreCommFrame(fixture(), "OTHER000000001"))
        assertNull(Encryption2Probe.parsePreCommFrame(fixture().dropLast(1).toByteArray(), name))
        assertNull(Encryption2Probe.parsePreCommFrame(Encryption2Probe.buildPreCommFrame(name), name))
    }
    @Test fun reassemblesEveryPossibleFragmentBoundary() {
        for (split in 1 until fixture().size) {
            val buffer = ProbeFrameBuffer()
            assertTrue(buffer.append(fixture().copyOfRange(0, split)).isEmpty())
            assertArrayEquals(fixture(), buffer.append(fixture().copyOfRange(split, fixture().size)).single())
        }
    }
    @Test fun handlesNoiseCombinedFramesAndSessionReset() {
        val buffer = ProbeFrameBuffer()
        assertEquals(2, buffer.append(byteArrayOf(1, 2, 3) + fixture() + fixture()).size)
        buffer.append(fixture().copyOfRange(0, 20))
        buffer.clear()
        assertArrayEquals(fixture(), buffer.append(fixture()).single())
    }
    @Test fun utcIsIndependentOfPhoneTimezone() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("GMT+12"))
            val value = CaptureReport.nowUtc()
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals(CaptureReport.nowUtc().take(13), value.take(13))
        } finally { TimeZone.setDefault(original) }
    }
    @Test fun sharedAdvertisementDoesNotProveExactModel() {
        assertEquals("low", ModelProfiles.fromAdvertisement("unknown", "434E0100020000FC").confidence)
    }
}
