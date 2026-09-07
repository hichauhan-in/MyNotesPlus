package com.example.data.share

import com.example.domain.model.AttachmentMarkup
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID

internal object ShareImportPolicy {
    const val MAX_FILE_BYTES = 16 * 1024 * 1024
    const val MAX_ATTACHMENT_BYTES = 6 * 1024 * 1024
    const val MAX_ATTACHMENTS_BYTES = 8 * 1024 * 1024
    const val MAX_CONTENT_CHARS = 1_000_000
    const val MAX_ATTACHMENTS = 100
    private val safeName = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,179}")

    fun freshName(original: String): String {
        require(safeName.matches(original) && !original.contains("..")) { "Invalid attachment name" }
        val extension = original.substringAfterLast('.', "bin").lowercase()
        require(extension in setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp", "m4a", "mp3", "wav", "ogg", "aac", "bin")) { "Unsupported attachment" }
        return "import_${UUID.randomUUID()}.$extension"
    }

    fun readBounded(input: InputStream, maxBytes: Int = MAX_FILE_BYTES): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(count <= maxBytes - output.size()) { "Share file is too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    fun renameContent(content: String, isBoard: Boolean, replacements: Map<String, String>): String {
        require(content.length <= MAX_CONTENT_CHARS) { "Note is too large" }
        require(AttachmentMarkup.fileNames(content).all { it in replacements }) { "An attachment is missing" }
        val rewritten = AttachmentMarkup.renameFiles(content, replacements)
        if (!isBoard || content.isBlank()) return rewritten
        val board = JSONObject(rewritten)
        val images = board.optJSONArray("im")
        if (images != null) for (index in 0 until images.length()) {
            val image = images.getJSONObject(index)
            val original = image.getString("a")
            image.put("a", requireNotNull(replacements[original]) { "A board image is missing" })
        }
        return board.toString()
    }
}