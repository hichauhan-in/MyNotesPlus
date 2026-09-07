package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Minimal projection (id + last-modified) used by cloud sync to compare without decrypting. */
data class NoteStamp(val id: String, val updatedAt: Long)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    /** Emits on every write to the notes table - a lightweight "something changed" signal. */
    @Query("SELECT COUNT(*) FROM notes")
    fun changeSignal(): Flow<Int>

    @Query("SELECT id, updatedAt FROM notes")
    suspend fun getAllStamps(): List<NoteStamp>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Query("UPDATE notes SET isPinned = :value, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: String, value: Boolean, updatedAt: Long)

    @Query("UPDATE notes SET isFavorite = :value, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: String, value: Boolean, updatedAt: Long)

    @Query("UPDATE notes SET isArchived = :value, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: String, value: Boolean, updatedAt: Long)

    @Query("UPDATE notes SET isTrashed = :value, isPinned = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setTrashed(id: String, value: Boolean, updatedAt: Long)

    @Query("UPDATE notes SET colorArgb = :colorArgb, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setColor(id: String, colorArgb: Int, updatedAt: Long)

    @Query("UPDATE notes SET folderId = :folderId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFolder(id: String, folderId: String?, updatedAt: Long)

    @Query("UPDATE notes SET folderId = :newFolderId WHERE folderId = :oldFolderId")
    suspend fun reparentNotes(oldFolderId: String, newFolderId: String?)

    /** Trash every note that lives in any of the given books (used when a book is deleted). */
    @Query(
        "UPDATE notes SET isTrashed = 1, isPinned = 0, folderId = NULL, updatedAt = :updatedAt " +
            "WHERE folderId IN (:folderIds)",
    )
    suspend fun trashNotesInFolders(folderIds: List<String>, updatedAt: Long)

    /** Trash notes in the given books but KEEP their folderId, so Trash can show the hierarchy. */
    @Query(
        "UPDATE notes SET isTrashed = 1, isPinned = 0, updatedAt = :updatedAt " +
            "WHERE folderId IN (:folderIds)",
    )
    suspend fun trashNotesInFoldersKeepFolder(folderIds: List<String>, updatedAt: Long)

    /** Restore notes that were trashed together with their book. */
    @Query("UPDATE notes SET isTrashed = 0, updatedAt = :updatedAt WHERE folderId IN (:folderIds)")
    suspend fun restoreNotesInFolders(folderIds: List<String>, updatedAt: Long)

    @Query("SELECT attachments FROM notes WHERE folderId IN (:folderIds)")
    suspend fun getAttachmentsInFolders(folderIds: List<String>): List<String>

    /** Count of live notes (excludes Trash and Archive) - used by the home-screen widget. */
    @Query("SELECT COUNT(*) FROM notes WHERE isTrashed = 0 AND isArchived = 0")
    suspend fun getActiveNoteCount(): Int

    @Query("DELETE FROM notes WHERE folderId IN (:folderIds)")
    suspend fun deleteNotesInFolders(folderIds: List<String>)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: String)

    @Query("DELETE FROM notes WHERE isTrashed = 1")
    suspend fun emptyTrash()

    @Query("DELETE FROM notes WHERE isTrashed = 1 AND updatedAt < :cutoff")
    suspend fun purgeTrashedBefore(cutoff: Long)

    @Query("SELECT attachments FROM notes WHERE isTrashed = 1")
    suspend fun getTrashedAttachments(): List<String>

    @Query("SELECT attachments FROM notes WHERE isTrashed = 1 AND updatedAt < :cutoff")
    suspend fun getPurgeableAttachments(cutoff: Long): List<String>
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    suspend fun getAllFoldersOnce(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun renameFolder(id: String, name: String)

    @Query("UPDATE folders SET colorArgb = :colorArgb WHERE id = :id")
    suspend fun setFolderColor(id: String, colorArgb: Int)

    @Query("UPDATE folders SET parentId = :parentId WHERE id = :id")
    suspend fun setFolderParent(id: String, parentId: String?)

    @Query("UPDATE folders SET parentId = :newParent WHERE parentId = :oldParent")
    suspend fun reparentChildFolders(oldParent: String, newParent: String?)

    /** Soft-trash (or restore) a set of books together. */
    @Query("UPDATE folders SET isTrashed = :trashed, trashedAt = :ts WHERE id IN (:ids)")
    suspend fun setFoldersTrashed(ids: List<String>, trashed: Boolean, ts: Long)

    @Query("DELETE FROM folders WHERE isTrashed = 1")
    suspend fun deleteTrashedFolders()

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolderById(id: String)

    /** Delete a whole set of books at once (a book plus its entire nested subtree). */
    @Query("DELETE FROM folders WHERE id IN (:ids)")
    suspend fun deleteFoldersByIds(ids: List<String>)
}
