package com.mynavisccooter.capture

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SessionCredentials(
    val serialNumber: String,
    val passwordHex: String,
    val commandIdentHex: String?
)

object CredentialParser {
    fun parse(value: String): Result<SessionCredentials> = runCatching {
        val uri = Uri.parse(value.trim())
        require(uri.scheme.equals("segway", true)) { "A credencial deve começar por segway://" }
        require(uri.host.equals("credentials", true)) { "Formato de credencial inválido" }
        val serial = uri.getQueryParameter("sn").orEmpty()
        val password = uri.getQueryParameter("pwd").orEmpty().lowercase()
        val ident = uri.getQueryParameter("ident")?.lowercase()
        require(serial.isNotBlank()) { "Serial em falta" }
        require(password.matches(Regex("[0-9a-f]{32}"))) { "pwd deve conter 32 caracteres hex" }
        require(ident == null || ident.matches(Regex("[0-9a-f]{32}"))) { "ident deve conter 32 caracteres hex" }
        SessionCredentials(serial, password, ident)
    }
}

class EncryptedCredentialStore(context: Context, slot: String = "mynavi_credentials") {
    private val prefs = context.getSharedPreferences(slot, Context.MODE_PRIVATE)
    private val alias = "mynavi-session-credentials-v1"

    fun save(credentials: SessionCredentials) {
        val plain = listOf(credentials.serialNumber, credentials.passwordHex, credentials.commandIdentHex.orEmpty())
            .joinToString("\u0000")
            .toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(plain)
        val payload = ByteBuffer.allocate(4 + cipher.iv.size + encrypted.size)
            .putInt(cipher.iv.size)
            .put(cipher.iv)
            .put(encrypted)
            .array()
        check(prefs.edit().putString("payload", android.util.Base64.encodeToString(payload, android.util.Base64.NO_WRAP)).commit()) {
            "Não foi possível guardar a credencial"
        }
    }

    fun hasCredentials(): Boolean = prefs.contains("payload")

    fun load(): SessionCredentials? = runCatching {
        val encoded = prefs.getString("payload", null) ?: return null
        val payload = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
        val buffer = ByteBuffer.wrap(payload)
        val iv = ByteArray(buffer.int)
        buffer.get(iv)
        val encrypted = ByteArray(buffer.remaining())
        buffer.get(encrypted)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        val fields = cipher.doFinal(encrypted).toString(Charsets.UTF_8).split("\u0000")
        SessionCredentials(fields[0], fields[1], fields.getOrNull(2)?.ifBlank { null })
    }.getOrNull()

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false)
            .build())
        return generator.generateKey()
    }
}
