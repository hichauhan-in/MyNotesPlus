package com.example.data.sync

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Metadata for a note blob stored in the visible "MyNotes" sync folder. */
data class RemoteNoteMeta(val fileId: String, val noteId: String, val updatedAt: Long)

/**
 * Thin Google Drive v3 REST client used by cloud sync. It only ever sends already-encrypted bytes,
 * and talks to Drive with a short-lived OAuth access token obtained via the Authorization API.
 * Covers the account probe, the visible sync folder, the hidden appData key envelope, and the
 * per-note blob list/upload/download/delete used by the two-way sync.
 */
object DriveRest {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Returns the email of the account the [accessToken] belongs to (via Drive's `about` endpoint),
     * or null on any failure. A successful non-null result confirms the token is valid and the app
     * is authorised for Drive. Retries a couple of times to ride out a transient network hiccup or
     * a token that's still propagating right after (re)connecting.
     */
    fun fetchAccountEmail(accessToken: String): String? {
        repeat(3) { attempt ->
            val email = fetchAccountEmailOnce(accessToken)
            if (email != null) return email
            if (attempt < 2) Thread.sleep(400)
        }
        return null
    }

    private fun fetchAccountEmailOnce(accessToken: String): String? = runCatching {
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/about?fields=user(emailAddress,displayName)")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            JSONObject(body).optJSONObject("user")?.optString("emailAddress")?.ifBlank { null }
        }
    }.getOrNull()

    // ---- Folders / files / appData (Slice 2) ------------------------------------

    private val jsonMedia = "application/json; charset=UTF-8".toMediaType()
    private val textMedia = "text/plain; charset=UTF-8".toMediaType()
    private val htmlMedia = "text/html; charset=UTF-8".toMediaType()
    private const val FOLDER_MIME = "application/vnd.google-apps.folder"

    /** Finds the visible "MyNotes" [name] folder, creating it if absent. Returns its id or null. */
    fun ensureFolder(accessToken: String, name: String): String? {
        val findUrl = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("q", "name = '${esc(name)}' and mimeType = '$FOLDER_MIME' and trashed = false")
            .addQueryParameter("spaces", "drive")
            .addQueryParameter("fields", "files(id)")
            .addQueryParameter("pageSize", "1")
            .build()
        firstFileId(exec(authGet(accessToken, findUrl)))?.let { return it }

        val meta = JSONObject().put("name", name).put("mimeType", FOLDER_MIME)
        val create = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?fields=id")
            .header("Authorization", "Bearer $accessToken")
            .post(meta.toString().toRequestBody(jsonMedia))
            .build()
        return exec(create)?.let { JSONObject(it).optString("id").ifBlank { null } }
    }

    /** Returns the id of a non-trashed file named [name] in the hidden appDataFolder, or null. */
    fun findAppDataFile(accessToken: String, name: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("q", "name = '${esc(name)}' and trashed = false")
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("fields", "files(id)")
            .addQueryParameter("pageSize", "1")
            .build()
        return firstFileId(exec(authGet(accessToken, url)))
    }

    /**
     * Whether the appDataFolder file [name] exists: true = present, false = confirmed absent, and
     * **null = couldn't determine** (network/HTTP failure). Callers MUST treat null as "unknown"
     * and never as "absent" - mistaking a transient error for "no key envelope" would prompt a new
     * passphrase and overwrite the real one, orphaning existing notes.
     */
    fun appDataFileExists(accessToken: String, name: String): Boolean? {
        val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("q", "name = '${esc(name)}' and trashed = false")
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("fields", "files(id)")
            .addQueryParameter("pageSize", "1")
            .build()
        return try {
            client.newCall(authGet(accessToken, url)).execute().use { r ->
                if (r.isSuccessful) firstFileId(r.body?.string()) != null else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Creates or overwrites a small text file named [name] inside the hidden appDataFolder with
     * [content]. Returns the file id, or null on failure. Used for the wrapped-key envelope.
     */
    fun upsertAppDataFile(accessToken: String, name: String, content: String): String? {
        val existing = findAppDataFile(accessToken, name)
        if (existing != null) {
            val patch = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files/$existing?uploadType=media")
                .header("Authorization", "Bearer $accessToken")
                .patch(content.toRequestBody(jsonMedia))
                .build()
            return if (exec(patch) != null) existing else null
        }
        val meta = JSONObject().put("name", name).put("parents", JSONArray().put("appDataFolder"))
        val multipart = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(meta.toString().toRequestBody(jsonMedia))
            .addPart(content.toRequestBody(jsonMedia))
            .build()
        val create = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
            .header("Authorization", "Bearer $accessToken")
            .post(multipart)
            .build()
        return exec(create)?.let { JSONObject(it).optString("id").ifBlank { null } }
    }

    /** Downloads the text content of [fileId], or null on failure. */
    fun downloadText(accessToken: String, fileId: String): String? {
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        return exec(req)
    }

    /** Lists every non-trashed note blob in the visible [folderId], or null if the listing fails. */
    fun listFolderNotes(accessToken: String, folderId: String): List<RemoteNoteMeta>? {
        val out = ArrayList<RemoteNoteMeta>()
        var pageToken: String? = null
        do {
            val pt = pageToken
            val builder = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", "'${esc(folderId)}' in parents and trashed = false")
                .addQueryParameter("spaces", "drive")
                .addQueryParameter("fields", "nextPageToken, files(id,name,appProperties)")
                .addQueryParameter("pageSize", "1000")
            if (pt != null) builder.addQueryParameter("pageToken", pt)
            val body = exec(authGet(accessToken, builder.build())) ?: return null
            val obj = runCatching { JSONObject(body) }.getOrNull() ?: return null
            obj.optJSONArray("files")?.let { files ->
                for (i in 0 until files.length()) {
                    val f = files.getJSONObject(i)
                    val id = f.optString("id").ifBlank { null } ?: continue
                    val props = f.optJSONObject("appProperties")
                    val noteId = props?.optString("noteId")?.ifBlank { null }
                        ?: f.optString("name").removeSuffix(".mnote").ifBlank { null } ?: continue
                    val updatedAt = props?.optString("updatedAt")?.toLongOrNull() ?: 0L
                    out.add(RemoteNoteMeta(id, noteId, updatedAt))
                }
            }
            pageToken = obj.optString("nextPageToken").ifBlank { null }
        } while (pageToken != null)
        return out
    }

    /**
     * Creates (when [existingId] is null) or updates an encrypted note blob named [name] in the
     * visible [folderId], stamping [noteId] + [updatedAt] into appProperties. Returns the file id.
     */
    fun putNoteFile(
        accessToken: String,
        folderId: String,
        noteId: String,
        name: String,
        content: String,
        updatedAt: Long,
        existingId: String?,
    ): String? {
        val props = JSONObject().put("noteId", noteId).put("updatedAt", updatedAt.toString())
        return if (existingId == null) {
            val meta = JSONObject()
                .put("name", name)
                .put("parents", JSONArray().put(folderId))
                .put("appProperties", props)
            uploadMultipart(
                accessToken,
                "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id",
                patch = false, metadata = meta, content = content,
            )
        } else {
            val meta = JSONObject().put("appProperties", props)
            uploadMultipart(
                accessToken,
                "https://www.googleapis.com/upload/drive/v3/files/$existingId?uploadType=multipart&fields=id",
                patch = true, metadata = meta, content = content,
            )
        }
    }

    /** Permanently deletes a Drive file (used when a note was deleted on another device). */
    fun deleteFile(accessToken: String, fileId: String): Boolean = try {
        client.newCall(
            Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId")
                .header("Authorization", "Bearer $accessToken")
                .delete()
                .build(),
        ).execute().use { it.isSuccessful }
    } catch (e: Exception) {
        false
    }

    private fun uploadMultipart(
        accessToken: String,
        url: String,
        patch: Boolean,
        metadata: JSONObject,
        content: String,
    ): String? {
        val multipart = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata.toString().toRequestBody(jsonMedia))
            .addPart(content.toRequestBody(textMedia))
            .build()
        val builder = Request.Builder().url(url).header("Authorization", "Bearer $accessToken")
        val request = (if (patch) builder.patch(multipart) else builder.post(multipart)).build()
        return exec(request)?.let { runCatching { JSONObject(it).optString("id").ifBlank { null } }.getOrNull() }
    }

    // ---- Shareable link (plaintext copy as a Google Doc) ------------------------

    /**
     * Uploads [html] as a converted **Google Doc** named [name] in [folderId] and returns its id.
     * Unlike the encrypted sync blobs, this is a readable copy the user has chosen to share.
     */
    fun uploadSharedDoc(accessToken: String, folderId: String, name: String, html: String): String? {
        val meta = JSONObject()
            .put("name", name)
            .put("parents", JSONArray().put(folderId))
            .put("mimeType", "application/vnd.google-apps.document")
        val multipart = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(meta.toString().toRequestBody(jsonMedia))
            .addPart(html.toRequestBody(htmlMedia))
            .build()
        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
            .header("Authorization", "Bearer $accessToken")
            .post(multipart)
            .build()
        return exec(request)?.let { runCatching { JSONObject(it).optString("id").ifBlank { null } }.getOrNull() }
    }

    /** Grants "anyone with the link can view" on [fileId]. Returns success. */
    fun setAnyoneReader(accessToken: String, fileId: String): Boolean {
        val body = JSONObject().put("role", "reader").put("type", "anyone").toString().toRequestBody(jsonMedia)
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId/permissions")
            .header("Authorization", "Bearer $accessToken")
            .post(body)
            .build()
        return exec(request) != null
    }

    /** The public web link for [fileId], or null. */
    fun webViewLink(accessToken: String, fileId: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId?fields=webViewLink".toHttpUrl()
        return exec(authGet(accessToken, url))?.let {
            runCatching { JSONObject(it).optString("webViewLink").ifBlank { null } }.getOrNull()
        }
    }

    private fun authGet(accessToken: String, url: okhttp3.HttpUrl): Request =
        Request.Builder().url(url).header("Authorization", "Bearer $accessToken").get().build()

    private fun exec(request: Request): String? = try {
        client.newCall(request).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    } catch (e: Exception) {
        null
    }

    private fun firstFileId(body: String?): String? {
        val files = body?.let { runCatching { JSONObject(it).optJSONArray("files") }.getOrNull() } ?: return null
        return if (files.length() == 0) null else files.getJSONObject(0).optString("id").ifBlank { null }
    }

    /** Escapes a literal for use inside a Drive `q` string (single-quoted). */
    private fun esc(value: String): String = value.replace("\\", "\\\\").replace("'", "\\'")
}
