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

object Encryption2Probe {
    // Modern X3/Gen3 PRE_COMM uses a zero second key block. The fw_data
    // variant belongs to older Gen2 devices and must not be guessed here.
    private val zeroKeyBlock = ByteArray(16)

    fun buildPreCommFrame(deviceName: String): ByteArray {
        // Plaintext: 5A A5 LEN BT_ID TARGET CMD INDEX PAYLOAD
        val plaintext = byteArrayOf(0x5A, 0xA5.toByte(), 0x00, 0x3E, 0x04, 0x5B, 0x00)
        val key = deriveKey(deviceName.toByteArray(Charsets.UTF_8), zeroKeyBlock)
        val keystream = aesEcb(key, ByteArray(16))
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
        if (frame.size < total || frame.size < bodyLength + 9) return null
        val key = deriveKey(deviceName.toByteArray(Charsets.UTF_8), zeroKeyBlock)
        val keystream = aesEcb(key, ByteArray(16))
        val body = xor(frame.copyOfRange(3, 3 + bodyLength), keystream)
        if (body.size < 4 || body[0].toInt() and 0xFF != 0x3E) return null
        if (body[1].toInt() and 0xFF != 0x04 || body[2].toInt() and 0xFF != 0x5B) return null
        val index = body[3].toInt() and 0xFF
        val data = body.copyOfRange(4, body.size)
        if (data.size < 30) return null
        val auth = data.take(16).toByteArray()
        if (auth.size != 16) return null
        val serial = data.drop(16).toByteArray().toString(Charsets.UTF_8).trim('\u0000', ' ', '\r', '\n').ifBlank { null }
        return PreCommResult(index, bytesToHex(auth) ?: "", serial, bytesToHex(frame) ?: "")
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

    private fun xor(left: ByteArray, right: ByteArray): ByteArray = ByteArray(left.size) { i ->
        (left[i].toInt() xor right[i % right.size].toInt()).toByte()
    }

    private fun hexToBytes(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
