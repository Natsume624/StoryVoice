package com.storyvoice.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("cloud_settings", Context.MODE_PRIVATE)
    private val alias = "storyvoice_dashscope_key"

    var endpoint: String
        get() = prefs.getString("endpoint", DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT
        set(value) { prefs.edit().putString("endpoint", value.trim().trimEnd('/')).apply() }

    fun hasApiKey(): Boolean = loadApiKey().isNotBlank()

    fun loadApiKey(): String = runCatching {
        val encrypted = Base64.decode(prefs.getString("api_key", ""), Base64.NO_WRAP)
        val iv = Base64.decode(prefs.getString("api_key_iv", ""), Base64.NO_WRAP)
        if (encrypted.isEmpty() || iv.isEmpty()) return@runCatching ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrDefault("")

    fun saveApiKey(value: String) {
        val clean = value.trim()
        if (clean.isBlank()) {
            prefs.edit().remove("api_key").remove("api_key_iv").apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        prefs.edit()
            .putString("api_key", Base64.encodeToString(cipher.doFinal(clean.toByteArray()), Base64.NO_WRAP))
            .putString("api_key_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://dashscope.aliyuncs.com/api/v1"
    }
}
