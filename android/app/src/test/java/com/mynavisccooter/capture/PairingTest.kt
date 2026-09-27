package com.mynavisccooter.capture

import org.junit.Assert.*
import org.junit.Test

class PairingTest {
    private val name = "TEST0000000001"
    private val auth = "000102030405060708090A0B0C0D0E0F"
    private val password = "101112131415161718191A1B1C1D1E1F"
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    // Synthetic fixtures encoded independently by NootNooot's Python NbCrypto.
    private val waiting = hex("5aa5003fdbc100439641e90002")
    private val accepted = hex("5aa500cf81ad467e5144860003")
    private val authenticated = hex("5aa5006a1c0be84028f2640004")

    @Test fun outgoingMessagesMatchIndependentImplementation() {
        assertArrayEquals(hex("5aa51005e1c10048d63c7ed8994a1627f04175eff939b25d7351d50002"),
            Encryption2Probe.buildPairingFrame(name, password, auth))
        assertArrayEquals(hex("5aa50e7a26104905c15f605692b7712473ed5decc2721d263f0003"),
            Encryption2Probe.buildAuthFrame(name, password, auth, name, 3))
    }

    @Test fun wireCounterIsExplicitAndAdvancesAcrossHandshakeFrames() {
        val pair2 = Encryption2Probe.buildPairingFrame(name, password, auth, 2)
        val pair5 = Encryption2Probe.buildPairingFrame(name, password, auth, 5)
        assertEquals(0, pair2[pair2.lastIndex - 1].toInt() and 0xFF)
        assertEquals(2, pair2.last().toInt() and 0xFF)
        assertEquals(5, pair5.last().toInt() and 0xFF)
        assertFalse(pair2.contentEquals(pair5))

        val auth5 = Encryption2Probe.buildAuthFrame(name, password, auth, name, 5)
        assertEquals(5, auth5.last().toInt() and 0xFF)
    }

    @Test fun scooterReplyAdvancesToExactlyTheNextWireCounter() {
        // Captured ZT3 sequence: pair TX=5, pair ACK RX=6, AUTH must be TX=7.
        assertEquals(7, NinebotCounter.nextTxAfterReply(6))
        val auth7 = Encryption2Probe.buildAuthFrame(name, password, auth, name, 7)
        assertEquals(7, auth7.last().toInt() and 0xFF)
    }

    @Test fun zeroPayloadRepliesAreValidAndOnlyPeerReplyAuthorizes() {
        val progress = PairingProgress()
        val wait = Encryption2Probe.parsePairingFrame(waiting, name, auth)!!
        assertEquals(PairingProgress.Action.WAIT_FOR_BUTTON, progress.accept(wait))
        assertEquals(PairingProgress.Action.IGNORE, progress.accept(wait))
        val acceptedProgress = PairingProgress()
        val accept = Encryption2Probe.parsePairingFrame(accepted, name, auth)!!
        assertEquals(PairingProgress.Action.AUTHENTICATE, acceptedProgress.accept(accept))
        assertEquals(PairingProgress.Action.IGNORE, acceptedProgress.accept(accept))
        assertTrue(Encryption2Probe.parseAuthFrame(authenticated, password, auth)!!.accepted)
    }

    @Test fun rejectsCorruptWrongKeyWrongCommandEchoAndTruncatedReplies() {
        for (i in accepted.indices) {
            val bad = accepted.clone().also { it[i] = (it[i].toInt() xor 1).toByte() }
            assertNull(Encryption2Probe.parsePairingFrame(bad, name, auth))
        }
        assertNull(Encryption2Probe.parsePairingFrame(accepted, "OTHER000000001", auth))
        assertNotNull(Encryption2Probe.parsePairingFrame(accepted, name, auth, password))
        assertNull(Encryption2Probe.parseAuthFrame(accepted, password, auth))
        assertNull(Encryption2Probe.parsePairingFrame(Encryption2Probe.buildPairingFrame(name, password, auth), name, auth))
        assertNull(Encryption2Probe.parsePairingFrame(accepted.dropLast(1).toByteArray(), name, auth))
        assertNull(Encryption2Probe.parseAuthFrame(authenticated, "00000000000000000000000000000000", auth))
    }

    @Test fun rejectsUnknownStatusAndIgnoresOutOfOrderReplies() {
        val progress = PairingProgress()
        assertEquals(PairingProgress.Action.WAIT_FOR_BUTTON, progress.accept(HandshakeReply(0x5C, 0, 4)))
        assertEquals(PairingProgress.Action.IGNORE, progress.accept(HandshakeReply(0x5C, 1, 3)))
        assertEquals(PairingProgress.Action.AUTHENTICATE, progress.accept(HandshakeReply(0x5C, 1, 6)))
        val rejected = PairingProgress()
        assertEquals(PairingProgress.Action.REJECT, rejected.accept(HandshakeReply(0x5C, 2, 5)))
        assertEquals(PairingProgress.Action.IGNORE, progress.accept(HandshakeReply(0x5C, 1, 7)))
    }

    @Test fun fragmentedPairingRepliesAreReassembled() {
        for (split in 1 until accepted.size) {
            val buffer = ProbeFrameBuffer()
            assertTrue(buffer.append(accepted.copyOfRange(0, split)).isEmpty())
            val frame = buffer.append(accepted.copyOfRange(split, accepted.size)).single()
            assertEquals(1, Encryption2Probe.parsePairingFrame(frame, name, auth)!!.index)
        }
    }
}
