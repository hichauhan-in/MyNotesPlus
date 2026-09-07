package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "note_versions",
    foreignKeys = [ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("noteId")],
)
data class NoteVersionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val noteId: String,
    val encryptedTitle: String,
    val encryptedContent: String,
    val type: String,
    val attachments: String,
    val updatedAt: Long,
) {
    companion object {
        fun from(note: NoteEntity) = NoteVersionEntity(
            noteId = note.id, encryptedTitle = note.encryptedTitle, encryptedContent = note.encryptedContent,
            type = note.type, attachments = note.attachments, updatedAt = note.updatedAt,
        )
    }
}