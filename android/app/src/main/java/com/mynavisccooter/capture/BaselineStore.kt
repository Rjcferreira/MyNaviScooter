package com.mynavisccooter.capture

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class InitialBaseline(val serial: String, val registers: List<RegisterCapture>) {
    fun register(device: Int, address: Int) = registers.singleOrNull { it.device == device && it.register == address }
}

/** Immutable first captured register snapshot per serial; not a full firmware backup. */
class BaselineStore(context: Context, preferencesName: String = "mynavi_initial_baseline_v1") {
    private val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val alias = "mynavi-initial-baseline-v1"

    @Synchronized fun saveFirst(baseline: InitialBaseline): Boolean {
        if (!valid(baseline)) return false
        val slot = slot(baseline.serial)
        // A damaged existing snapshot must never be silently replaced.
        if (prefs.contains(slot)) return load(baseline.serial) != null
        if (prefs.contains("payload")) {
            val legacy = decode(prefs.getString("payload", null)) ?: return false
            if (!valid(legacy)) return false
            if (legacy.serial == baseline.serial) {
                return prefs.edit().putString(slot, prefs.getString("payload", null)).commit() && load(baseline.serial) != null
            }
        }
        val rows = baseline.registers.joinToString(";") { "${it.device},${it.register},${it.name},${it.expectedBytes},${it.valueHex}" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal((baseline.serial + "\n" + rows).toByteArray())
        val payload = ByteBuffer.allocate(4 + cipher.iv.size + encrypted.size).putInt(cipher.iv.size).put(cipher.iv).put(encrypted).array()
        return prefs.edit().putString(slot, android.util.Base64.encodeToString(payload, android.util.Base64.NO_WRAP)).commit() && load(baseline.serial) != null
    }

    fun load(serial: String): InitialBaseline? {
        val slot = slot(serial)
        val raw = if (prefs.contains(slot)) prefs.getString(slot, null) else prefs.getString("payload", null)
        return decode(raw)?.takeIf { it.serial == serial && valid(it) }
    }

    private fun slot(serial: String): String = "snapshot_v2_" + java.security.MessageDigest.getInstance("SHA-256")
        .digest(serial.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun valid(baseline: InitialBaseline): Boolean =
        baseline.serial.matches(Regex("[A-Za-z0-9]{1,32}")) && baseline.registers.isNotEmpty() &&
            baseline.registers.size <= 512 &&
            baseline.registers.map { it.device to it.register }.distinct().size == baseline.registers.size &&
            baseline.registers.all { it.device in 0..255 && it.register in 0..255 &&
                it.name.matches(Regex("[A-Za-z0-9_]{1,80}")) && it.expectedBytes in 1..512 &&
                it.valueHex.length == it.expectedBytes * 2 && it.valueHex.matches(Regex("[0-9a-fA-F]+")) }

    private fun decode(raw: String?): InitialBaseline? = runCatching {
        if (raw == null || raw.length > 800_000) return null
        val payload = ByteBuffer.wrap(android.util.Base64.decode(raw, android.util.Base64.DEFAULT))
        val ivSize = payload.int
        if (ivSize != 12 || payload.remaining() < ivSize + 16) return null
        val iv = ByteArray(ivSize); payload.get(iv)
        val encrypted = ByteArray(payload.remaining()); payload.get(encrypted)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        val text = cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        val serial = text.substringBefore('\n')
        val rows = text.substringAfter('\n').split(';').filter { it.isNotBlank() }.map { row ->
            val f = row.split(','); RegisterCapture(f[0].toInt(), f[1].toInt(), f[2], f[3].toInt(), f[4])
        }
        InitialBaseline(serial, rows)
    }.getOrNull()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
}
