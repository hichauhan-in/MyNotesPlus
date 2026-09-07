package com.example.data.attachments

import android.content.Context
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import okio.Buffer

/**
 * A Coil model that points at an encrypted attachment by name. The app's singleton [ImageLoader] is
 * configured with [EncryptedAttachmentFetcher] (see VaultNotesApp), so loading one of these decrypts
 * the file off the main thread inside Coil's own pipeline - the UI keeps Coil's async loading,
 * memory cache and crossfade with no separate loading state.
 */
data class EncAttachment(val name: String)

/** Fetches an [EncAttachment] by reading + decrypting its bytes (on Coil's IO dispatcher). */
internal class EncryptedAttachmentFetcher(
    private val context: Context,
    private val data: EncAttachment,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val bytes = AttachmentStore.readDecrypted(context, data.name)
            ?: error("Attachment ${data.name} is missing or could not be decrypted")
        return SourceResult(
            source = ImageSource(Buffer().apply { write(bytes) }, context),
            mimeType = null,
            // MEMORY keeps Coil from writing the decrypted bytes to its on-disk cache.
            dataSource = DataSource.MEMORY,
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<EncAttachment> {
        override fun create(data: EncAttachment, options: Options, imageLoader: ImageLoader): Fetcher =
            EncryptedAttachmentFetcher(context.applicationContext, data)
    }
}

/** Keys the memory cache by the attachment name so repeat displays are instant. */
internal class EncAttachmentKeyer : Keyer<EncAttachment> {
    override fun key(data: EncAttachment, options: Options): String = data.name
}
