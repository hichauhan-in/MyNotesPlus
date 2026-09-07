package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.attachments.AttachmentStore
import com.example.data.local.FolderDao
import com.example.data.local.FolderEntity
import com.example.data.local.NoteDao
import com.example.domain.model.Folder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Manages "books" (nestable folders) and moving notes between them. Folder names are
 * not sensitive, so they are stored in plain text (unlike note titles / bodies).
 */
class FolderRepository(
    private val folderDao: FolderDao,
    private val noteDao: NoteDao,
    private val appContext: Context,
    private val database: AppDatabase,
) {
    val allFolders: Flow<List<Folder>> = folderDao.getAllFolders().map { list ->
        list.map { it.toFolder() }
    }

    suspend fun createFolder(name: String, parentId: String?, colorArgb: Int = 0): String = withContext(Dispatchers.IO) {
        val folder = FolderEntity(
            name = name.trim().ifBlank { "Untitled book" },
            parentId = parentId,
            colorArgb = colorArgb,
        )
        folderDao.insertFolder(folder)
        folder.id
    }

    /** Update a book's colour label (0 = default). */
    suspend fun setFolderColor(id: String, colorArgb: Int) = withContext(Dispatchers.IO) {
        folderDao.setFolderColor(id, colorArgb)
    }

    /** The ids of every existing book, used by cloud sync to detect dangling folder references. */
    suspend fun folderIdsOnce(): Set<String> = withContext(Dispatchers.IO) {
        folderDao.getAllFoldersOnce().map { it.id }.toSet()
    }

    suspend fun renameFolder(id: String, name: String) = withContext(Dispatchers.IO) {
        folderDao.renameFolder(id, name.trim().ifBlank { "Untitled book" })
    }

    /** Move a book under a new parent (null = top level). */
    suspend fun moveFolder(id: String, newParentId: String?) = withContext(Dispatchers.IO) {
        if (id != newParentId) folderDao.setFolderParent(id, newParentId)
    }

    /**
     * Delete a book. Its notes and sub-books are moved up to the book's own parent so
     * nothing is lost, then the book itself is removed.
     */
    suspend fun deleteFolder(id: String) = withContext(Dispatchers.IO) {
        val parentId = folderDao.getFolderById(id)?.parentId
        noteDao.reparentNotes(id, parentId)
        folderDao.reparentChildFolders(id, parentId)
        folderDao.deleteFolderById(id)
    }

    /**
     * Delete a book together with everything nested inside it: all sub-books (at any depth)
     * are removed and every note in the subtree is moved to Trash (recoverable during the
     * retention window). Notes are detached from their book so a later restore lands them
     * at the top level rather than in a book that no longer exists.
     */
    suspend fun deleteFolderTree(id: String) = withContext(Dispatchers.IO) {
        val subtree = subtreeIds(id)
        val now = System.currentTimeMillis()
        // Keep folderId on the notes so Trash can still show the book hierarchy.
        noteDao.trashNotesInFoldersKeepFolder(subtree, now)
        folderDao.setFoldersTrashed(subtree, true, now)
    }

    /** Restore a trashed book and everything nested inside it. */
    suspend fun restoreFolderTree(id: String) = withContext(Dispatchers.IO) {
        val subtree = subtreeIds(id)
        val now = System.currentTimeMillis()
        folderDao.setFoldersTrashed(subtree, false, now)
        noteDao.restoreNotesInFolders(subtree, now)
    }

    /** Permanently delete a trashed book, its sub-books and their notes (and image files). */
    suspend fun deleteFolderTreePermanently(id: String) = withContext(Dispatchers.IO) {
        val attachments = database.withTransaction {
            val subtree = subtreeIds(id)
            val files = noteDao.folderVersionAttachments(subtree) + noteDao.getAttachmentsInFolders(subtree)
            noteDao.deleteNotesInFolders(subtree)
            folderDao.deleteFoldersByIds(subtree)
            files
        }
        com.example.data.attachments.AttachmentMaintenance(appContext, database)
            .removeUnused(attachments.flatMap { it.split(",").filter(String::isNotBlank) })
    }

    /** Drop every trashed book row (their notes are cleared separately by emptying the note trash). */
    suspend fun emptyTrashedFolders() = withContext(Dispatchers.IO) {
        folderDao.deleteTrashedFolders()
    }

    /** [rootId] plus every book nested inside it, at any depth (trashed or not). */
    private suspend fun subtreeIds(rootId: String): List<String> {
        val all = folderDao.getAllFoldersOnce()
        val childrenByParent = all.groupBy { it.parentId }
        val result = mutableListOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(rootId)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (result.contains(current)) continue
            result.add(current)
            childrenByParent[current]?.forEach { queue.add(it.id) }
        }
        return result
    }

    suspend fun moveNoteToFolder(noteId: String, folderId: String?) = withContext(Dispatchers.IO) {
        noteDao.setFolder(noteId, folderId, System.currentTimeMillis())
    }

    suspend fun moveNotesToFolder(noteIds: Collection<String>, folderId: String?) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            noteIds.forEach { noteDao.setFolder(it, folderId, now) }
        }

    private fun FolderEntity.toFolder() = Folder(
        id = id,
        name = name,
        colorArgb = colorArgb,
        parentId = parentId,
        isTrashed = isTrashed,
    )
}
