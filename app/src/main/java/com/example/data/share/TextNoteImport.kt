package com.example.data.share

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.domain.model.Note
import java.util.UUID

internal object TextNoteImport {
    fun displayName(context: Context, uri: Uri): String = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
    }.orEmpty()

    fun isText(context: Context, uri: Uri): Boolean {
        val name = displayName(context, uri)
        return name.endsWith(".txt", true) || name.endsWith(".md", true) || name.endsWith(".markdown", true) ||
            context.contentResolver.getType(uri) in setOf("text/plain", "text/markdown", "text/x-markdown")
    }

    fun read(context: Context, uri: Uri, folderId: String?): Note {
        require(uri.scheme == "content") { "Choose a document from the file picker" }
        val text = requireNotNull(context.contentResolver.openInputStream(uri)).use { TextImportPolicy.decode(ShareImportPolicy.readBounded(it, TextImportPolicy.MAX_BYTES)) }
        val title = displayName(context, uri).substringBeforeLast('.').ifBlank { "Imported note" }.take(200)
        val now = System.currentTimeMillis()
        return Note(id = UUID.randomUUID().toString(), title = title, content = text, createdAt = now, updatedAt = now, folderId = folderId)
    }
}