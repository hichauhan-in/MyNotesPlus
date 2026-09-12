package com.example.data.sync

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
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

    @Test fun aSuccessfulKeyDownloadUsesTheFileValidatorNotTheMediaHeader() {
        var metadataReads = 0
        val remote = remote { request ->
            if (request.url.queryParameter("alt") == "media") {
                assertNull(request.header("If-Match"))
                response(request, "encrypted recovery key", etag = "\"media-only\"")
            } else {
                metadataReads++
                assertEquals("/drive/v2/files/key-file", request.url.encodedPath)
                assertEquals("id,etag,version", request.url.queryParameter("fields"))
                assertEquals("false", request.url.queryParameter("updateViewedDate"))
                response(request, """{"id":"key-file","version":"7","etag":"\"metadata-v7\""}""")
            }
        }
        val download = remote.downloadNote("test-token", "key-file")
        assertNotNull(download)
        assertEquals("encrypted recovery key", download?.content)
        assertEquals("\"metadata-v7\"", download?.etag)
        assertEquals(2, metadataReads)
    }

    @Test fun aChangedMetadataRevisionRejectsTheDownloadedKey() {
        var revisionReads = 0
        val remote = remote { request ->
            if (request.url.queryParameter("alt") == "media") response(request, "encrypted recovery key")
            else {
                revisionReads++
                response(request, JSONObject().put("id", "key-file").put("version", revisionReads.toString())
                    .put("etag", "\"metadata-$revisionReads\"").toString())
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

    @Test fun aKeyDownloadDoesNotDependOnHttpEtagHeaders() {
        val requests = mutableListOf<Request>()
        val remote = remote { request ->
            requests.add(request)
            when {
                request.url.queryParameter("alt") == "media" -> response(request, "encrypted recovery key")
                request.url.encodedPath == "/drive/v2/files/key-file" ->
                    response(request, """{"id":"key-file","version":"7","etag":"\"file-v7\""}""")
                else -> response(request, """{"id":"key-file","version":"7"}""")
            }
        }
        val download = remote.downloadNote("test-token", "key-file")
        assertNotNull(download)
        assertEquals("encrypted recovery key", download?.content)
        assertEquals("\"file-v7\"", download?.etag)
        assertEquals(2, requests.count { it.url.encodedPath == "/drive/v2/files/key-file" })
        assertTrue(requests.all { it.method == "GET" })
    }

    @Test fun metadataWithoutAFileValidatorNeverUsesTheVersionOrWildcardInstead() {
        listOf("", "*", "7", "W/\"weak\"").forEach { invalid ->
            var downloads = 0
            val remote = remote { request ->
                if (request.url.queryParameter("alt") == "media") downloads++
                response(request, JSONObject().put("id", "key-file").put("version", "7").put("etag", invalid).toString())
            }
            assertThrows(DriveRequestException::class.java) { remote.downloadNote("test-token", "key-file") }
            assertEquals(0, downloads)
        }
    }

    @Test fun collectionUpdatesUseTheMatchingV2ConditionalEndpoint() {
        val requests = mutableListOf<Request>()
        val remote = remote { request ->
            requests.add(request)
            response(request, """{"id":"key-file"}""")
        }
        assertTrue(remote.updateCollection("test-token", "key-file", "new encrypted key", "\"file-v7\""))
        val request = requests.single()
        assertEquals("PUT", request.method)
        assertEquals("/upload/drive/v2/files/key-file", request.url.encodedPath)
        assertEquals("\"file-v7\"", request.header("If-Match"))
        assertEquals("multipart", request.url.queryParameter("uploadType"))
    }

    @Test fun conditionalNoteUpdatesPreservePrivateSyncProperties() {
        val remote = remote { request ->
            assertEquals("PUT", request.method)
            assertEquals("/upload/drive/v2/files/note-file", request.url.encodedPath)
            assertEquals("\"note-v2\"", request.header("If-Match"))
            val multipart = request.body as MultipartBody
            val metadata = JSONObject(Buffer().also { multipart.part(0).body.writeTo(it) }.readUtf8())
            assertFalse(metadata.has("appProperties"))
            val properties = metadata.getJSONArray("properties")
            val values = (0 until properties.length()).associate { index ->
                val property = properties.getJSONObject(index)
                assertEquals("PRIVATE", property.getString("visibility"))
                property.getString("key") to property.getString("value")
            }
            assertEquals(mapOf("noteId" to "local-note", "updatedAt" to "12345"), values)
            assertEquals("encrypted note", Buffer().also { multipart.part(1).body.writeTo(it) }.readUtf8())
            response(request, """{"id":"note-file"}""")
        }
        assertEquals("note-file", remote.putNoteFile("test-token", "folder", "local-note", "local-note.mnote", "encrypted note", 12345, "note-file", "\"note-v2\""))
    }

    @Test fun rejectedConditionalWritesNeverRetryWithoutTheValidator() {
        val requests = mutableListOf<Request>()
        val remote = remote { request ->
            requests.add(request)
            response(request, "{}", 412)
        }
        assertThrows(DriveRequestException::class.java) { remote.updateCollection("test-token", "key-file", "new key", "\"stale\"") }
        assertNull(remote.putNoteFile("test-token", "folder", "note", "note.mnote", "new note", 12345, "note-file", "\"stale\""))
        assertFalse(remote.deleteFile("test-token", "note-file", "\"stale\""))
        assertEquals(3, requests.size)
        assertTrue(requests.all { it.header("If-Match") == "\"stale\"" && it.url.encodedPath.contains("/drive/v2/") })
    }

    @Test fun missingOrUnsafeWriteValidatorsNeverSendARequest() {
        var requests = 0
        val remote = remote { request -> requests++; response(request, "{}") }
        listOf("", "*", "7").forEach { invalid ->
            assertThrows(DriveRequestException::class.java) { remote.updateCollection("test-token", "key-file", "content", invalid) }
            assertFalse(remote.deleteFile("test-token", "note-file", invalid))
        }
        assertThrows(DriveRequestException::class.java) { remote.putNoteFile("test-token", "folder", "note", "note.mnote", "content", 1, "note-file", null) }
        assertEquals(0, requests)
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