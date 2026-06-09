package ai.telegram.android.data.telegram

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object TdLibDatabaseKeyStore {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "ai.telegram.android.tdlib.database_key"
    private const val PREFS_NAME = "tdlib_database_key"
    private const val PREF_CIPHERTEXT = "ciphertext"
    private const val PREF_IV = "iv"
    private const val DATABASE_KEY_BYTES = 32
    private const val GCM_TAG_BITS = 128
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"

    fun getOrCreate(context: Context): ByteArray {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ciphertext = prefs.getString(PREF_CIPHERTEXT, null)
        val iv = prefs.getString(PREF_IV, null)
        if (ciphertext != null && iv != null) {
            val decrypted = runCatching {
                decrypt(
                    ciphertext = Base64.decode(ciphertext, Base64.NO_WRAP),
                    iv = Base64.decode(iv, Base64.NO_WRAP)
                )
            }.getOrNull()
            if (decrypted != null && decrypted.size == DATABASE_KEY_BYTES) return decrypted
        }

        val databaseKey = ByteArray(DATABASE_KEY_BYTES).also { SecureRandom().nextBytes(it) }
        val encrypted = encrypt(databaseKey)
        prefs.edit {
            putString(PREF_CIPHERTEXT, Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP))
            putString(PREF_IV, Base64.encodeToString(encrypted.iv, Base64.NO_WRAP))
        }
        return databaseKey
    }

    fun reset(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit(commit = true) {
            clear()
        }
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                .deleteEntry(KEY_ALIAS)
        }
    }

    fun encodeForJson(databaseKey: ByteArray): String {
        return Base64.encodeToString(databaseKey, Base64.NO_WRAP)
    }

    private fun encrypt(databaseKey: ByteArray): EncryptedDatabaseKey {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey())
        return EncryptedDatabaseKey(
            ciphertext = cipher.doFinal(databaseKey),
            iv = cipher.iv
        )
    }

    private fun decrypt(ciphertext: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateWrappingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private data class EncryptedDatabaseKey(
        val ciphertext: ByteArray,
        val iv: ByteArray
    )
}
