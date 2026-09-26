package com.mynavisccooter.capture

import org.junit.Assert.*
import org.junit.Test

class CredentialSelectionTest {
    private val saved = SessionCredentials("TEST0000000001", "11".repeat(16), null)
    private val pending = saved.copy(passwordHex = "22".repeat(16))
    @Test fun savedCredentialWinsOverUnconfirmedCandidate() {
        assertEquals("saved" to saved, CredentialSelection.choose(saved.serialNumber, saved, pending))
    }
    @Test fun pendingRemainsRecoverableWithoutSavedCredential() {
        assertEquals("pending" to pending, CredentialSelection.choose(saved.serialNumber, null, pending))
    }
    @Test fun neverUsesAnotherScootersCredential() {
        assertNull(CredentialSelection.choose("OTHER000000001", saved, pending))
    }
}
