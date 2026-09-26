package com.mynavisccooter.capture

object CredentialSelection {
    fun choose(serial: String, saved: SessionCredentials?, pending: SessionCredentials?): Pair<String, SessionCredentials>? {
        saved?.takeIf { it.serialNumber == serial }?.let { return "saved" to it }
        pending?.takeIf { it.serialNumber == serial }?.let { return "pending" to it }
        return null
    }
}
