package com.example.data.sync

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