package com.example.data.repository

import android.content.Context
import com.example.data.attachments.AttachmentStore
import com.example.data.local.NoteDao
import com.example.data.local.NoteEntity
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
) {

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
    suspend fun saveNote(note: Note): Unit = withContext(Dispatchers.IO) {
        val existing = noteDao.getNoteById(note.id)
        // Safety net: never overwrite a note whose stored content can't be decrypted right now.
        // The original encrypted bytes may still be recoverable once the key is available again,
        // so we refuse the save rather than replacing them with freshly-encrypted placeholder text.
        if (existing != null && existing.isLocked()) return@withContext
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
        noteDao.insertNote(entity)
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
        noteDao.getNoteById(id)?.let { deleteAttachmentFiles(it.attachments) }
        noteDao.deleteNoteById(id)
    }

    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        noteDao.getTrashedAttachments().forEach { deleteAttachmentFiles(it) }
        noteDao.emptyTrash()
    }

    /** Permanently deletes trashed notes last touched before [retentionDays] ago. No-op if 0. */
    suspend fun purgeTrashOlderThan(retentionDays: Int) = withContext(Dispatchers.IO) {
        if (retentionDays <= 0) return@withContext
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        noteDao.getPurgeableAttachments(cutoff).forEach { deleteAttachmentFiles(it) }
        noteDao.purgeTrashedBefore(cutoff)
    }

    /** Removes the on-disk image files backing a note (comma-separated file names). */
    private fun deleteAttachmentFiles(attachments: String) {
        attachments.split(",")
            .filter { it.isNotBlank() }
            .forEach { AttachmentStore.delete(appContext, it) }
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
    suspend fun importFromSync(note: Note, validFolderIds: Set<String>): Unit = withContext(Dispatchers.IO) {
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
