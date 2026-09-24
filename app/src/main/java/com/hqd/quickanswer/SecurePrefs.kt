package com.hqd.quickanswer

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecurePrefs(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("secure_settings", Context.MODE_PRIVATE)

    private object Keys {
        const val KEY_ALIAS = "quick_ai_master_key_v1"
        const val PREF_API_KEY = "gemini_api_key"
        const val PREF_MODEL = "gemini_model"
    }

    init {
        getOrCreateKey()
    }

    fun saveApiKey(value: String) {
        if (value.isBlank()) {
            prefs.edit().remove(Keys.PREF_API_KEY).apply()
            return
        }
        val encrypted = encrypt(value)
        prefs.edit().putString(Keys.PREF_API_KEY, encrypted).apply()
    }

    fun getApiKey(): String = prefs.getString(Keys.PREF_API_KEY, null)?.let(::decrypt).orEmpty()

    fun saveModel(value: String) {
        prefs.edit().putString(Keys.PREF_MODEL, value.trim().ifBlank { DEFAULT_MODEL }).apply()
    }

    fun getModel(): String = prefs.getString(Keys.PREF_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL

    private fun getOrCreateKey(): SecretKey {
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(Keys.KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                Keys.KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val cipherText = Base64.encodeToString(cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP)
        return "$iv:$cipherText"
    }

    private fun decrypt(value: String): String {
        return runCatching {
            val parts = value.split(":", limit = 2)
            require(parts.size == 2)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherText), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-3.8-flash"
    }
}
