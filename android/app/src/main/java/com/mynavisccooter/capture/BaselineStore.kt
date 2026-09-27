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

/** Immutable first complete state observed by MyNaviScooter for this installation. */
class BaselineStore(context: Context) {
    private val prefs = context.getSharedPreferences("mynavi_initial_baseline_v1", Context.MODE_PRIVATE)
    private val alias = "mynavi-initial-baseline-v1"

    fun saveFirst(baseline: InitialBaseline): Boolean {
        if (prefs.contains("payload")) return load()?.serial == baseline.serial
        val rows = baseline.registers.joinToString(";") { "${it.device},${it.register},${it.name},${it.expectedBytes},${it.valueHex}" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal((baseline.serial + "\n" + rows).toByteArray())
        val payload = ByteBuffer.allocate(4 + cipher.iv.size + encrypted.size).putInt(cipher.iv.size).put(cipher.iv).put(encrypted).array()
        return prefs.edit().putString("payload", android.util.Base64.encodeToString(payload, android.util.Base64.NO_WRAP)).commit()
    }

    fun load(): InitialBaseline? = runCatching {
        val payload = ByteBuffer.wrap(android.util.Base64.decode(prefs.getString("payload", null) ?: return null, android.util.Base64.DEFAULT))
        val iv = ByteArray(payload.int); payload.get(iv)
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
