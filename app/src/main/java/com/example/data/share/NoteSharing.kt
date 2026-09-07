package com.example.data.share

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.data.attachments.AttachmentStore
import com.example.data.sync.SyncCrypto
import com.example.domain.model.AttachmentMarkup
import com.example.domain.model.Note
import com.example.domain.model.NoteType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * In-app, end-to-end-encrypted note sharing. A note (and its attachment bytes) is packed into a
 * small encrypted `.mynote` file locked by a passphrase the sender chooses; the recipient - another
 * MyNotes user - imports the file and enters the same passphrase to unlock it. Nothing is uploaded
 * anywhere and no other app can read the contents. The passphrase never travels inside the file
 * (a wrong one simply fails the AES-GCM authentication), so it must be shared out-of-band.
 */
object NoteSharing {

    const val FILE_EXTENSION = "mynote"
    const val MIME = "application/octet-stream"
    const val MIN_PASSPHRASE = 12
    private const val MAGIC = "mynotes.share"
    private const val VERSION = 1

    /** Encrypts [note] (plus its attachment bytes) with [passphrase] into shareable file bytes. */
    suspend fun exportEncrypted(context: Context, note: Note, passphrase: CharArray): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                require(passphrase.size >= MIN_PASSPHRASE)
                require(note.content.length <= ShareImportPolicy.MAX_CONTENT_CHARS)
                val attachments = JSONArray()
                val names = (note.attachments + AttachmentMarkup.fileNames(note.content)).distinct()
                require(names.size <= ShareImportPolicy.MAX_ATTACHMENTS)
                var totalBytes = 0L
                names.forEach { name ->
                    require(AttachmentStore.fileFor(context, name).length() <= ShareImportPolicy.MAX_ATTACHMENT_BYTES + 64L)
                    val bytes = requireNotNull(AttachmentStore.readDecrypted(context, name))
                    require(bytes.size <= ShareImportPolicy.MAX_ATTACHMENT_BYTES)
                    totalBytes += bytes.size
                    require(totalBytes <= ShareImportPolicy.MAX_ATTACHMENTS_BYTES)
                        attachments.put(
                            JSONObject()
                                .put("name", name)
                                .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP)),
                        )
                }
                val payload = JSONObject()
                    .put("title", note.title)
                    .put("content", note.content)
                    .put("type", note.type.name)
                    .put("color", note.colorArgb)
                    .put("tags", JSONArray(note.tags))
                    .put("attachments", attachments)
                    .toString()

                val salt = SyncCrypto.newSalt()
                val key = SyncCrypto.deriveKeyFromPassphrase(passphrase, salt)
                val encrypted = try { SyncCrypto.encrypt(payload.toByteArray(Charsets.UTF_8), key) } finally { key.fill(0) }
                JSONObject()
                    .put("app", MAGIC)
                    .put("v", VERSION)
                    .put("salt", SyncCrypto.encodeBase64(salt))
                    .put("data", SyncCrypto.encodeBase64(encrypted))
                    .toString()
                    .toByteArray(Charsets.UTF_8)
                    .also { require(it.size <= ShareImportPolicy.MAX_FILE_BYTES) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                null
            } finally {
                passphrase.fill('\u0000')
            }
        }

    /** Distinguishes a wrong passphrase from a file that isn't a MyNotes share at all. */
    sealed interface ImportResult {
        data class Success(val note: Note) : ImportResult
        object WrongPassphrase : ImportResult
        object Invalid : ImportResult
    }

    suspend fun importFromUri(context: Context, uri: Uri, passphrase: CharArray): ImportResult = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { ShareImportPolicy.readBounded(it) }
                ?: return@withContext ImportResult.Invalid
            importEncrypted(context, bytes, passphrase)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ImportResult.Invalid
        } finally {
            passphrase.fill('\u0000')
        }
    }

    /**
     * Decrypts share-file [bytes] with [passphrase], writing any attachments into this device's
     * store and returning a brand-new [Note] (fresh id, no book/pin/favourite state) to be saved.
     */
    suspend fun importEncrypted(context: Context, bytes: ByteArray, passphrase: CharArray): ImportResult =
        withContext(Dispatchers.IO) {
            val written = mutableListOf<String>()
            var imported = false
            try {
                require(bytes.size <= ShareImportPolicy.MAX_FILE_BYTES)
                val env = JSONObject(String(bytes, Charsets.UTF_8))
                if (env.optString("app") != MAGIC || env.optInt("v") != VERSION) return@withContext ImportResult.Invalid
                val salt = SyncCrypto.decodeBase64(env.getString("salt"))
                require(salt.size == 16)
                val data = SyncCrypto.decodeBase64(env.getString("data"))
                val key = SyncCrypto.deriveKeyFromPassphrase(passphrase, salt)
                val decrypted = try { SyncCrypto.decrypt(data, key) } finally { key.fill(0) }
                    ?: return@withContext ImportResult.WrongPassphrase
                val payload = JSONObject(String(decrypted, Charsets.UTF_8))

                val attArr = payload.optJSONArray("attachments") ?: JSONArray()
                require(attArr.length() <= ShareImportPolicy.MAX_ATTACHMENTS)
                val replacements = linkedMapOf<String, String>()
                val decodedAttachments = linkedMapOf<String, ByteArray>()
                var totalBytes = 0L
                for (i in 0 until attArr.length()) {
                    val a = attArr.getJSONObject(i)
                    val original = a.getString("name")
                    require(original !in replacements) { "Duplicate attachment name" }
                    val name = ShareImportPolicy.freshName(original)
                    val attachment = Base64.decode(a.getString("data"), Base64.NO_WRAP)
                    require(attachment.size <= ShareImportPolicy.MAX_ATTACHMENT_BYTES)
                    totalBytes += attachment.size
                    require(totalBytes <= ShareImportPolicy.MAX_ATTACHMENTS_BYTES)
                    replacements[original] = name
                    decodedAttachments[name] = attachment
                }
                val tags = payload.optJSONArray("tags")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
                } ?: emptyList()
                val type = NoteType.valueOf(payload.getString("type"))
                val content = ShareImportPolicy.renameContent(payload.getString("content"), type == NoteType.SCRIBBLE, replacements)
                val now = System.currentTimeMillis()
                val note = Note(
                    id = UUID.randomUUID().toString(),
                    title = payload.optString("title"),
                    content = content,
                    createdAt = now,
                    updatedAt = now,
                    tags = tags,
                    colorArgb = payload.optInt("color", 0),
                    type = type,
                    attachments = replacements.values.toList(),
                )
                decodedAttachments.forEach { (name, attachment) ->
                    written.add(name)
                    check(AttachmentStore.writeEncrypted(context, name, attachment)) { "Could not store an attachment" }
                }
                imported = true
                ImportResult.Success(note)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                ImportResult.Invalid
            } finally {
                if (!imported) written.forEach { AttachmentStore.delete(context, it) }
                passphrase.fill('\u0000')
            }
        }
}
