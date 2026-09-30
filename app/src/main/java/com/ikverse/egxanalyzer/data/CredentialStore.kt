package com.ikverse.egxanalyzer.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.ikverse.egxanalyzer.model.CloudProvider
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface CredentialStore {
    fun contains(provider: CloudProvider): Boolean
    fun save(provider: CloudProvider, credential: CharArray)
    fun read(provider: CloudProvider): CharArray?
    fun remove(provider: CloudProvider)
}

interface NamedSecretStore {
    fun containsSecret(key: String): Boolean
    fun saveSecret(key: String, secret: CharArray)
    fun readSecret(key: String): CharArray?
    fun removeSecret(key: String)
}

/**
 * Encrypts each credential with a non-exportable AES key held by Android Keystore.
 * No provider credential is sourced from or written to an environment variable.
 */
class AndroidKeystoreCredentialStore(context: Context) : CredentialStore, NamedSecretStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun contains(provider: CloudProvider): Boolean =
        containsSecret(provider.preferenceKey())

    override fun save(provider: CloudProvider, credential: CharArray) {
        saveSecret(provider.preferenceKey(), credential)
    }

    override fun read(provider: CloudProvider): CharArray? = readSecret(provider.preferenceKey())

    override fun remove(provider: CloudProvider) = removeSecret(provider.preferenceKey())

    override fun containsSecret(key: String): Boolean = preferences.contains(key)

    override fun saveSecret(key: String, secret: CharArray) {
        require(secret.isNotEmpty()) { "Secret cannot be empty." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val clearBytes = Charsets.UTF_8.encode(java.nio.CharBuffer.wrap(secret))
        val clear = ByteArray(clearBytes.remaining()).also(clearBytes::get)
        val encrypted = try {
            cipher.doFinal(clear)
        } finally {
            clear.fill(0)
        }
        val packed = cipher.iv + encrypted
        preferences.edit()
            .putString(key, Base64.encodeToString(packed, Base64.NO_WRAP))
            .apply()
        secret.fill('\u0000')
    }

    override fun readSecret(key: String): CharArray? {
        val packed = preferences.getString(key, null)
            ?.let { Base64.decode(it, Base64.NO_WRAP) }
            ?: return null
        if (packed.size <= IV_BYTES) return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_BITS, packed.copyOfRange(0, IV_BYTES)),
        )
        return cipher.doFinal(packed.copyOfRange(IV_BYTES, packed.size))
            .toString(Charsets.UTF_8)
            .toCharArray()
    }

    override fun removeSecret(key: String) {
        preferences.edit().remove(key).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_NAME).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_NAME).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private fun CloudProvider.preferenceKey() = "credential_${name.lowercase()}"

    private companion object {
        const val PREFERENCES_NAME = "egx_secure_credentials"
        const val KEYSTORE_NAME = "AndroidKeyStore"
        const val KEY_ALIAS = "egx_cloud_credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
