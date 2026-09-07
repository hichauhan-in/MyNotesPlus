package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.data.attachments.AttachmentStore
import com.example.data.local.AppDatabase
import com.example.data.local.NoteDao
import com.example.data.local.NoteEntity
import com.example.data.local.NoteVersionEntity
import com.example.data.security.EncryptionManager
import com.example.domain.model.Note
import com.example.domain.model.NoteType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Single source of truth for notes. Encryption / decryption happens here so the rest
 * of the app only ever deals with plaintext [Note] models held in memory.
 */
class NoteRepository(
    private val noteDao: NoteDao,
    private val appContext: Context,
    private val database: AppDatabase,
) {
    fun versions(noteId: String): Flow<List<NoteVersionEntity>> = noteDao.versions(noteId)

    suspend fun versionContent(noteId: String, id: String): Triple<String, String, List<String>>? = withContext(Dispatchers.IO) {
        noteDao.getVersion(id)?.let { version ->
            if (version.noteId != noteId) return@withContext null
            val title = EncryptionManager.decryptOrNull(version.encryptedTitle) ?: return@withContext null
            val content = EncryptionManager.decryptOrNull(version.encryptedContent) ?: return@withContext null
            Triple(title, content, version.attachments.split(",").filter(String::isNotBlank))
        }
    }

    suspend fun duplicate(note: Note, title: String = "${note.title.ifBlank { "Untitled" }} (Copy)"): String = withContext(Dispatchers.IO) {
        val written = mutableListOf<String>()
        try {
            val names = (note.attachments + com.example.domain.model.AttachmentMarkup.fileNames(note.content)).distinct()
            val replacements = names.associateWith { name -> com.example.data.share.ShareImportPolicy.freshName(name) }
            val content = com.example.data.share.ShareImportPolicy.renameContent(note.content, note.type == NoteType.SCRIBBLE, replacements)
            names.forEach { original ->
                val name = replacements.getValue(original)
                val bytes = requireNotNull(AttachmentStore.readDecrypted(appContext, original)) { "An attachment could not be recovered" }
                written.add(name)
                check(AttachmentStore.writeEncrypted(appContext, name, bytes)) { "An attachment could not be saved" }
            }
            val now = System.currentTimeMillis()
            val copy = note.copy(id = UUID.randomUUID().toString(), title = title, content = content, attachments = replacements.values.toList(),
                isTrashed = false, isArchived = false, isPinned = false, createdAt = now, updatedAt = now)
            saveNote(copy)
            copy.id
        } catch (failure: Exception) {
            written.forEach { AttachmentStore.delete(appContext, it) }
            throw failure
        }
    }

    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()
        .map { entities -> entities.map { it.toNote() } }
        // Decryption is CPU work; keep it off the main thread even though this flow is
        // collected in viewModelScope (which defaults to the main dispatcher).
        .flowOn(Dispatchers.Default)

    /** Emits whenever the notes table changes - a trigger for cloud sync. */
    fun changeSignal(): Flow<Int> = noteDao.changeSignal()

    suspend fun getNoteById(id: String): Note? = withContext(Dispatchers.IO) {
        noteDao.getNoteById(id)?.toNote()
    }
    suspend fun saveNote(note: Note, expectedUpdatedAt: Long? = null): Unit = withContext(Dispatchers.IO) {
        database.withTransaction {
        val existing = noteDao.getNoteById(note.id)
        check(expectedUpdatedAt == null || existing?.updatedAt == expectedUpdatedAt) { "This note changed. Please retry." }
        // Safety net: never overwrite a note whose stored content can't be decrypted right now.
        // The original encrypted bytes may still be recoverable once the key is available again,
        // so we refuse the save rather than replacing them with freshly-encrypted placeholder text.
        check(existing == null || !existing.isLocked()) { "This note cannot be decrypted. Its saved content has been preserved." }
        val entity = NoteEntity(
            id = note.id.ifBlank { UUID.randomUUID().toString() },
            encryptedTitle = EncryptionManager.encrypt(note.title),
            encryptedContent = EncryptionManager.encrypt(note.content),
            createdAt = existing?.createdAt ?: note.createdAt,
            updatedAt = System.currentTimeMillis(),
            isPinned = note.isPinned,
            isFavorite = note.isFavorite,
            // Archive / trash state is managed by dedicated actions, never the editor,
            // so preserve whatever is already persisted for an existing note.
            isArchived = existing?.isArchived ?: note.isArchived,
            isTrashed = existing?.isTrashed ?: note.isTrashed,
            folderId = note.folderId,
            tags = note.tags.joinToString(","),
            colorArgb = note.colorArgb,
            type = note.type.name,
            attachments = note.attachments.joinToString(","),
        )
        if (existing != null && (EncryptionManager.decryptOrNull(existing.encryptedTitle) != note.title ||
                EncryptionManager.decryptOrNull(existing.encryptedContent) != note.content || existing.attachments != entity.attachments)) {
            noteDao.insertVersion(NoteVersionEntity.from(existing))
        }
        noteDao.insertNote(entity)
        noteDao.trimVersions(entity.id)
        }
    }

    suspend fun setPinned(id: String, value: Boolean) = withContext(Dispatchers.IO) {
        noteDao.setPinned(id, value, System.currentTimeMillis())
    }

    suspend fun setFavorite(id: String, value: Boolean) = withContext(Dispatchers.IO) {
        noteDao.setFavorite(id, value, System.currentTimeMillis())
    }

    suspend fun setArchived(id: String, value: Boolean) = withContext(Dispatchers.IO) {
        noteDao.setArchived(id, value, System.currentTimeMillis())
    }

    suspend fun setTrashed(id: String, value: Boolean) = withContext(Dispatchers.IO) {
        noteDao.setTrashed(id, value, System.currentTimeMillis())
    }

    suspend fun setColor(id: String, colorArgb: Int) = withContext(Dispatchers.IO) {
        noteDao.setColor(id, colorArgb, System.currentTimeMillis())
    }

    suspend fun deletePermanently(id: String) = withContext(Dispatchers.IO) {
        val attachments = database.withTransaction {
            val files = noteDao.versionAttachments(id) + listOfNotNull(noteDao.getNoteById(id)?.attachments)
            noteDao.deleteNoteById(id)
            files
        }
        deleteAttachmentFiles(attachments)
    }

    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        val attachments = database.withTransaction {
            val files = noteDao.trashedVersionAttachments(Long.MAX_VALUE) + noteDao.getTrashedAttachments()
            noteDao.emptyTrash()
            files
        }
        deleteAttachmentFiles(attachments)
    }

    /** Permanently deletes trashed notes last touched before [retentionDays] ago. No-op if 0. */
    suspend fun purgeTrashOlderThan(retentionDays: Int) = withContext(Dispatchers.IO) {
        if (retentionDays <= 0) return@withContext
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        val attachments = database.withTransaction {
            val files = noteDao.trashedVersionAttachments(cutoff) + noteDao.getPurgeableAttachments(cutoff)
            noteDao.purgeTrashedBefore(cutoff)
            files
        }
        deleteAttachmentFiles(attachments)
    }

    /** Removes the on-disk image files backing a note (comma-separated file names). */
    private suspend fun deleteAttachmentFiles(attachments: List<String>) {
        com.example.data.attachments.AttachmentMaintenance(appContext, database)
            .removeUnused(attachments.flatMap { it.split(",").filter(String::isNotBlank) })
    }

    // ---- Cloud sync helpers -----------------------------------------------------

    /** Every note's id mapped to its last-modified time - cheap (no decryption) for merge diffing. */
    suspend fun idStamps(): Map<String, Long> = withContext(Dispatchers.IO) {
        noteDao.getAllStamps().associate { it.id to it.updatedAt }
    }

    /** Count of live notes (excludes Trash + Archive). Used by the home-screen widget. */
    suspend fun activeNoteCount(): Int = withContext(Dispatchers.IO) {
        noteDao.getActiveNoteCount()
    }

    /**
     * The decrypted note for upload, or null if it is missing or currently "locked" (its stored
     * ciphertext can't be decrypted) - we never upload a placeholder over real data.
     */
    suspend fun decryptedNoteForSync(id: String): Note? = withContext(Dispatchers.IO) {
        val entity = noteDao.getNoteById(id) ?: return@withContext null
        if (entity.isLocked()) null else entity.toNote()
    }

    /**
     * Writes a note pulled from the cloud, preserving its remote timestamps and full state
     * (archive/trash/etc.). The [validFolderIds] guard drops a dangling book reference so the note
     * stays visible even when its book hasn't synced to this device. Re-encrypts with this device's
     * key, so it is safe even when other local notes are currently locked.
     */
    suspend fun importFromSync(note: Note, validFolderIds: Set<String>, expectedLocalStamp: Long?): Unit = withContext(Dispatchers.IO) {
        database.withTransaction {
        check(noteDao.getNoteById(note.id)?.updatedAt == expectedLocalStamp) { "The note changed while syncing; retry sync" }
        noteDao.getNoteById(note.id)?.let { existing ->
            if (existing.updatedAt != note.updatedAt) noteDao.insertVersion(NoteVersionEntity.from(existing))
        }
        val entity = NoteEntity(
            id = note.id,
            encryptedTitle = EncryptionManager.encrypt(note.title),
            encryptedContent = EncryptionManager.encrypt(note.content),
            createdAt = note.createdAt,
            updatedAt = note.updatedAt,
            isPinned = note.isPinned,
            isFavorite = note.isFavorite,
            isArchived = note.isArchived,
            isTrashed = note.isTrashed,
            folderId = note.folderId?.takeIf { it in validFolderIds },
            tags = note.tags.joinToString(","),
            colorArgb = note.colorArgb,
            type = note.type.name,
            attachments = note.attachments.joinToString(","),
        )
        noteDao.insertNote(entity)
        noteDao.trimVersions(note.id)
        }
    }

    private fun NoteEntity.toNote(): Note {
        val decTitle = EncryptionManager.decryptOrNull(encryptedTitle)
        val decContent = EncryptionManager.decryptOrNull(encryptedContent)
        val locked = (encryptedTitle.isNotEmpty() && decTitle == null) ||
            (encryptedContent.isNotEmpty() && decContent == null)
        return Note(
            id = id,
            title = if (locked) "🔒 Locked note" else (decTitle ?: ""),
            content = if (locked) "" else (decContent ?: ""),
            createdAt = createdAt,
            updatedAt = updatedAt,
            isPinned = isPinned,
            isFavorite = isFavorite,
            isArchived = isArchived,
            isTrashed = isTrashed,
            folderId = folderId,
            tags = tags.split(",").filter { it.isNotBlank() },
            colorArgb = colorArgb,
            type = runCatching { NoteType.valueOf(type) }.getOrDefault(NoteType.TEXT),
            attachments = attachments.split(",").filter { it.isNotBlank() },
        )
    }

    /** True if the note has stored content/title that cannot be decrypted with the current key. */
    private fun NoteEntity.isLocked(): Boolean =
        (encryptedTitle.isNotEmpty() && EncryptionManager.decryptOrNull(encryptedTitle) == null) ||
            (encryptedContent.isNotEmpty() && EncryptionManager.decryptOrNull(encryptedContent) == null)
}
