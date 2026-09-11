package com.example.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class DriveFolderStatusTest {
    @Test fun activeAndTrashedFoldersAreDistinguished() {
        assertEquals(DriveFolderStatus.PRESENT, DriveFolderStatus.fromResponse(200, """{"mimeType":"application/vnd.google-apps.folder","trashed":false}"""))
        assertEquals(DriveFolderStatus.MISSING, DriveFolderStatus.fromResponse(200, """{"mimeType":"application/vnd.google-apps.folder","trashed":true}"""))
        assertEquals(DriveFolderStatus.MISSING, DriveFolderStatus.fromResponse(404, null))
    }

    @Test fun authNetworkAndInvalidResponsesNeverMeanMissing() {
        listOf(401, 403, 429, 500, 503).forEach { code ->
            assertEquals(DriveFolderStatus.UNKNOWN, DriveFolderStatus.fromResponse(code, "{}"))
        }
        listOf(null, "", "{}", "not JSON", """{"mimeType":"text/plain","trashed":false}""").forEach { body ->
            assertEquals(DriveFolderStatus.UNKNOWN, DriveFolderStatus.fromResponse(200, body))
        }
    }
}