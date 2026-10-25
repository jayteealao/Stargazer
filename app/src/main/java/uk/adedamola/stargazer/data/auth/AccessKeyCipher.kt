package uk.adedamola.stargazer.data.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.InvalidKeyException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts and decrypts the GitHub access key. The plain key never reaches disk.
 */
interface AccessKeyCipher {
    fun encrypt(plain: String): ByteArray

    /** Throws when the blob cannot be decrypted, including when the encryption key is missing. */
    fun decrypt(blob: ByteArray): String

    fun deleteKey()
}

/**
 * AES-256-GCM with a key that lives in the Android Keystore and never leaves it.
 *
 * Blob layout: `[version byte][12-byte IV][ciphertext + 16-byte tag]`.
 * Decryption never creates a key: a missing alias is a failure, so ciphertext restored
 * onto another device (without its Keystore key) reads as "no key".
 */
class AndroidKeystoreCipher(private val alias: String) : AccessKeyCipher {

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    override fun encrypt(plain: String): ByteArray {
        val cipher = try {
            initEncrypt(getOrCreateKey())
        } catch (e: InvalidKeyException) {
            // Includes KeyPermanentlyInvalidatedException: replace the unusable key once.
            deleteKey()
            initEncrypt(getOrCreateKey())
        }
        val iv = cipher.iv
        check(iv.size == IV_LENGTH) { "Unexpected IV length" }
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return byteArrayOf(VERSION) + iv + sealed
    }

    override fun decrypt(blob: ByteArray): String {
        require(blob.size > 1 + IV_LENGTH + TAG_LENGTH_BYTES) { "Ciphertext too short" }
        require(blob[0] == VERSION) { "Unknown ciphertext version" }
        val key = keyStore().getKey(alias, null) as? SecretKey
            ?: throw IllegalStateException("Encryption key missing")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BYTES * 8, blob, 1, IV_LENGTH))
        cipher.updateAAD(ASSOCIATED_DATA)
        val plain = cipher.doFinal(blob, 1 + IV_LENGTH, blob.size - 1 - IV_LENGTH)
        return String(plain, Charsets.UTF_8)
    }

    override fun deleteKey() {
        val store = keyStore()
        if (store.containsAlias(alias)) store.deleteEntry(alias)
    }

    private fun initEncrypt(key: SecretKey): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key)
            updateAAD(ASSOCIATED_DATA)
        }

    private fun getOrCreateKey(): SecretKey {
        (keyStore().getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION: Byte = 0x01
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BYTES = 16
        val ASSOCIATED_DATA = "stargazer:github-access-key:v1".toByteArray(Charsets.UTF_8)
    }
}
