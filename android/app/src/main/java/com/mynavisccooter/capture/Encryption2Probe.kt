package com.mynavisccooter.capture

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

data class PreCommResult(
    val index: Int,
    val authParameterHex: String,
    val reportedSerial: String?,
    val frameHex: String
)

data class AuthResult(
    val accepted: Boolean,
    val frameHex: String
)

object Encryption2Probe {
    // The ZT3/X3 interoperability implementation uses this Gen2-compatible
    // non-SN block for PRE_COMM, even though the advertisement advertises
    // Encryption2. Keep this isolated to the diagnostic probe.
    private val fwData = hexToBytes("97CFB802844143DE56002B3B34780A5D")

    fun buildPreCommFrame(deviceName: String): ByteArray {
        // Plaintext: 5A A5 LEN BT_ID TARGET CMD INDEX PAYLOAD
        val plaintext = byteArrayOf(0x5A, 0xA5.toByte(), 0x00, 0x3E, 0x04, 0x5B, 0x00)
        val key = deriveKey(deviceName.toByteArray(Charsets.UTF_8), fwData)
        val keystream = aesEcb(key, fwData)
        val encryptedBody = xor(plaintext.copyOfRange(3, plaintext.size), keystream)
        val checksum = ((0xFFFF - plaintext.copyOfRange(3, plaintext.size).sumOf { it.toInt() and 0xFF }) and 0xFFFF)
        return plaintext.copyOfRange(0, 3) + encryptedBody + byteArrayOf(
            0x00, 0x00,
            (checksum and 0xFF).toByte(), ((checksum ushr 8) and 0xFF).toByte(),
            0x00, 0x00
        )
    }

    fun parsePreCommFrame(frame: ByteArray, deviceName: String): PreCommResult? {
        if (frame.size < 13 || frame[0] != 0x5A.toByte() || frame[1] != 0xA5.toByte()) return null
        val length = frame[2].toInt() and 0xFF
        val bodyLength = length + 4
        val total = length + 13
        if (length != 30 || frame.size != total) return null
        val key = deriveKey(deviceName.toByteArray(Charsets.UTF_8), fwData)
        val keystream = aesEcb(key, fwData)
        val body = xor(frame.copyOfRange(3, 3 + bodyLength), keystream)
        // Responses reverse the request's source/destination: BLE -> phone.
        if (body[0].toInt() and 0xFF != 0x04) return null
        if (body[1].toInt() and 0xFF != 0x3E || body[2].toInt() and 0xFF != 0x5B) return null
        val footer = 3 + bodyLength
        if (frame[footer] != 0.toByte() || frame[footer + 1] != 0.toByte() ||
            frame[total - 2] != 0.toByte() || frame[total - 1] != 0.toByte()) return null
        val expected = (0xFFFF - body.sumOf { it.toInt() and 0xFF }) and 0xFFFF
        val actual = (frame[footer + 2].toInt() and 0xFF) or
            ((frame[footer + 3].toInt() and 0xFF) shl 8)
        if (expected != actual) return null
        val index = body[3].toInt() and 0xFF
        val data = body.copyOfRange(4, body.size)
        if (data.size < 30) return null
        val auth = data.take(16).toByteArray()
        if (auth.size != 16) return null
        val serial = data.drop(16).toByteArray().toString(Charsets.UTF_8).trim('\u0000', ' ', '\r', '\n').ifBlank { null }
        return PreCommResult(index, bytesToHex(auth) ?: "", serial, bytesToHex(frame) ?: "")
    }

    fun buildAuthFrame(deviceName: String, passwordHex: String, authHex: String, serial: String): ByteArray {
        val password = hexToBytes(passwordHex)
        val auth = hexToBytes(authHex)
        val serialBytes = serial.toByteArray(Charsets.US_ASCII).copyOf(14)
        val plaintext = byteArrayOf(0x5A, 0xA5.toByte(), 0x0E, 0x3E, 0x04, 0x5D, 0x00) + serialBytes
        val key = deriveKey(password, auth)
        val nonce = byteArrayOf(0x00, 0x00, 0x00, 0x01) + auth.copyOf(8) + byteArrayOf(0x00)
        val encryptedBody = ctrXor(key, nonce, plaintext.copyOfRange(3, plaintext.size), 1)
        val tag = cbcMac(key, nonce, plaintext)
        val tagKeystream = aesEcb(key, byteArrayOf(0x01) + nonce + byteArrayOf(0x00, 0x00))
        val encryptedTag = ByteArray(4) { i -> (tag[i].toInt() xor tagKeystream[i].toInt()).toByte() }
        return plaintext.copyOfRange(0, 3) + encryptedBody + encryptedTag + byteArrayOf(0x00, 0x01)
    }

    fun parseAuthFrame(frame: ByteArray, deviceName: String, passwordHex: String, authHex: String): AuthResult? {
        if (frame.size < 27 || frame[0] != 0x5A.toByte() || frame[1] != 0xA5.toByte()) return null
        val length = frame[2].toInt() and 0xFF
        val total = length + 13
        if (length != 14 || frame.size < total) return null
        val counter = ((frame[total - 2].toInt() and 0xFF) shl 8) or (frame[total - 1].toInt() and 0xFF)
        if (counter == 0) return null
        val password = hexToBytes(passwordHex)
        val auth = hexToBytes(authHex)
        val key = deriveKey(password, auth)
        val nonce = byteArrayOf(
            ((counter ushr 24) and 0xFF).toByte(),
            ((counter ushr 16) and 0xFF).toByte(),
            ((counter ushr 8) and 0xFF).toByte(),
            (counter and 0xFF).toByte()
        ) + auth.copyOf(8) + byteArrayOf(0x00)
        val bodyLength = length + 4
        val body = ctrXor(key, nonce, frame.copyOfRange(3, 3 + bodyLength), 1)
        if (body.size < 4 || body[0].toInt() and 0xFF != 0x3E || body[1].toInt() and 0xFF != 0x04 || body[2].toInt() and 0xFF != 0x5D) return null
        return AuthResult(body[3].toInt() and 0xFF == 1, bytesToHex(frame) ?: "")
    }

    private fun deriveKey(key1: ByteArray, key2: ByteArray): ByteArray {
        val input = ByteArray(32)
        key1.copyInto(input, 0, 0, minOf(key1.size, 16))
        key2.copyInto(input, 16, 0, minOf(key2.size, 16))
        return MessageDigest.getInstance("SHA-1").digest(input).copyOf(16)
    }

    private fun aesEcb(key: ByteArray, input: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(input)
    }

    private fun ctrXor(key: ByteArray, nonce: ByteArray, input: ByteArray, startBlock: Int): ByteArray {
        return ByteArray(input.size) { i ->
            val block = startBlock + i / 16
            val counterBlock = byteArrayOf(0x01) + nonce + byteArrayOf(0x00, (block and 0xFF).toByte())
            (input[i].toInt() xor aesEcb(key, counterBlock)[i % 16].toInt()).toByte()
        }
    }

    private fun cbcMac(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray {
        val b0 = byteArrayOf(0x59) + nonce + byteArrayOf(0x00, (plaintext.size - 3).toByte())
        var state = aesEcb(key, b0)
        val aad = plaintext.copyOfRange(0, 3) + ByteArray(13)
        state = aesEcb(key, xor(state, aad))
        val payload = plaintext.copyOfRange(3, plaintext.size)
        var offset = 0
        while (offset < payload.size) {
            val block = payload.copyOfRange(offset, minOf(offset + 16, payload.size)).let { it + ByteArray(16 - it.size) }
            state = aesEcb(key, xor(state, block))
            offset += 16
        }
        return state.copyOf(4)
    }

    private fun xor(left: ByteArray, right: ByteArray): ByteArray = ByteArray(left.size) { i ->
        (left[i].toInt() xor right[i % right.size].toInt()).toByte()
    }

    private fun hexToBytes(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
