package com.example.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Shares exported notes/books with other apps via the system share sheet (which includes Drive,
 * Gmail, messaging apps, etc.). Files are written to a private cache folder and handed out through
 * the app's [FileProvider], so no storage permission is needed and nothing is left in public
 * storage. Only already-decrypted, user-initiated exports pass through here.
 */
object ShareIO {

    private const val SHARE_DIR = "shared"

    /** Fresh destination file in the cache share folder; clears previous shares to avoid clutter. */
    private fun freshFile(context: Context, fileName: String): File {
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        return File(dir, fileName)
    }

    private fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Writes [bytes] to a shareable cache file and returns its content Uri (off-main safe). */
    fun writeShareFile(context: Context, fileName: String, bytes: ByteArray): Uri? = runCatching {
        val file = freshFile(context, fileName).apply { writeBytes(bytes) }
        uriFor(context, file)
    }.getOrNull()

    /** Streams into a shareable cache file via [block] and returns its content Uri (off-main safe). */
    fun writeShareStream(context: Context, fileName: String, block: (OutputStream) -> Unit): Uri? = runCatching {
        val file = freshFile(context, fileName)
        FileOutputStream(file).use(block)
        uriFor(context, file)
    }.getOrNull()

    /** Opens the system share sheet for a previously written file [uri]. Call on the main thread. */
    fun shareFile(context: Context, uri: Uri, mime: String, subject: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "Share")) }
    }

    /** Opens the system share sheet with plain [text]. Call on the main thread. */
    fun shareText(context: Context, subject: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "Share")) }
    }
}
