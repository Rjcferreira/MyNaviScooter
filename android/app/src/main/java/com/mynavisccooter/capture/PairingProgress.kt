package com.mynavisccooter.capture

/** Only verified scooter responses advance pairing; UI actions never authorize it. */
class PairingProgress {
    enum class Action { WAIT_FOR_BUTTON, AUTHENTICATE, REJECT, IGNORE }
    private var lastCounter = 0
    private var terminal = false

    fun accept(reply: HandshakeReply): Action {
        if (terminal || reply.command != 0x5C || reply.counter <= lastCounter) return Action.IGNORE
        lastCounter = reply.counter
        return when (reply.index) {
            0 -> Action.WAIT_FOR_BUTTON
            1 -> { terminal = true; Action.AUTHENTICATE }
            else -> { terminal = true; Action.REJECT }
        }
    }
}
