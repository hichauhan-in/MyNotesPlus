package com.example.data.share

import android.content.Context
import android.util.Base64
import com.example.data.attachments.AttachmentStore
import com.example.data.sync.SyncCrypto
import com.example.domain.model.AttachmentMarkup
import com.example.domain.model.Note
import com.example.domain.model.NoteType
import kotlinx.coroutines.Dispatchers
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
    const val MIN_PASSPHRASE = 4
    private const val MAGIC = "mynotes.share"
    private const val VERSION = 1

    /** Encrypts [note] (plus its attachment bytes) with [passphrase] into shareable file bytes. */
    suspend fun exportEncrypted(context: Context, note: Note, passphrase: CharArray): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val attachments = JSONArray()
                val names = (note.attachments + AttachmentMarkup.fileNames(note.content)).distinct()
                names.forEach { name ->
                    val bytes = AttachmentStore.readDecrypted(context, name)
                    if (bytes != null) {
                        attachments.put(
                            JSONObject()
                                .put("name", name)
                                .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP)),
                        )
                    }
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
                val encrypted = SyncCrypto.encrypt(payload.toByteArray(Charsets.UTF_8), key)
                JSONObject()
                    .put("app", MAGIC)
                    .put("v", VERSION)
                    .put("salt", SyncCrypto.encodeBase64(salt))
                    .put("data", SyncCrypto.encodeBase64(encrypted))
                    .toString()
                    .toByteArray(Charsets.UTF_8)
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

    /**
     * Decrypts share-file [bytes] with [passphrase], writing any attachments into this device's
     * store and returning a brand-new [Note] (fresh id, no book/pin/favourite state) to be saved.
     */
    suspend fun importEncrypted(context: Context, bytes: ByteArray, passphrase: CharArray): ImportResult =
        withContext(Dispatchers.IO) {
            try {
                val env = JSONObject(String(bytes, Charsets.UTF_8))
                if (env.optString("app") != MAGIC) return@withContext ImportResult.Invalid
                val salt = SyncCrypto.decodeBase64(env.getString("salt"))
                val data = SyncCrypto.decodeBase64(env.getString("data"))
                val key = SyncCrypto.deriveKeyFromPassphrase(passphrase, salt)
                val decrypted = SyncCrypto.decrypt(data, key)
                    ?: return@withContext ImportResult.WrongPassphrase
                val payload = JSONObject(String(decrypted, Charsets.UTF_8))

                val attArr = payload.optJSONArray("attachments") ?: JSONArray()
                for (i in 0 until attArr.length()) {
                    val a = attArr.getJSONObject(i)
                    val name = a.optString("name")
                    if (name.isNotBlank()) {
                        val b = runCatching { Base64.decode(a.optString("data"), Base64.NO_WRAP) }.getOrNull()
                        if (b != null) AttachmentStore.writeEncrypted(context, name, b)
                    }
                }
                val tags = payload.optJSONArray("tags")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
                } ?: emptyList()
                val type = runCatching { NoteType.valueOf(payload.optString("type")) }.getOrDefault(NoteType.TEXT)
                val content = payload.optString("content")
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
                    attachments = AttachmentMarkup.fileNames(content),
                )
                ImportResult.Success(note)
            } catch (e: Exception) {
                ImportResult.Invalid
            } finally {
                passphrase.fill('\u0000')
            }
        }
}
