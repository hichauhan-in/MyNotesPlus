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
 *  - A single random 256-bit **Data Encryption Key (DEK)** encrypts every synced note/attachment
 *    blob (AES-256-GCM). The same DEK is reused across all of a user's devices, which is what lets
 *    a note encrypted on one phone be decrypted on another.
 *  - The DEK itself never travels in the clear. It is **wrapped** (encrypted) two ways and only the
 *    wrapped copies are stored:
 *      1. Wrapped with a key derived from the user's **recovery passphrase** (PBKDF2). This copy is
 *         zero-knowledge: without the passphrase it is useless, so even someone who gets a copy of
 *         all the synced files (e.g. via a shared Drive) cannot read anything.
 *      2. Wrapped and kept in Drive's private *appDataFolder* for convenient auto-unlock on another
 *         device signed into the same Google account (see [CloudSyncManager]).
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
    // OWASP-recommended floor for PBKDF2-HMAC-SHA256 (2023). High enough to make brute-forcing a
    // strong passphrase against a stolen wrapped-DEK impractical.
    private const val KDF_ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val SALT_LENGTH = 16

    private val secureRandom = SecureRandom()

    /** A fresh random salt for passphrase derivation (not secret; stored alongside the wrapped DEK). */
    fun newSalt(): ByteArray = ByteArray(SALT_LENGTH).also { secureRandom.nextBytes(it) }

    /** A fresh random 256-bit Data Encryption Key. Generated once, then reused across devices. */
    fun newDataKey(): ByteArray = ByteArray(KEY_BITS / 8).also { secureRandom.nextBytes(it) }

    /** Derives a 256-bit key-encryption key from a passphrase + [salt] using PBKDF2. */
    fun deriveKeyFromPassphrase(passphrase: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, KDF_ITERATIONS, KEY_BITS)
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
