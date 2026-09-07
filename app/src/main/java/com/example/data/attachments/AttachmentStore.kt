package com.example.data.attachments

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import android.util.LruCache
import androidx.core.content.FileProvider
import com.example.data.security.EncryptionManager
import java.io.File
import java.util.UUID

/**
 * Stores note image / audio attachments in the app-private files directory. Files are additionally
 * encrypted at rest with the app's Keystore key (AES-256-GCM), so even a raw copy of the sandbox
 * reveals nothing. Reads decrypt transparently and are served from a small in-memory cache for
 * speed; legacy plaintext files (written before encryption existed) are detected by the absence of
 * the magic header and returned as-is, so nothing ever breaks during the transition.
 */
object AttachmentStore {

    // 4-byte prefix that marks a file as one of our encrypted blobs (magic || IV || ciphertext).
    private val MAGIC = byteArrayOf('V'.code.toByte(), 'N'.code.toByte(), 'A'.code.toByte(), '1'.code.toByte())
    private const val MIGRATION_MARKER = ".enc_migrated"

    // Bounded in-memory cache of DECRYPTED bytes so repeat displays / exports don't re-read+decrypt.
    private const val CACHE_BYTES = 24 * 1024 * 1024 // 24 MB
    private val cache = object : LruCache<String, ByteArray>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    private fun dir(context: Context): File =
        File(context.filesDir, "attachments").apply { mkdirs() }

    // Only the last path segment is ever used, so a maliciously-crafted note token like
    // `attachment://../../databases/...` can never resolve outside the attachments directory.
    fun fileFor(context: Context, name: String): File = File(dir(context), File(name).name)

    /** A fresh, empty destination file for a camera capture. */
    fun newImageFile(context: Context): File =
        File(dir(context), "img_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")

    /** A fresh, empty destination file for a recorded voice note. */
    fun newAudioFile(context: Context): File =
        File(dir(context), "aud_${System.currentTimeMillis()}_${UUID.randomUUID()}.m4a")

    /** A content Uri (via FileProvider) that the camera app can write the capture to. */
    fun uriForFile(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    // ---- Encryption at rest -------------------------------------------------------------------

    private fun isEncrypted(raw: ByteArray): Boolean =
        raw.size >= MAGIC.size && MAGIC.indices.all { raw[it] == MAGIC[it] }

    /** Encrypts [bytes] and writes them to the named attachment file, refreshing the cache. */
    fun writeEncrypted(context: Context, name: String, bytes: ByteArray): Boolean = runCatching {
        val blob = EncryptionManager.encryptBytes(bytes)
        val atomic = AtomicFile(fileFor(context, name))
        val stream = atomic.startWrite()
        try {
            stream.write(MAGIC)
            stream.write(blob)
            atomic.finishWrite(stream)
        } catch (failure: Exception) {
            atomic.failWrite(stream)
            throw failure
        }
        cache.put(name, bytes)
        true
    }.getOrDefault(false)

    /**
     * Reads the named attachment and returns its DECRYPTED bytes (or null if missing/undecryptable).
     * Serves from the in-memory cache when possible; transparently handles legacy plaintext files.
     */
    fun readDecrypted(context: Context, name: String): ByteArray? {
        cache.get(name)?.let { return it }
        val file = fileFor(context, name)
        val raw = runCatching { AtomicFile(file).readFully() }.getOrNull() ?: return null
        val bytes = if (isEncrypted(raw)) {
            EncryptionManager.decryptBytes(raw.copyOfRange(MAGIC.size, raw.size)) ?: return null
        } else {
            raw // legacy plaintext - still usable
        }
        cache.put(name, bytes)
        return bytes
    }

    /** Encrypts an already-written plaintext file (camera capture / voice recording) in place. */
    fun encryptFileInPlace(context: Context, name: String): Boolean {
        val file = fileFor(context, name)
        val raw = runCatching { AtomicFile(file).readFully() }.getOrNull() ?: return false
        if (isEncrypted(raw)) return true
        return writeEncrypted(context, name, raw)
    }

    /**
     * One-off background pass that encrypts any pre-existing plaintext attachment files. Idempotent
     * and cheap on repeat runs (a marker file short-circuits it once everything is encrypted).
     */
    fun migrateExistingToEncrypted(context: Context) {
        runCatching {
            val d = dir(context)
            val marker = File(d, MIGRATION_MARKER)
            if (marker.exists()) return
            var complete = true
            d.listFiles()?.forEach { f ->
                // encryptFileInPlace reads the file and skips anything already encrypted, so this is
                // safe and idempotent regardless of each file's current state.
                if (f.isFile && f.name != MIGRATION_MARKER && !f.name.endsWith(".bak") && !f.name.endsWith(".new")) {
                    if (!encryptFileInPlace(context, f.name)) complete = false
                }
            }
            if (complete) marker.writeText("1")
        }
    }

    /** Copies a picked content Uri into private storage (encrypted); returns the stored name or null. */
    fun importFromUri(context: Context, uri: Uri): String? = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val file = newImageFile(context)
        if (writeEncrypted(context, file.name, bytes)) file.name else null
    }.getOrNull()

    /** Writes a bitmap (e.g. a cropped image) into a fresh private encrypted JPEG; returns its name. */
    fun saveBitmap(context: Context, bitmap: android.graphics.Bitmap): String? = runCatching {
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
        val file = newImageFile(context)
        if (writeEncrypted(context, file.name, out.toByteArray())) file.name else null
    }.getOrNull()

    fun delete(context: Context, name: String) {
        cache.remove(name)
        runCatching { AtomicFile(fileFor(context, name)).delete() }
    }
}
