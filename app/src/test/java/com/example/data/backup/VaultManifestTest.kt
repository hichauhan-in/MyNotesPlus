package com.example.data.backup

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultManifestTest {
    private fun manifest() = JSONObject("""{
        "version":1,
        "notes":[{"id":"note","title":"Receipt","type":"TEXT","content":"![img](attachment://receipt.jpg)","folderId":"child","attachments":["receipt.jpg"],"createdAt":1,"updatedAt":2}],
        "books":[{"id":"parent","name":"Money"},{"id":"child","name":"Receipts","parentId":"parent"}],
        "templates":[{"id":"template","name":"Meeting","content":"Agenda"}],
        "reminders":[{"id":"reminder","noteId":"note","title":"Check","body":"Receipt","triggerAt":100,"repeat":"NONE","createdAt":1,"updatedAt":2}],
        "media":[{"entry":"media-0","name":"receipt.jpg"}]
    }""")

    @Test fun restoreCopiesRemapEveryReferenceWithoutMutatingTheSource() {
        val original = manifest()
        val restored = VaultManifest.restoredCopy(original)
        val note = VaultManifest.objects(restored, "notes").single()
        val books = VaultManifest.objects(restored, "books")
        val reminder = VaultManifest.objects(restored, "reminders").single()
        val media = VaultManifest.objects(restored, "media").single()
        assertNotEquals("note", note.getString("id"))
        assertEquals(books[1].getString("id"), note.getString("folderId"))
        assertEquals(books[0].getString("id"), books[1].getString("parentId"))
        assertEquals(note.getString("id"), reminder.getString("noteId"))
        assertTrue(note.getString("content").contains(media.getString("name")))
        assertFalse(note.getString("content").contains("receipt.jpg"))
        assertEquals("note", original.getJSONArray("notes").getJSONObject(0).getString("id"))
        VaultManifest.validate(restored)
    }

    @Test fun repeatedRestoreUsesIndependentMediaAndNoteIds() {
        val first = VaultManifest.restoredCopy(manifest())
        val second = VaultManifest.restoredCopy(manifest())
        assertNotEquals(first.getJSONArray("media").getJSONObject(0).getString("name"), second.getJSONArray("media").getJSONObject(0).getString("name"))
        assertNotEquals(first.getJSONArray("notes").getJSONObject(0).getString("id"), second.getJSONArray("notes").getJSONObject(0).getString("id"))
    }

    @Test fun missingMediaAndBookCyclesAreRejected() {
        val missing = manifest().put("media", JSONArray())
        assertThrows(IllegalArgumentException::class.java) { VaultManifest.validate(missing) }
        val cyclic = manifest()
        cyclic.getJSONArray("books").getJSONObject(0).put("parentId", "child")
        assertThrows(IllegalArgumentException::class.java) { VaultManifest.validate(cyclic) }
    }

    @Test fun restoreKeepsTrashRecoverableForANewRetentionWindow() {
        val original = manifest()
        original.getJSONArray("notes").getJSONObject(0).put("isTrashed", true).put("updatedAt", 10)
        val restored = VaultManifest.restoredCopy(original, now = 500)
        assertEquals(500L, restored.getJSONArray("notes").getJSONObject(0).getLong("updatedAt"))
    }

    @Test fun versionsAndLegacyMediaReferencesFollowTheRestoredNote() {
        val original = manifest()
        original.put("versions", JSONArray().put(JSONObject()
            .put("id", "version").put("noteId", "note").put("title", "Old board").put("updatedAt", 1)
            .put("type", "SCRIBBLE").put("content", """{"im":[{"a":"receipt.jpg"}]}""")))
        val restored = VaultManifest.restoredCopy(original)
        val note = VaultManifest.objects(restored, "notes").single()
        val version = VaultManifest.objects(restored, "versions").single()
        assertEquals(note.getString("id"), version.getString("noteId"))
        assertNotEquals("version", version.getString("id"))
        assertEquals(VaultManifest.strings(note, "attachments"), VaultManifest.strings(version, "attachments"))
        VaultManifest.validate(restored)
    }
}