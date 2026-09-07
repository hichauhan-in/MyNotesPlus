package com.example.data.sync

import com.example.data.export.ExportFormat
import com.example.data.export.Exporter
import com.example.domain.model.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Creates a shareable Google Drive link to a **readable copy** of a note. This is deliberately
 * separate from the encrypted sync blobs: the user is explicitly choosing to share a plaintext copy
 * (rendered to a Google Doc) that anyone with the link can view. Nothing here touches the E2EE data.
 */
object DriveShare {
    private const val FOLDER_NAME = "MyNotes Shared"

    /** Uploads a copy of [note] as a Google Doc, makes it link-viewable, and returns the URL (or null). */
    suspend fun createLink(accessToken: String, note: Note): String? = withContext(Dispatchers.IO) {
        val folderId = DriveRest.ensureFolder(accessToken, FOLDER_NAME) ?: return@withContext null
        val html = String(Exporter.noteBytes(note, ExportFormat.HTML), Charsets.UTF_8)
        val name = Exporter.noteFileBase(note)
        val fileId = DriveRest.uploadSharedDoc(accessToken, folderId, name, html) ?: return@withContext null
        DriveRest.setAnyoneReader(accessToken, fileId)
        DriveRest.webViewLink(accessToken, fileId)
    }
}
