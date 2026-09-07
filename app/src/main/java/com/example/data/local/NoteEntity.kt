package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Room entity for a note. Title and content are stored ONLY as AES-256-GCM
 * ciphertext ([encryptedTitle] / [encryptedContent]) - plaintext never touches disk.
 */
@Entity(
    tableName = "notes",
    indices = [Index("updatedAt"), Index("isPinned"), Index("isTrashed"), Index("isArchived")],
)
data class NoteEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val encryptedTitle: String,
    val encryptedContent: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,
    val folderId: String? = null,
    val tags: String = "", // comma separated
    val colorArgb: Int = 0, // 0 == default / no colour label
    val type: String = "TEXT", // TEXT | CHECKLIST
    val attachments: String = "", // comma separated image file names
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorArgb: Int = 0,
    val parentId: String? = null,
    val isTrashed: Boolean = false,
    val trashedAt: Long = 0,
)
