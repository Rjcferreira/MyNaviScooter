package com.mynavisccooter.capture

/** Counter transition used by ScooterHacking/NinebotCrypto after an RX frame. */
object NinebotCounter {
    fun nextTxAfterReply(replyCounter: Int): Int {
        require(replyCounter in 1 until 65535)
        return replyCounter + 1
    }
}
