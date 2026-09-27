package com.mynavisccooter.capture

/** Only verified scooter responses advance pairing; UI actions never authorize it. */
class PairingProgress {
    enum class Action { AUTHENTICATE, REJECT, IGNORE }
    private var lastCounter = 0
    private var terminal = false

    fun accept(reply: HandshakeReply): Action {
        if (terminal || reply.command != 0x5C || reply.counter <= lastCounter) return Action.IGNORE
        lastCounter = reply.counter
        return when (reply.index) {
            // ZT3 firmware acknowledges the proposed app random with either
            // 00 or 01. Both move the cipher to the paired session key; 0x5D
            // then completes physical confirmation.
            0, 1 -> { terminal = true; Action.AUTHENTICATE }
            else -> { terminal = true; Action.REJECT }
        }
    }
}
