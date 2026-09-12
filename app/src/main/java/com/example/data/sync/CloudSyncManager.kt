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
 * Orchestrates the end-to-end-encrypted cloud sync: recovery credential/key setup and
 * the two-way note sync itself.
 *
 * Key model (see [SyncCrypto] for the crypto details):
 *  - A random **Data Encryption Key (DEK)** is generated per sync setup and reused on every
 *    device. It encrypts every synced note blob (AES-256-GCM).
 *  - The DEK is wrapped with a key derived from the user's recovery passphrase or PIN and the wrapped
 *    copy (the "envelope") is stored in Drive's hidden *appDataFolder* - never the plaintext DEK.
 *  - On this device the unlocked DEK is cached wrapped by the Android Keystore, so we only ask for
 *    the credential once per device (or after the cloud key changes).
 *
 * [syncNow] performs the two-way, last-write-wins sync of note records to/from the visible folder.
 */
class CloudSyncManager(
    private val settings: SettingsRepository,
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository,
    private val reminderRepository: ReminderRepository,
    private val appContext: Context,
    private val remote: DriveSyncRemote = DriveRest,
) {

    enum class RemoteState { NO_ENVELOPE, HAS_ENVELOPE, FOLDER_MISSING, KEY_MISSING, ERROR }
    sealed interface RestoreResult {
        data object SUCCESS : RestoreResult
        data object WRONG_PASSPHRASE : RestoreResult
        data object FOLDER_MISSING : RestoreResult
        data object KEY_MISSING : RestoreResult
        data object ERROR : RestoreResult
        data class Failure(val message: String) : RestoreResult
    }
    sealed interface SetupResult {
        data object SUCCESS : SetupResult
        data object STATE_CHANGED : SetupResult
        data object ERROR : SetupResult
        data class Failure(val message: String) : SetupResult
    }

    data class RecoveryStatus(
        val state: RemoteState,
        val method: DriveRecoveryMethod = DriveRecoveryMethod.PASSPHRASE,
        val canUseLocalKey: Boolean = false,
        val error: String? = null,
    )

    private data class EnvelopeRecord(val fileId: String, val etag: String, val envelope: DriveRecoveryEnvelope)
    private data class VaultState(val email: String, val state: RemoteState, val record: EnvelopeRecord?, val folderId: String?)
    private data class PendingSetup(val email: String, val envelope: DriveRecoveryEnvelope, val wrappedLocalKey: String, val previousIdentity: String?)

    private suspend fun pendingSetup(): PendingSetup? {
        val encrypted = settings.pendingDriveSetup() ?: return null
        val value = JSONObject(requireNotNull(EncryptionManager.decryptOrNull(encrypted)) { "Interrupted setup could not be read" })
        return PendingSetup(value.getString("email"), DriveRecoveryEnvelope.decode(value.getString("envelope")),
            value.getString("localKey"), if (value.isNull("previousIdentity")) null else value.getString("previousIdentity"))
    }

    private suspend fun savePending(setup: PendingSetup) {
        val value = JSONObject().put("email", setup.email).put("envelope", setup.envelope.encode())
            .put("localKey", setup.wrappedLocalKey).put("previousIdentity", setup.previousIdentity ?: JSONObject.NULL)
        settings.setPendingDriveSetup(EncryptionManager.encrypt(value.toString()))
    }

    private suspend fun finishPending(accessToken: String, setup: PendingSetup): Boolean {
        check(settings.snapshot().driveAccountEmail.equals(setup.email, ignoreCase = true)) { "The Google account changed" }
        val published = readEnvelope(accessToken) ?: return false
        if (published.envelope.identity != setup.envelope.identity) return false
        val folderId = requireNotNull(setup.envelope.folderId)
        when (remote.folderStatus(accessToken, folderId)) {
            DriveFolderStatus.UNKNOWN -> return false
            DriveFolderStatus.MISSING -> if (!remote.createFolder(accessToken, FOLDER_NAME, folderId)) return false
            DriveFolderStatus.PRESENT -> Unit
        }
        if (remote.folderStatus(accessToken, folderId) != DriveFolderStatus.PRESENT) return false
        if (readEnvelope(accessToken)?.envelope?.identity != setup.envelope.identity) return false
        settings.activateDriveKey(setup.email, folderId, setup.wrappedLocalKey, setup.envelope.identity)
        SyncStatus.setError(null)
        return true
    }

    private fun readEnvelope(accessToken: String): EnvelopeRecord? = when (val found = remote.lookupAppDataFile(accessToken, ENVELOPE_NAME)) {
        DriveFileLookup.Missing -> null
        DriveFileLookup.Unknown -> error("Could not verify the Drive recovery key")
        is DriveFileLookup.Found -> {
            val download = requireNotNull(remote.downloadNote(accessToken, found.id)) { "Could not read the Drive recovery key" }
            EnvelopeRecord(found.id, download.etag, DriveRecoveryEnvelope.decode(download.content))
        }
    }

    private suspend fun inspect(accessToken: String): VaultState {
        val email = requireNotNull(remote.fetchAccountEmail(accessToken)) { "Could not verify the Google account" }
        val local = settings.snapshot()
        check(local.driveAccountEmail == null || local.driveAccountEmail.equals(email, ignoreCase = true)) { "The connected Google account changed" }
        val record = readEnvelope(accessToken)
        val explicitFolder = record?.envelope?.folderId ?: local.driveFolderId
        var folder = explicitFolder
        var status = explicitFolder?.let { remote.folderStatus(accessToken, it) }
        if (status == DriveFolderStatus.UNKNOWN) error("Could not verify the Drive folder")
        if (explicitFolder == null || (record?.envelope?.folderId == null && status == DriveFolderStatus.MISSING)) {
            when (val found = remote.lookupFolder(accessToken, FOLDER_NAME)) {
                DriveFileLookup.Unknown -> error("Could not locate the Drive folder")
                DriveFileLookup.Missing -> { folder = null; status = DriveFolderStatus.MISSING }
                is DriveFileLookup.Found -> { folder = found.id; status = remote.folderStatus(accessToken, found.id) }
            }
        }
        if (status == DriveFolderStatus.UNKNOWN) error("Could not verify the Drive folder")
        val state = when {
            status == DriveFolderStatus.PRESENT && record != null -> RemoteState.HAS_ENVELOPE
            status == DriveFolderStatus.PRESENT -> RemoteState.KEY_MISSING
            record != null || local.driveFolderId != null || local.recoveryConfigured -> RemoteState.FOLDER_MISSING
            else -> RemoteState.NO_ENVELOPE
        }
        return VaultState(email, state, record, folder?.takeIf { status == DriveFolderStatus.PRESENT })
    }

    suspend fun connect(accessToken: String): RecoveryStatus = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val email = requireNotNull(remote.fetchAccountEmail(accessToken))
                settings.selectDriveAccount(email)
                pendingSetup()?.takeIf { it.email.equals(email, ignoreCase = true) }?.let { setup ->
                    if (readEnvelope(accessToken)?.envelope?.identity == setup.envelope.identity) {
                        check(finishPending(accessToken, setup)) { "Could not finish interrupted Drive setup" }
                    }
                }
                recoveryStatus(inspect(accessToken))
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: DriveRequestException) { RecoveryStatus(RemoteState.ERROR, error = failure.userMessage) }
            catch (_: Exception) { RecoveryStatus(RemoteState.ERROR) }
        }
    }

    private suspend fun recoveryStatus(vault: VaultState): RecoveryStatus {
        val local = settings.snapshot()
        val identity = vault.record?.envelope?.identity
        val matches = vault.state == RemoteState.HAS_ENVELOPE && identity == local.driveKeyIdentity && vault.folderId == local.driveFolderId && hasLocalKey()
        settings.setDriveRecoveryIssue(when (vault.state) {
            RemoteState.FOLDER_MISSING -> "FOLDER_MISSING"
            RemoteState.KEY_MISSING -> "KEY_MISSING"
            RemoteState.HAS_ENVELOPE -> if (matches) null else "KEY_CHANGED"
            else -> null
        })
        return RecoveryStatus(vault.state, vault.record?.envelope?.method ?: DriveRecoveryMethod.PASSPHRASE, matches)
    }

    /** Outcome of a two-way sync. */
    sealed interface SyncOutcome {
        data class Success(
            val pushed: Int,
            val pulled: Int,
            val deletedLocal: Int,
            val deletedRemote: Int,
        ) : SyncOutcome

        /** This device hasn't unlocked the key yet (needs the recovery credential). */
        object NotUnlocked : SyncOutcome
        object FolderMissing : SyncOutcome
        object KeyMissing : SyncOutcome
        object KeyChanged : SyncOutcome
        object Error : SyncOutcome
    }

    /** Whether the connected account already has a recovery envelope on Drive. Network call. */
    suspend fun remoteState(accessToken: String): RemoteState = connect(accessToken).state

    /** True if this device already holds the unlocked DEK (so no passphrase prompt is needed). */
    suspend fun hasLocalKey(): Boolean {
        val key = runCatching { localDataKey() }.getOrNull() ?: return false
        return try { key.size == 32 } finally { key.fill(0) }
    }

    /**
     * First-time setup for the account: generate a DEK, wrap it with [passphrase], store the
     * envelope in appDataFolder, create the visible "MyNotes" folder, and cache the DEK locally.
     * The [passphrase] array is zeroed before returning.
     */
    suspend fun provision(
        accessToken: String,
        passphrase: CharArray,
        method: DriveRecoveryMethod = DriveRecoveryMethod.PASSPHRASE,
        restartConfirmed: Boolean = false,
    ): SetupResult {
        try {
            return syncMutex.withLock {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                    try {
                        require(method.accepts(passphrase)) { "Invalid recovery credential" }
                        val vault = inspect(accessToken)
                        val pending = pendingSetup()?.takeIf { it.email.equals(vault.email, ignoreCase = true) }
                        if (pending != null && vault.record?.envelope?.identity == pending.envelope.identity) {
                            val key = pending.envelope.unlock(passphrase) ?: return@withContext SetupResult.STATE_CHANGED
                            key.fill(0)
                            return@withContext if (finishPending(accessToken, pending)) SetupResult.SUCCESS else SetupResult.ERROR
                        }
                        val allowed = if (restartConfirmed) vault.state == RemoteState.FOLDER_MISSING else vault.state == RemoteState.NO_ENVELOPE
                        if (!allowed) return@withContext SetupResult.STATE_CHANGED
                        settings.selectDriveAccount(vault.email)
                        val previousIdentity = vault.record?.envelope?.identity
                        val key = SyncCrypto.newDataKey()
                        val setup = try {
                            val folderId = requireNotNull(remote.generateFileId(accessToken)) { "Could not reserve a Drive folder" }
                            PendingSetup(vault.email, DriveRecoveryEnvelope.create(passphrase, method, key, folderId),
                                SyncCrypto.encodeBase64(EncryptionManager.encryptBytes(key)), previousIdentity)
                        } finally { key.fill(0) }
                        savePending(setup)
                        vault.record?.let { previous ->
                            check(remote.createAppDataFile(accessToken, "mynotes.key.retired.${java.util.UUID.randomUUID()}.json", previous.envelope.encode()) != null) {
                                "Could not preserve the previous recovery key"
                            }
                        }
                        val latest = inspect(accessToken)
                        if (latest.state != vault.state || latest.record?.fileId != vault.record?.fileId ||
                            latest.record?.etag != vault.record?.etag || latest.record?.envelope?.identity != previousIdentity) {
                            return@withContext SetupResult.STATE_CHANGED
                        }
                        if (vault.record != null) {
                            remote.updateCollection(accessToken, vault.record.fileId, setup.envelope.encode(), vault.record.etag)
                        } else {
                            if (remote.lookupAppDataFile(accessToken, ENVELOPE_NAME) != DriveFileLookup.Missing) return@withContext SetupResult.STATE_CHANGED
                            remote.createAppDataFile(accessToken, ENVELOPE_NAME, setup.envelope.encode())
                        }
                        if (readEnvelope(accessToken)?.envelope?.identity != setup.envelope.identity) return@withContext SetupResult.STATE_CHANGED
                        if (finishPending(accessToken, setup)) SetupResult.SUCCESS else SetupResult.ERROR
                    } catch (failure: DriveRequestException) { SetupResult.Failure(failure.userMessage) }
                    catch (_: Exception) { SetupResult.ERROR }
                }
            }
        } finally { passphrase.fill('\u0000') }
    }

    /**
     * Restores the DEK on a new device (or after disconnect) by downloading the envelope and
     * unwrapping it with [passphrase]. Returns [RestoreResult.WRONG_PASSPHRASE] when the passphrase
     * doesn't unwrap the key. The [passphrase] array is zeroed before returning.
     */
    suspend fun restore(accessToken: String, passphrase: CharArray): RestoreResult {
        try {
            return syncMutex.withLock {
                withContext(Dispatchers.IO) {
                    try {
                        require(passphrase.size in 1..1024)
                        val vault = inspect(accessToken)
                        if (vault.state == RemoteState.FOLDER_MISSING) return@withContext RestoreResult.FOLDER_MISSING
                        if (vault.state == RemoteState.KEY_MISSING || vault.state == RemoteState.NO_ENVELOPE) return@withContext RestoreResult.KEY_MISSING
                        val record = requireNotNull(vault.record)
                        val folderId = requireNotNull(vault.folderId)
                        val key = record.envelope.unlock(passphrase) ?: return@withContext RestoreResult.WRONG_PASSPHRASE
                        try {
                            val latest = readEnvelope(accessToken) ?: return@withContext RestoreResult.ERROR
                            if (latest.envelope.identity != record.envelope.identity) return@withContext RestoreResult.ERROR
                            when (remote.folderStatus(accessToken, folderId)) {
                                DriveFolderStatus.MISSING -> return@withContext RestoreResult.FOLDER_MISSING
                                DriveFolderStatus.UNKNOWN -> return@withContext RestoreResult.ERROR
                                DriveFolderStatus.PRESENT -> Unit
                            }
                            settings.selectDriveAccount(vault.email)
                            settings.activateDriveKey(vault.email, folderId, SyncCrypto.encodeBase64(EncryptionManager.encryptBytes(key)), record.envelope.identity)
                            SyncStatus.setError(null)
                            RestoreResult.SUCCESS
                        } finally { key.fill(0) }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (failure: DriveRequestException) { RestoreResult.Failure(failure.userMessage) }
                    catch (_: Exception) { RestoreResult.ERROR }
                }
            }
        } finally { passphrase.fill('\u0000') }
    }

    /** The unlocked DEK for this device, or null if this device hasn't been set up. */
    suspend fun localDataKey(): ByteArray? {
        val stored = settings.wrappedDataKey() ?: return null
        return EncryptionManager.decryptBytes(SyncCrypto.decodeBase64(stored))
    }

    /**
     * Forgets the locally cached key + folder on disconnect. The Drive envelope is left intact, so
     * reconnecting and re-entering the passphrase restores access.
     */
    suspend fun forgetLocalKeys() = syncMutex.withLock {
        settings.clearDriveKey()
        SyncStatus.setError(null)
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
            syncInternal(accessToken).also { outcome ->
                SyncStatus.setError(when (outcome) {
                    is SyncOutcome.Success -> null
                    SyncOutcome.NotUnlocked -> "Enter your recovery passphrase or PIN to sync."
                    SyncOutcome.FolderMissing -> "The MyNotes folder is missing from Drive. Open Settings to reconnect or start over. Your local notes are unchanged."
                    SyncOutcome.KeyMissing -> "The Drive recovery key is missing. Open Settings to review recovery. Your local notes are unchanged."
                    SyncOutcome.KeyChanged -> "Drive sync was set up again or needs to be unlocked. Open Settings and enter its current recovery credential."
                    SyncOutcome.Error -> "Sync is incomplete. Local notes are saved; reconnect or retry sync."
                })
                when (outcome) {
                    SyncOutcome.FolderMissing -> settings.setDriveRecoveryIssue("FOLDER_MISSING")
                    SyncOutcome.KeyMissing -> settings.setDriveRecoveryIssue("KEY_MISSING")
                    SyncOutcome.KeyChanged, SyncOutcome.NotUnlocked -> settings.setDriveRecoveryIssue("KEY_CHANGED")
                    is SyncOutcome.Success -> settings.setDriveRecoveryIssue(null)
                    SyncOutcome.Error -> Unit
                }
            }
        } finally {
            SyncStatus.setSyncing(false)
        }
    }

    private suspend fun syncInternal(accessToken: String): SyncOutcome = withContext(Dispatchers.IO) {
        var dataKey: ByteArray? = null
        try {
            val vault = inspect(accessToken)
            when (vault.state) {
                RemoteState.FOLDER_MISSING -> return@withContext SyncOutcome.FolderMissing
                RemoteState.KEY_MISSING, RemoteState.NO_ENVELOPE -> return@withContext SyncOutcome.KeyMissing
                RemoteState.ERROR -> return@withContext SyncOutcome.Error
                RemoteState.HAS_ENVELOPE -> Unit
            }
            val envelope = requireNotNull(vault.record).envelope
            val local = settings.snapshot()
            if (local.driveKeyIdentity != envelope.identity || local.driveFolderId != vault.folderId) return@withContext SyncOutcome.KeyChanged
            dataKey = localDataKey()
            val dek = dataKey ?: return@withContext SyncOutcome.NotUnlocked
            val folderId = requireNotNull(vault.folderId)
            val remoteNotes = remote.listFolderNotes(accessToken, folderId) ?: return@withContext SyncOutcome.Error
            when (remote.folderStatus(accessToken, folderId)) {
                DriveFolderStatus.MISSING -> return@withContext SyncOutcome.FolderMissing
                DriveFolderStatus.UNKNOWN -> return@withContext SyncOutcome.Error
                DriveFolderStatus.PRESENT -> Unit
            }
            val latestEnvelope = readEnvelope(accessToken)?.envelope ?: return@withContext SyncOutcome.KeyMissing
            if (latestEnvelope.identity != envelope.identity || latestEnvelope.folderId != envelope.folderId) return@withContext SyncOutcome.KeyChanged
            val remoteById = remoteNotes.associateBy { it.noteId }
            val localStamps = noteRepository.idStamps()
            val base = settings.syncedNoteVersions()
            val validFolderIds = folderRepository.folderIdsOnce()

            val allIds = HashSet<String>().apply {
                addAll(localStamps.keys); addAll(remoteById.keys); addAll(base.keys)
            }
            val newBase = base.toMutableMap()
            var pushed = 0; var pulled = 0; var deletedLocal = 0; var deletedRemote = 0
            var failed = false

            for (id in allIds) {
                val local = localStamps[id]
                val rmeta = remoteById[id]
                try {
                    when (SyncMergePolicy.decide(local, rmeta?.updatedAt, base[id])) {
                        MergeAction.UNCHANGED -> if (local != null) newBase[id] = local else newBase.remove(id)
                        MergeAction.PUSH, MergeAction.KEEP_BOTH -> {
                            val download = rmeta?.let { requireNotNull(remote.downloadNote(accessToken, it.fileId)) }
                            val remoteNote = download?.let { downloaded ->
                                val decoded = SyncCrypto.decrypt(SyncCrypto.decodeBase64(downloaded.content), dek)
                                val note = requireNotNull(decoded?.let { noteFromJson(String(it, Charsets.UTF_8)) })
                                check(note.id == id && note.updatedAt == rmeta.updatedAt) { "Remote note changed; retry sync" }
                                note
                            }
                            if (remoteNote != null && SyncMergePolicy.decide(local, remoteNote.updatedAt, base[id]) == MergeAction.KEEP_BOTH) {
                                noteRepository.duplicate(remoteNote.copy(folderId = remoteNote.folderId?.takeIf { it in validFolderIds }),
                                    "${remoteNote.title.ifBlank { "Untitled" }} (Conflict copy)")
                            }
                            val stamp = requireNotNull(push(accessToken, folderId, id, rmeta?.fileId, dek, download?.etag))
                            pushed++
                            newBase[id] = stamp
                        }
                        MergeAction.PULL -> {
                            check(pull(accessToken, requireNotNull(rmeta), dek, validFolderIds, local))
                            pulled++
                            newBase[id] = rmeta.updatedAt
                        }
                        MergeAction.TRASH_LOCAL -> {
                            check(noteRepository.idStamps()[id] == local)
                            noteRepository.setTrashed(id, true)
                            deletedLocal++
                            newBase.remove(id)
                        }
                        MergeAction.DELETE_REMOTE -> {
                            val metadata = requireNotNull(rmeta)
                            val download = requireNotNull(remote.downloadNote(accessToken, metadata.fileId))
                            val decoded = requireNotNull(SyncCrypto.decrypt(SyncCrypto.decodeBase64(download.content), dek))
                            val note = requireNotNull(noteFromJson(String(decoded, Charsets.UTF_8)))
                            check(note.id == id && note.updatedAt == metadata.updatedAt)
                            check(remote.deleteFile(accessToken, metadata.fileId, download.etag))
                            deletedRemote++
                            newBase.remove(id)
                        }
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) { failed = true }
            }
            settings.setSyncedNoteVersions(newBase)
            if (failed) return@withContext SyncOutcome.Error
            if (!syncTemplates(accessToken, dek, envelope.templatesName) || !syncReminders(accessToken, dek, envelope.remindersName)) return@withContext SyncOutcome.Error
            settings.setLastSyncedAt(System.currentTimeMillis())
            SyncOutcome.Success(pushed, pulled, deletedLocal, deletedRemote)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            SyncOutcome.Error
        } finally { dataKey?.fill(0) }
    }

    /** Encrypts and uploads a single local note (create when [existingFileId] is null). */
    private suspend fun push(
        accessToken: String,
        folderId: String,
        id: String,
        existingFileId: String?,
        dek: ByteArray,
        etag: String? = null,
    ): Long? {
        val note = noteRepository.decryptedNoteForSync(id) ?: return null
        val blob = SyncCrypto.encodeBase64(SyncCrypto.encrypt(noteToJson(note).toByteArray(Charsets.UTF_8), dek))
        return if (remote.putNoteFile(accessToken, folderId, id, "$id.mnote", blob, note.updatedAt, existingFileId, etag) != null) note.updatedAt else null
    }

    /** Downloads, decrypts and writes a single remote note into the local database. */
    private suspend fun pull(
        accessToken: String,
        rmeta: RemoteNoteMeta,
        dek: ByteArray,
        validFolderIds: Set<String>,
        expectedLocalStamp: Long?,
    ): Boolean {
        val content = remote.downloadNote(accessToken, rmeta.fileId)?.content ?: return false
        val decrypted = SyncCrypto.decrypt(SyncCrypto.decodeBase64(content), dek) ?: return false
        val note = noteFromJson(String(decrypted, Charsets.UTF_8)) ?: return false
        if (note.id != rmeta.noteId || note.updatedAt != rmeta.updatedAt) return false
        noteRepository.importFromSync(note, validFolderIds, expectedLocalStamp)
        return true
    }

    /**
     * Backs up and merges the user's custom templates. Templates are a small global list, so the
     * whole set is stored as one encrypted file in Drive's hidden appDataFolder. Merge is
     * last-write-wins per template id (using updatedAt / trashedAt), so creating, editing, trashing
    * or restoring a template on one device propagates to the others. Failed metadata sync leaves
    * the overall sync incomplete without advancing the successful-sync timestamp.
     */
    private suspend fun syncTemplates(accessToken: String, dek: ByteArray, name: String): Boolean {
        return try {
            val local = settings.allTemplatesForSync()
            val remoteFileId = verifiedCollectionId(accessToken, name)
            val download = remoteFileId?.let { requireNotNull(remote.downloadNote(accessToken, it)) }
            val remote = download?.let { templatesFromJson(decryptCollection(it.content, dek)) } ?: emptyList()

            val merged = mergeTemplates(local, remote)
            if (remoteFileId == null || merged.toSet() != remote.toSet()) {
                val blob = SyncCrypto.encodeBase64(
                    SyncCrypto.encrypt(templatesToJson(merged).toByteArray(Charsets.UTF_8), dek),
                )
                check(putCollection(accessToken, name, remoteFileId, download?.etag, blob))
            }
            if (merged.toSet() != local.toSet()) settings.mergeSyncedTemplates(merged)
            true
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { false }
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
    * hidden appDataFolder for the active recovery generation; merge is last-write-wins per reminder id via
     * [Reminder.updatedAt], and a persisted merge base ([SettingsRepository.syncedReminderIds])
     * tells a real deletion apart from a reminder that simply hasn't reached this device yet. Pulled
    * reminders are re-armed with AlarmManager so they actually fire here. A failure leaves the
    * overall sync incomplete without advancing the successful-sync timestamp.
     */
    private suspend fun syncReminders(accessToken: String, dek: ByteArray, name: String): Boolean {
        return try {
            val local = reminderRepository.allForSync()
            val localById = local.associateBy { it.id }
            val remoteFileId = verifiedCollectionId(accessToken, name)
            val download = remoteFileId?.let { requireNotNull(remote.downloadNote(accessToken, it)) }
            val remote = download?.let { remindersFromJson(decryptCollection(it.content, dek)) } ?: emptyList()
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
            if (remoteFileId == null || merged.toSet() != remote.toSet()) {
                val blob = SyncCrypto.encodeBase64(
                    SyncCrypto.encrypt(remindersToJson(merged).toByteArray(Charsets.UTF_8), dek),
                )
                check(putCollection(accessToken, name, remoteFileId, download?.etag, blob))
            }
            if (upserts.isNotEmpty() || deleteLocal.isNotEmpty()) {
                reminderRepository.applySyncedSet(appContext, upserts, deleteLocal, localById.mapValues { it.value.updatedAt })
            }
            settings.setSyncedReminderIds(newBase)
            true
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }

    private fun verifiedCollectionId(accessToken: String, name: String): String? = when (val found = remote.lookupAppDataFile(accessToken, name)) {
        is DriveFileLookup.Found -> found.id
        DriveFileLookup.Missing -> null
        DriveFileLookup.Unknown -> error("Could not verify sync metadata")
    }

    private fun decryptCollection(content: String, key: ByteArray): String = String(
        requireNotNull(SyncCrypto.decrypt(SyncCrypto.decodeBase64(content), key)) { "Sync metadata is unavailable or damaged" }, Charsets.UTF_8,
    )

    private fun putCollection(accessToken: String, name: String, fileId: String?, etag: String?, content: String): Boolean =
        if (fileId == null) remote.createAppDataFile(accessToken, name, content) != null
        else remote.updateCollection(accessToken, fileId, content, requireNotNull(etag))

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
                .put("completedAt", it.completedAt ?: JSONObject.NULL)
                .put("lastNotifiedAt", it.lastNotifiedAt ?: JSONObject.NULL)
                .put("snoozedUntil", it.snoozedUntil ?: JSONObject.NULL)
                .put("repeatAnchorAt", it.repeatAnchorAt)
                .put("checklistText", it.checklistText ?: JSONObject.NULL)
            arr.put(o)
        }
        return arr.toString()
    }

    private fun remindersFromJson(text: String): List<Reminder> {
        val arr = JSONArray(text)
        require(arr.length() <= 20_000) { "Too many synced reminders" }
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val id = o.optString("id")
            require(id.isNotBlank()) { "Invalid synced reminder" }
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
                completedAt = if (o.isNull("completedAt")) null else o.getLong("completedAt"),
                lastNotifiedAt = if (o.isNull("lastNotifiedAt")) null else o.getLong("lastNotifiedAt"),
                snoozedUntil = if (o.isNull("snoozedUntil")) null else o.getLong("snoozedUntil"),
                repeatAnchorAt = o.optLong("repeatAnchorAt", o.optLong("triggerAt")),
                checklistText = if (o.isNull("checklistText")) null else o.getString("checklistText"),
            )
        }
    }

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

    private fun templatesFromJson(text: String): List<CustomTemplate> {
        val arr = JSONArray(text)
        require(arr.length() <= 20_000) { "Too many synced templates" }
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            require(o.optString("id").isNotBlank()) { "Invalid synced template" }
            CustomTemplate(
                id = o.optString("id"),
                name = o.optString("name"),
                iconKey = o.optString("icon", "note"),
                content = o.optString("content"),
                trashedAt = if (o.has("trashedAt") && !o.isNull("trashedAt")) o.optLong("trashedAt") else null,
                updatedAt = o.optLong("updatedAt", 0L),
            )
        }
    }

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

    companion object {
        private const val ENVELOPE_NAME = "mynotes.key.json"
        private const val FOLDER_NAME = "MyNotes"
    }
}
