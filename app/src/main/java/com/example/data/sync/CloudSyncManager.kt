package com.example.data.sync

import android.content.Context
import com.example.data.repository.FolderRepository
import com.example.data.repository.NoteRepository
import com.example.data.repository.ReminderRepository
import com.example.data.security.EncryptionManager
import com.example.data.settings.SettingsRepository
import com.example.domain.model.CustomTemplate
import com.example.domain.model.Note
import com.example.domain.model.NoteType
import com.example.domain.model.Reminder
import com.example.domain.model.ReminderRepeat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Orchestrates the end-to-end-encrypted cloud sync: key setup (recovery passphrase + envelope) and
 * the two-way note sync itself.
 *
 * Key model (see [SyncCrypto] for the crypto details):
 *  - A random **Data Encryption Key (DEK)** is generated once per account and reused on every
 *    device. It encrypts every synced note blob (AES-256-GCM).
 *  - The DEK is wrapped with a key derived from the user's **recovery passphrase** and the wrapped
 *    copy (the "envelope") is stored in Drive's hidden *appDataFolder* - never the plaintext DEK.
 *  - On this device the unlocked DEK is cached wrapped by the Android Keystore, so we only ask for
 *    the passphrase once per device (or when restoring on a new one).
 *
 * [syncNow] performs the two-way, last-write-wins sync of note records to/from the visible folder.
 */
class CloudSyncManager(
    private val settings: SettingsRepository,
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository,
    private val reminderRepository: ReminderRepository,
    private val appContext: Context,
) {

    enum class RemoteState { NO_ENVELOPE, HAS_ENVELOPE, ERROR }
    enum class RestoreResult { SUCCESS, WRONG_PASSPHRASE, ERROR }

    /** Outcome of a two-way sync. */
    sealed interface SyncOutcome {
        data class Success(
            val pushed: Int,
            val pulled: Int,
            val deletedLocal: Int,
            val deletedRemote: Int,
        ) : SyncOutcome

        /** This device hasn't unlocked the key yet (needs the recovery passphrase). */
        object NotUnlocked : SyncOutcome
        object Error : SyncOutcome
    }

    /** Whether the connected account already has a recovery envelope on Drive. Network call. */
    suspend fun remoteState(accessToken: String): RemoteState = withContext(Dispatchers.IO) {
        // Confirm we can reach Drive first; only then trust an "absent" result. This avoids ever
        // mistaking a transient failure for "no envelope" (which would prompt a new passphrase and
        // overwrite the real key, orphaning existing notes).
        if (DriveRest.fetchAccountEmail(accessToken) == null) return@withContext RemoteState.ERROR
        when (DriveRest.appDataFileExists(accessToken, ENVELOPE_NAME)) {
            true -> RemoteState.HAS_ENVELOPE
            false -> RemoteState.NO_ENVELOPE
            null -> RemoteState.ERROR
        }
    }

    /** True if this device already holds the unlocked DEK (so no passphrase prompt is needed). */
    suspend fun hasLocalKey(): Boolean = settings.wrappedDataKey() != null

    /**
     * First-time setup for the account: generate a DEK, wrap it with [passphrase], store the
     * envelope in appDataFolder, create the visible "MyNotes" folder, and cache the DEK locally.
     * The [passphrase] array is zeroed before returning.
     */
    suspend fun provision(accessToken: String, passphrase: CharArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val dek = SyncCrypto.newDataKey()
            val salt = SyncCrypto.newSalt()
            val kek = SyncCrypto.deriveKeyFromPassphrase(passphrase, salt)
            val wrapped = SyncCrypto.wrapDataKey(dek, kek)
            val envelope = JSONObject()
                .put("version", ENVELOPE_VERSION)
                .put("salt", SyncCrypto.encodeBase64(salt))
                .put("wrappedDek", SyncCrypto.encodeBase64(wrapped))
                .put("createdAt", System.currentTimeMillis())
                .toString()
            val saved = DriveRest.upsertAppDataFile(accessToken, ENVELOPE_NAME, envelope) != null
            val folderId = if (saved) DriveRest.ensureFolder(accessToken, FOLDER_NAME) else null
            if (saved && folderId != null) {
                cacheDek(dek)
                settings.setDriveFolderId(folderId)
                settings.setRecoveryConfigured(true)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        } finally {
            passphrase.fill('\u0000')
        }
    }

    /**
     * Restores the DEK on a new device (or after disconnect) by downloading the envelope and
     * unwrapping it with [passphrase]. Returns [RestoreResult.WRONG_PASSPHRASE] when the passphrase
     * doesn't unwrap the key. The [passphrase] array is zeroed before returning.
     */
    suspend fun restore(accessToken: String, passphrase: CharArray): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val id = DriveRest.findAppDataFile(accessToken, ENVELOPE_NAME) ?: return@withContext RestoreResult.ERROR
            val json = DriveRest.downloadText(accessToken, id) ?: return@withContext RestoreResult.ERROR
            val obj = JSONObject(json)
            val salt = SyncCrypto.decodeBase64(obj.getString("salt"))
            val wrapped = SyncCrypto.decodeBase64(obj.getString("wrappedDek"))
            val kek = SyncCrypto.deriveKeyFromPassphrase(passphrase, salt)
            val dek = SyncCrypto.unwrapDataKey(wrapped, kek) ?: return@withContext RestoreResult.WRONG_PASSPHRASE
            val folderId = DriveRest.ensureFolder(accessToken, FOLDER_NAME) ?: return@withContext RestoreResult.ERROR
            cacheDek(dek)
            settings.setDriveFolderId(folderId)
            settings.setRecoveryConfigured(true)
            RestoreResult.SUCCESS
        } catch (e: Exception) {
            RestoreResult.ERROR
        } finally {
            passphrase.fill('\u0000')
        }
    }

    /** The unlocked DEK for this device, or null if this device hasn't been set up. For later slices. */
    suspend fun localDataKey(): ByteArray? {
        val stored = settings.wrappedDataKey() ?: return null
        return EncryptionManager.decryptBytes(SyncCrypto.decodeBase64(stored))
    }

    /**
     * Forgets the locally cached key + folder on disconnect. The Drive envelope is left intact, so
     * reconnecting and re-entering the passphrase restores access.
     */
    suspend fun forgetLocalKeys() {
        settings.setWrappedDataKey(null)
        settings.setDriveFolderId(null)
        settings.setRecoveryConfigured(false)
        settings.setSyncedNoteIds(emptySet())
        settings.setSyncedReminderIds(emptySet())
    }

    private val syncMutex = Mutex()

    /**
     * Two-way, last-write-wins sync of note records between this device and the visible "MyNotes"
     * Drive folder. Uses a persisted "merge base" (the ids present at the last sync) to tell a real
     * deletion apart from a note that simply hasn't reached this device yet. Serialized so a manual
     * "Sync now" and an automatic background sync can never run at the same time.
     */
    suspend fun syncNow(accessToken: String): SyncOutcome = syncMutex.withLock {
        SyncStatus.setSyncing(true)
        try {
            syncInternal(accessToken)
        } finally {
            SyncStatus.setSyncing(false)
        }
    }

    private suspend fun syncInternal(accessToken: String): SyncOutcome = withContext(Dispatchers.IO) {
        try {
            val dek = localDataKey() ?: return@withContext SyncOutcome.NotUnlocked
            val folderId = settings.snapshot().driveFolderId
                ?: DriveRest.ensureFolder(accessToken, FOLDER_NAME)?.also { settings.setDriveFolderId(it) }
                ?: return@withContext SyncOutcome.Error
            val remote = DriveRest.listFolderNotes(accessToken, folderId) ?: return@withContext SyncOutcome.Error
            val remoteById = remote.associateBy { it.noteId }
            val localStamps = noteRepository.idStamps()
            val base = settings.syncedNoteIds()
            val validFolderIds = folderRepository.folderIdsOnce()

            val allIds = HashSet<String>().apply {
                addAll(localStamps.keys); addAll(remoteById.keys); addAll(base)
            }
            val newBase = HashSet<String>()
            var pushed = 0; var pulled = 0; var deletedLocal = 0; var deletedRemote = 0

            for (id in allIds) {
                val local = localStamps[id]
                val rmeta = remoteById[id]
                val inBase = id in base
                when {
                    local != null && rmeta != null -> when {
                        local > rmeta.updatedAt ->
                            if (push(accessToken, folderId, id, rmeta.fileId, dek)) { pushed++; newBase.add(id) }
                        rmeta.updatedAt > local ->
                            if (pull(accessToken, rmeta, dek, validFolderIds)) { pulled++; newBase.add(id) }
                        else -> newBase.add(id)
                    }
                    local != null && rmeta == null ->
                        if (inBase) {
                            noteRepository.deletePermanently(id); deletedLocal++
                        } else if (push(accessToken, folderId, id, null, dek)) {
                            pushed++; newBase.add(id)
                        }
                    local == null && rmeta != null ->
                        if (inBase) {
                            if (DriveRest.deleteFile(accessToken, rmeta.fileId)) deletedRemote++
                        } else if (pull(accessToken, rmeta, dek, validFolderIds)) {
                            pulled++; newBase.add(id)
                        }
                    // else: only in base -> gone from both sides, drop from the base.
                }
            }
            settings.setSyncedNoteIds(newBase)
            syncTemplates(accessToken, dek)
            syncReminders(accessToken, dek)
            settings.setLastSyncedAt(System.currentTimeMillis())
            SyncOutcome.Success(pushed, pulled, deletedLocal, deletedRemote)
        } catch (e: Exception) {
            SyncOutcome.Error
        }
    }

    /** Encrypts and uploads a single local note (create when [existingFileId] is null). */
    private suspend fun push(
        accessToken: String,
        folderId: String,
        id: String,
        existingFileId: String?,
        dek: ByteArray,
    ): Boolean {
        val note = noteRepository.decryptedNoteForSync(id) ?: return false
        val blob = SyncCrypto.encodeBase64(SyncCrypto.encrypt(noteToJson(note).toByteArray(Charsets.UTF_8), dek))
        return DriveRest.putNoteFile(accessToken, folderId, id, "$id.mnote", blob, note.updatedAt, existingFileId) != null
    }

    /** Downloads, decrypts and writes a single remote note into the local database. */
    private suspend fun pull(
        accessToken: String,
        rmeta: RemoteNoteMeta,
        dek: ByteArray,
        validFolderIds: Set<String>,
    ): Boolean {
        val content = DriveRest.downloadText(accessToken, rmeta.fileId) ?: return false
        val decrypted = SyncCrypto.decrypt(SyncCrypto.decodeBase64(content), dek) ?: return false
        val note = noteFromJson(String(decrypted, Charsets.UTF_8)) ?: return false
        noteRepository.importFromSync(note, validFolderIds)
        return true
    }

    /**
     * Backs up and merges the user's custom templates. Templates are a small global list, so the
     * whole set is stored as one encrypted file in Drive's hidden appDataFolder. Merge is
     * last-write-wins per template id (using updatedAt / trashedAt), so creating, editing, trashing
     * or restoring a template on one device propagates to the others. Best-effort: a failure here
     * never fails the note sync.
     */
    private suspend fun syncTemplates(accessToken: String, dek: ByteArray) {
        try {
            val local = settings.allTemplatesForSync()
            val remoteFileId = DriveRest.findAppDataFile(accessToken, TEMPLATES_NAME)
            val remote: List<CustomTemplate> = remoteFileId?.let { fid ->
                DriveRest.downloadText(accessToken, fid)?.let { b64 ->
                    SyncCrypto.decrypt(SyncCrypto.decodeBase64(b64), dek)?.let { bytes ->
                        templatesFromJson(String(bytes, Charsets.UTF_8))
                    }
                }
            } ?: emptyList()

            val merged = mergeTemplates(local, remote)
            if (merged.toSet() != local.toSet()) settings.replaceAllTemplates(merged)
            if (remoteFileId == null || merged.toSet() != remote.toSet()) {
                val blob = SyncCrypto.encodeBase64(
                    SyncCrypto.encrypt(templatesToJson(merged).toByteArray(Charsets.UTF_8), dek),
                )
                DriveRest.upsertAppDataFile(accessToken, TEMPLATES_NAME, blob)
            }
        } catch (e: Exception) {
            // Best-effort - note sync already succeeded.
        }
    }

    private fun templateStamp(t: CustomTemplate): Long = maxOf(t.updatedAt, t.trashedAt ?: 0L)

    private fun mergeTemplates(local: List<CustomTemplate>, remote: List<CustomTemplate>): List<CustomTemplate> {
        val byId = LinkedHashMap<String, CustomTemplate>()
        (local + remote).forEach { t ->
            val existing = byId[t.id]
            if (existing == null || templateStamp(t) >= templateStamp(existing)) byId[t.id] = t
        }
        return byId.values.toList()
    }

    /**
     * Backs up and merges reminders. Like templates, the whole set is one encrypted file in Drive's
     * hidden appDataFolder ([REMINDERS_NAME]); merge is last-write-wins per reminder id via
     * [Reminder.updatedAt], and a persisted merge base ([SettingsRepository.syncedReminderIds])
     * tells a real deletion apart from a reminder that simply hasn't reached this device yet. Pulled
     * reminders are re-armed with AlarmManager so they actually fire here. Best-effort: a failure
     * here never fails the note sync.
     */
    private suspend fun syncReminders(accessToken: String, dek: ByteArray) {
        try {
            val local = reminderRepository.allForSync()
            val localById = local.associateBy { it.id }
            val remoteFileId = DriveRest.findAppDataFile(accessToken, REMINDERS_NAME)
            val remote: List<Reminder> = remoteFileId?.let { fid ->
                DriveRest.downloadText(accessToken, fid)?.let { b64 ->
                    SyncCrypto.decrypt(SyncCrypto.decodeBase64(b64), dek)?.let { bytes ->
                        remindersFromJson(String(bytes, Charsets.UTF_8))
                    }
                }
            } ?: emptyList()
            val remoteById = remote.associateBy { it.id }
            val base = settings.syncedReminderIds()

            val allIds = HashSet<String>().apply {
                addAll(localById.keys); addAll(remoteById.keys); addAll(base)
            }
            val merged = ArrayList<Reminder>()
            val deleteLocal = ArrayList<String>()
            val newBase = HashSet<String>()
            for (id in allIds) {
                val l = localById[id]
                val r = remoteById[id]
                val inBase = id in base
                when {
                    l != null && r != null -> {
                        merged.add(if (l.updatedAt >= r.updatedAt) l else r); newBase.add(id)
                    }
                    l != null && r == null ->
                        if (inBase) deleteLocal.add(id) // deleted on another device
                        else { merged.add(l); newBase.add(id) } // new here -> keep & push
                    l == null && r != null ->
                        if (!inBase) { merged.add(r); newBase.add(id) } // new remotely -> pull
                    // else: only in base -> gone from both sides, drop it.
                }
            }

            // Only touch local rows that actually changed (avoids re-encrypt churn + UI thrash).
            val upserts = merged.filter { localById[it.id] != it }
            if (upserts.isNotEmpty() || deleteLocal.isNotEmpty()) {
                reminderRepository.applySyncedSet(appContext, upserts, deleteLocal)
            }
            settings.setSyncedReminderIds(newBase)

            if (remoteFileId == null || merged.toSet() != remote.toSet()) {
                val blob = SyncCrypto.encodeBase64(
                    SyncCrypto.encrypt(remindersToJson(merged).toByteArray(Charsets.UTF_8), dek),
                )
                DriveRest.upsertAppDataFile(accessToken, REMINDERS_NAME, blob)
            }
        } catch (e: Exception) {
            // Best-effort - note sync already succeeded.
        }
    }

    private fun remindersToJson(items: List<Reminder>): String {
        val arr = JSONArray()
        items.forEach {
            val o = JSONObject()
                .put("id", it.id)
                .put("title", it.title)
                .put("body", it.body)
                .put("noteId", it.noteId ?: JSONObject.NULL)
                .put("triggerAt", it.triggerAt)
                .put("repeat", it.repeat.name)
                .put("enabled", it.enabled)
                .put("createdAt", it.createdAt)
                .put("updatedAt", it.updatedAt)
            arr.put(o)
        }
        return arr.toString()
    }

    private fun remindersFromJson(text: String): List<Reminder> = runCatching {
        val arr = JSONArray(text)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val id = o.optString("id")
            if (id.isBlank()) return@mapNotNull null
            Reminder(
                id = id,
                title = o.optString("title"),
                body = o.optString("body"),
                noteId = if (o.isNull("noteId")) null else o.optString("noteId").ifBlank { null },
                triggerAt = o.optLong("triggerAt"),
                repeat = runCatching { ReminderRepeat.valueOf(o.optString("repeat")) }
                    .getOrDefault(ReminderRepeat.NONE),
                enabled = o.optBoolean("enabled", true),
                createdAt = o.optLong("createdAt"),
                updatedAt = o.optLong("updatedAt", o.optLong("createdAt")),
            )
        }
    }.getOrDefault(emptyList())

    private fun templatesToJson(items: List<CustomTemplate>): String {
        val arr = JSONArray()
        items.forEach {
            val o = JSONObject()
                .put("id", it.id)
                .put("name", it.name)
                .put("icon", it.iconKey)
                .put("content", it.content)
                .put("updatedAt", it.updatedAt)
            if (it.trashedAt != null) o.put("trashedAt", it.trashedAt)
            arr.put(o)
        }
        return arr.toString()
    }

    private fun templatesFromJson(text: String): List<CustomTemplate> = runCatching {
        val arr = JSONArray(text)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CustomTemplate(
                id = o.optString("id"),
                name = o.optString("name"),
                iconKey = o.optString("icon", "note"),
                content = o.optString("content"),
                trashedAt = if (o.has("trashedAt") && !o.isNull("trashedAt")) o.optLong("trashedAt") else null,
                updatedAt = o.optLong("updatedAt", 0L),
            )
        }
    }.getOrDefault(emptyList())

    private fun noteToJson(n: Note): String = JSONObject()
        .put("id", n.id)
        .put("title", n.title)
        .put("content", n.content)
        .put("createdAt", n.createdAt)
        .put("updatedAt", n.updatedAt)
        .put("isPinned", n.isPinned)
        .put("isFavorite", n.isFavorite)
        .put("isArchived", n.isArchived)
        .put("isTrashed", n.isTrashed)
        .put("folderId", n.folderId ?: JSONObject.NULL)
        .put("tags", JSONArray(n.tags))
        .put("colorArgb", n.colorArgb)
        .put("type", n.type.name)
        .put("attachments", JSONArray(n.attachments))
        .toString()

    private fun noteFromJson(text: String): Note? = runCatching {
        val o = JSONObject(text)
        fun strList(key: String): List<String> = o.optJSONArray(key)?.let { arr ->
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        } ?: emptyList()
        Note(
            id = o.getString("id"),
            title = o.optString("title"),
            content = o.optString("content"),
            createdAt = o.optLong("createdAt"),
            updatedAt = o.optLong("updatedAt"),
            isPinned = o.optBoolean("isPinned"),
            isFavorite = o.optBoolean("isFavorite"),
            isArchived = o.optBoolean("isArchived"),
            isTrashed = o.optBoolean("isTrashed"),
            folderId = if (o.isNull("folderId")) null else o.optString("folderId").ifBlank { null },
            tags = strList("tags"),
            colorArgb = o.optInt("colorArgb"),
            type = runCatching { NoteType.valueOf(o.optString("type")) }.getOrDefault(NoteType.TEXT),
            attachments = strList("attachments"),
        )
    }.getOrNull()

    /** Caches the DEK on this device, wrapped by the Android Keystore (never the raw DEK). */
    private suspend fun cacheDek(dek: ByteArray) {
        settings.setWrappedDataKey(SyncCrypto.encodeBase64(EncryptionManager.encryptBytes(dek)))
    }

    companion object {
        private const val ENVELOPE_NAME = "mynotes.key.json"
        private const val ENVELOPE_VERSION = 1
        private const val FOLDER_NAME = "MyNotes"
        private const val TEMPLATES_NAME = "mynotes.templates.json"
        private const val REMINDERS_NAME = "mynotes.reminders.json"
    }
}
