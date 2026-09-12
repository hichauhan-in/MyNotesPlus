package com.example.data.sync

import org.json.JSONObject

internal class DriveRequestException private constructor(val userMessage: String) : Exception(userMessage) {
    companion object {
        fun http(step: String, status: Int, body: String?): DriveRequestException {
            val reason = runCatching {
                val error = JSONObject(body.orEmpty()).optJSONObject("error")
                error?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason")?.takeIf { it.isNotBlank() }
                    ?: error?.optJSONArray("details")?.optJSONObject(0)?.optString("reason")
            }.getOrNull()
            val explanation = when {
                reason in setOf("accessNotConfigured", "SERVICE_DISABLED") ->
                    "The Google Drive API is disabled for this app's Google Cloud project. Enable it before retrying."
                reason in setOf("insufficientPermissions", "ACCESS_TOKEN_SCOPE_INSUFFICIENT") ->
                    "Reconnect Google Drive and allow both file access and hidden app-data access."
                reason == "domainPolicy" -> "Your Google Workspace administrator is blocking this app's Drive access."
                reason == "storageQuotaExceeded" -> "This Google account's Drive storage is full. Free space before retrying."
                reason in setOf("rateLimitExceeded", "userRateLimitExceeded", "dailyLimitExceeded") || status == 429 ->
                    "Google Drive's request limit was reached. Try again later."
                status == 401 -> "Google authorization expired or was revoked. Reconnect Google Drive."
                status == 403 -> "Google denied this Drive operation. Check this app's Google account permissions."
                status == 404 -> "The requested Drive item is no longer accessible. Check again before retrying."
                status == 409 || status == 412 -> "Drive changed during this operation. Check again before retrying."
                status >= 500 -> "Google Drive is temporarily unavailable. Try again later."
                else -> "Google rejected this Drive request. Report this error; deleting your notes will not fix it."
            }
            return DriveRequestException("$step (HTTP $status). $explanation")
        }

        fun network(step: String) = DriveRequestException("$step: could not reach Google Drive. Check your connection and retry.")
        fun invalidResponse(step: String) = DriveRequestException("$step: Google returned an unexpected response. Retry and report this step if it persists.")
        fun missingRevision() = DriveRequestException("Reading Drive data: Google did not provide the file revision needed for a safe update (MISSING_REVISION). Reconnect and retry.")
    }
}

sealed interface DriveFileLookup {
    data class Found(val id: String) : DriveFileLookup
    object Missing : DriveFileLookup
    object Unknown : DriveFileLookup
}

interface DriveSyncRemote {
    fun fetchAccountEmail(accessToken: String): String?
    fun lookupFolder(accessToken: String, name: String): DriveFileLookup
    fun lookupAppDataFile(accessToken: String, name: String): DriveFileLookup
    fun folderStatus(accessToken: String, folderId: String): DriveFolderStatus
    fun generateFileId(accessToken: String): String?
    fun createFolder(accessToken: String, name: String, fileId: String): Boolean
    fun createAppDataFile(accessToken: String, name: String, content: String): String?
    fun downloadNote(accessToken: String, fileId: String): RemoteNoteDownload?
    fun updateCollection(accessToken: String, fileId: String, content: String, etag: String): Boolean
    fun listFolderNotes(accessToken: String, folderId: String): List<RemoteNoteMeta>?
    fun putNoteFile(accessToken: String, folderId: String, noteId: String, name: String, content: String, updatedAt: Long, existingId: String?, etag: String? = null): String?
    fun deleteFile(accessToken: String, fileId: String, etag: String? = null): Boolean
}