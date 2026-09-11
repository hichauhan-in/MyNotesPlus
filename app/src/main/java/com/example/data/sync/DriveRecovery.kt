package com.example.data.sync

import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

enum class DriveRecoveryMethod(val label: String) {
    PASSPHRASE("Passphrase"), PIN("PIN");

    fun accepts(value: CharArray): Boolean = when (this) {
        PASSPHRASE -> value.size in 8..1024 && value.any { !it.isWhitespace() }
        PIN -> value.size in 4..10 && value.all { it in '0'..'9' }
    }
}

internal data class DriveRecoveryEnvelope(
    val version: Int,
    val salt: ByteArray,
    val wrappedKey: ByteArray,
    val folderId: String?,
    val generation: String?,
    val method: DriveRecoveryMethod,
    val iterations: Int,
    val createdAt: Long,
) {
    val identity: String get() = SyncCrypto.encodeBase64(MessageDigest.getInstance("SHA-256").digest(salt + wrappedKey))
    val templatesName: String get() = generation?.let { "mynotes.templates.$it.json" } ?: "mynotes.templates.json"
    val remindersName: String get() = generation?.let { "mynotes.reminders.$it.json" } ?: "mynotes.reminders.json"

    fun encode(): String = JSONObject().put("version", version)
        .put("salt", SyncCrypto.encodeBase64(salt)).put("wrappedDek", SyncCrypto.encodeBase64(wrappedKey))
        .put("folderId", folderId ?: JSONObject.NULL).put("generation", generation ?: JSONObject.NULL)
        .put("method", method.name).put("iterations", iterations).put("createdAt", createdAt).toString()

    fun unlock(secret: CharArray): ByteArray? {
        val derived = SyncCrypto.deriveKeyFromPassphrase(secret, salt, iterations)
        return try { SyncCrypto.unwrapDataKey(wrappedKey, derived) } finally { derived.fill(0) }
    }

    companion object {
        const val CURRENT_VERSION = 2
        const val NEW_ITERATIONS = 600_000

        fun create(secret: CharArray, method: DriveRecoveryMethod, key: ByteArray, folderId: String): DriveRecoveryEnvelope {
            require(method.accepts(secret)) { if (method == DriveRecoveryMethod.PIN) "Use 4 to 10 digits for the recovery PIN" else "Use at least 8 characters for the recovery passphrase" }
            require(key.size == 32 && validFolderId(folderId))
            val salt = SyncCrypto.newSalt()
            val derived = SyncCrypto.deriveKeyFromPassphrase(secret, salt, NEW_ITERATIONS)
            val wrapped = try { SyncCrypto.wrapDataKey(key, derived) } finally { derived.fill(0) }
            return DriveRecoveryEnvelope(CURRENT_VERSION, salt, wrapped, folderId, UUID.randomUUID().toString(), method, NEW_ITERATIONS, System.currentTimeMillis())
        }

        fun decode(text: String): DriveRecoveryEnvelope {
            require(text.length <= 16_384) { "Recovery metadata is too large" }
            val value = JSONObject(text)
            val version = value.getInt("version")
            require(version in 1..CURRENT_VERSION) { "Update MyNotes+ to read this recovery key" }
            val salt = SyncCrypto.decodeBase64(value.getString("salt"))
            val wrapped = SyncCrypto.decodeBase64(value.getString("wrappedDek"))
            require(salt.size == 16 && wrapped.size == 60) { "Invalid recovery key" }
            val folder = if (version == 1) null else value.getString("folderId")
            val generation = if (version == 1) null else value.getString("generation")
            require(folder == null || validFolderId(folder)) { "Invalid sync folder identifier" }
            require(generation == null || UUID.fromString(generation).toString() == generation) { "Invalid sync generation" }
            val method = if (version == 1) DriveRecoveryMethod.PASSPHRASE else DriveRecoveryMethod.valueOf(value.getString("method"))
            val iterations = if (version == 1) 210_000 else value.getInt("iterations")
            require(iterations == if (version == 1) 210_000 else NEW_ITERATIONS) { "Unsupported recovery key derivation" }
            return DriveRecoveryEnvelope(version, salt, wrapped, folder, generation, method, iterations, value.optLong("createdAt"))
        }

        private fun validFolderId(value: String): Boolean = value.matches(Regex("[A-Za-z0-9_-]{1,256}"))
    }
}