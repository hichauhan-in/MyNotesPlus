package com.example.data.sync

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class DriveRestTest {
    private fun remote(reply: (Request) -> Response): DriveRestClient = DriveRestClient(
        OkHttpClient.Builder().addInterceptor { chain -> reply(chain.request()) }.build(),
    )

    private fun response(request: Request, body: String, code: Int = 200, etag: String? = null): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Test response")
            .body(body.toResponseBody()).apply { if (etag != null) header("ETag", etag) }.build()

    @Test fun emptyAccountListsDoNotRequireAnExplicitFilesArray() {
        val remote = remote { request -> response(request, """{"kind":"drive#fileList","incompleteSearch":false}""") }
        assertEquals(DriveFileLookup.Missing, remote.lookupAppDataFile("test-token", "mynotes.key.json"))
        assertEquals(DriveFileLookup.Missing, remote.lookupFolder("test-token", "MyNotes"))
    }

    @Test fun errorsAndUnrecognisedListsNeverAuthorizeNewSetup() {
        listOf(
            401 to "{}",
            403 to "{}",
            500 to "{}",
            200 to "{}",
            200 to """{"files":null}""",
            200 to """{"files":[],"incompleteSearch":true}""",
        ).forEach { (code, body) ->
            val remote = remote { request -> response(request, body, code) }
            if (code == 200) assertEquals(DriveFileLookup.Unknown, remote.lookupAppDataFile("test-token", "mynotes.key.json"))
            else assertThrows(DriveRequestException::class.java) { remote.lookupAppDataFile("test-token", "mynotes.key.json") }
        }
    }

    @Test fun emptyIntermediatePagesAreFollowedBeforeDeclaringTheKeyMissing() {
        val pages = mutableListOf<String?>()
        val remote = remote { request ->
            val page = request.url.queryParameter("pageToken")
            pages.add(page)
            response(request, if (page == null) """{"kind":"drive#fileList","nextPageToken":"second-page"}"""
                else """{"kind":"drive#fileList","files":[{"id":"existing-key"}]}""")
        }
        assertEquals(DriveFileLookup.Found("existing-key"), remote.lookupAppDataFile("test-token", "mynotes.key.json"))
        assertEquals(listOf(null, "second-page"), pages)
    }

    @Test fun aFullyEmptyPaginatedAccountCanStartSetup() {
        val remote = remote { request -> response(request,
            if (request.url.queryParameter("pageToken") == null) """{"kind":"drive#fileList","files":[],"nextPageToken":"last-page"}"""
            else """{"kind":"drive#fileList","incompleteSearch":false}""",
        ) }
        assertEquals(DriveFileLookup.Missing, remote.lookupFolder("test-token", "MyNotes"))
    }

    @Test fun duplicateMatchesAndRepeatedPageTokensRemainAmbiguous() {
        val duplicates = remote { request -> response(request,
            if (request.url.queryParameter("pageToken") == null) """{"files":[{"id":"first-key"}],"nextPageToken":"last-page"}"""
            else """{"files":[{"id":"second-key"}]}""",
        ) }
        assertEquals(DriveFileLookup.Unknown, duplicates.lookupAppDataFile("test-token", "mynotes.key.json"))
        var requests = 0
        val repeated = remote { request ->
            requests++
            response(request, """{"files":[],"nextPageToken":"same-page"}""")
        }
        assertEquals(DriveFileLookup.Unknown, repeated.lookupFolder("test-token", "MyNotes"))
        assertEquals(2, requests)
    }

    @Test fun aSuccessfulKeyDownloadCanUseTheMetadataEtag() {
        var guardedDownloads = 0
        val remote = remote { request ->
            if (request.url.queryParameter("alt") == "media") {
                if (request.header("If-Match") == "\"metadata-v7\"") guardedDownloads++
                response(request, "encrypted recovery key")
            }
            else response(request, """{"id":"key-file","version":"7"}""", etag = "\"metadata-v7\"")
        }
        val download = remote.downloadNote("test-token", "key-file")
        assertNotNull(download)
        assertEquals("encrypted recovery key", download?.content)
        assertEquals("\"metadata-v7\"", download?.etag)
        assertEquals(1, guardedDownloads)
    }

    @Test fun aChangedMetadataRevisionRejectsTheDownloadedKey() {
        var revisionReads = 0
        val remote = remote { request ->
            if (request.url.queryParameter("alt") == "media") response(request, "encrypted recovery key")
            else {
                revisionReads++
                response(request, """{"id":"key-file","version":"$revisionReads"}""", etag = "\"metadata-$revisionReads\"")
            }
        }
        assertNull(remote.downloadNote("test-token", "key-file"))
        assertTrue(revisionReads >= 2)
    }

    @Test fun downloadsWithoutAnyRevisionValidatorStayBlocked() {
        val remote = remote { request ->
            if (request.url.queryParameter("alt") == "media") response(request, "encrypted recovery key")
            else response(request, """{"id":"key-file","version":"7"}""")
        }
        val failure = assertThrows(DriveRequestException::class.java) { remote.downloadNote("test-token", "key-file") }
        assertTrue(failure.userMessage.contains("MISSING_REVISION"))
    }

    @Test fun googlePermissionFailuresExposeTheStepButNeverRawResponseDetails() {
        val remote = remote { request -> response(request,
            """{"error":{"message":"private@example.test secret-token","errors":[{"reason":"insufficientPermissions"}]}}""", 403,
        ) }
        val failure = assertThrows(DriveRequestException::class.java) { remote.lookupAppDataFile("secret-token", "mynotes.key.json") }
        assertTrue(failure.userMessage.contains("Looking up recovery data"))
        assertTrue(failure.userMessage.contains("HTTP 403"))
        assertTrue(failure.userMessage.contains("hidden app-data"))
        assertFalse(failure.userMessage.contains("private@example.test"))
        assertFalse(failure.userMessage.contains("secret-token"))
    }

    @Test fun apiConfigurationAndNetworkFailuresHaveDifferentMessages() {
        val disabled = remote { request -> response(request, """{"error":{"details":[{"reason":"SERVICE_DISABLED"}]}}""", 403) }
        val configuration = assertThrows(DriveRequestException::class.java) { disabled.fetchAccountEmail("test-token") }
        assertTrue(configuration.userMessage.contains("Google Cloud project"))
        val offline = remote { throw java.io.IOException("private-network-details") }
        val network = assertThrows(DriveRequestException::class.java) { offline.lookupFolder("test-token", "MyNotes") }
        assertTrue(network.userMessage.contains("could not reach Google Drive"))
        assertFalse(network.userMessage.contains("private-network-details"))
    }

    @Test fun sharedFolderLookupStillReturnsNullInsteadOfThrowingOnDeniedAccess() {
        val methods = mutableListOf<String>()
        val remote = remote { request ->
            methods.add(request.method)
            response(request, "{}", 403)
        }
        assertNull(remote.ensureFolder("test-token", "MyNotes Shared"))
        assertEquals(listOf("GET"), methods)
    }
}