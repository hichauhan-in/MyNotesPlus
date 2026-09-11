package com.example.data.sync

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption for cloud sync.
 *
 * The model is "envelope encryption":
 *  - A random 256-bit **Data Encryption Key (DEK)** encrypts synced notes, templates and reminders
 *    using AES-256-GCM. Devices participating in the same sync setup use the same DEK.
 *  - The DEK is wrapped using PBKDF2 and the user's recovery passphrase or PIN. Only this encrypted
 *    envelope is stored in Drive's hidden appDataFolder. A copied envelope permits offline credential
 *    guessing; short PINs offer much less protection than long passphrases.
 *  - [CloudSyncManager] separately wraps the local DEK with Android Keystore for automatic sync on
 *    an unlocked device. Google account authorization alone does not unlock a new device.
 *
 * Nothing here touches the Android Keystore, precisely because the DEK must be reproducible on a
 * different device - a Keystore key never leaves the device that made it.
 */
object SyncCrypto {

    private const val AES = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128

    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    // Legacy envelope work factor; new recovery envelopes supply their versioned value explicitly.
    private const val KDF_ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val SALT_LENGTH = 16

    private val secureRandom = SecureRandom()

    /** A fresh random salt for passphrase derivation (not secret; stored alongside the wrapped DEK). */
    fun newSalt(): ByteArray = ByteArray(SALT_LENGTH).also { secureRandom.nextBytes(it) }

    /** A fresh random 256-bit Data Encryption Key. Generated once, then reused across devices. */
    fun newDataKey(): ByteArray = ByteArray(KEY_BITS / 8).also { secureRandom.nextBytes(it) }

    /** Derives a 256-bit key-encryption key from a passphrase + [salt] using PBKDF2. */
    fun deriveKeyFromPassphrase(passphrase: CharArray, salt: ByteArray, iterations: Int = KDF_ITERATIONS): ByteArray {
        require(iterations in KDF_ITERATIONS..1_300_000 && salt.size == SALT_LENGTH) { "Unsupported key derivation parameters" }
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Wraps (encrypts) the [dataKey] with a key-encryption key. Returns `IV || ciphertext`. */
    fun wrapDataKey(dataKey: ByteArray, keyEncryptionKey: ByteArray): ByteArray =
        aesGcmEncrypt(dataKey, keyEncryptionKey)

    /**
     * Unwraps the [wrapped] DEK with the key-encryption key, or returns null if the key is wrong
     * (GCM authentication fails). A null result is exactly how a wrong passphrase is detected -
     * no separate password "verifier" is needed or stored.
     */
    fun unwrapDataKey(wrapped: ByteArray, keyEncryptionKey: ByteArray): ByteArray? =
        aesGcmDecrypt(wrapped, keyEncryptionKey)

    /** Encrypts a note/attachment [plain] blob with the DEK. Returns `IV || ciphertext`. */
    fun encrypt(plain: ByteArray, dataKey: ByteArray): ByteArray = aesGcmEncrypt(plain, dataKey)

    /** Decrypts a synced blob with the DEK, or null if it isn't authentic (tampered/wrong key). */
    fun decrypt(blob: ByteArray, dataKey: ByteArray): ByteArray? = aesGcmDecrypt(blob, dataKey)

    fun encodeBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    fun decodeBase64(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

    private fun aesGcmEncrypt(plain: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, AES), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ct = cipher.doFinal(plain)
        return ByteArray(iv.size + ct.size).also {
            System.arraycopy(iv, 0, it, 0, iv.size)
            System.arraycopy(ct, 0, it, iv.size, ct.size)
        }
    }

    private fun aesGcmDecrypt(blob: ByteArray, key: ByteArray): ByteArray? = try {
        val iv = blob.copyOfRange(0, GCM_IV_LENGTH)
        val ct = blob.copyOfRange(GCM_IV_LENGTH, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, AES), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.doFinal(ct)
    } catch (e: Exception) {
        null
    }
}
