package com.example.data.share

import com.example.domain.model.AttachmentMarkup
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class ShareImportPolicyTest {
    @Test fun importsCannotReuseOrEscapeExistingAttachmentPaths() {
        assertNotEquals("photo.jpg", ShareImportPolicy.freshName("photo.jpg"))
        assertNotEquals(ShareImportPolicy.freshName("photo.jpg"), ShareImportPolicy.freshName("photo.jpg"))
        listOf("../photo.jpg", "folder/photo.jpg", "folder\\photo.jpg", ".enc_migrated", "..").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) { ShareImportPolicy.freshName(name) }
        }
    }

    @Test fun remappingPreservesTextAndImageWidth() {
        val content = "photo.jpg stays text\n![img](attachment://photo.jpg?w=65)\n![audio](attachment://voice.m4a)"
        val renamed = ShareImportPolicy.renameContent(content, false, mapOf("photo.jpg" to "new.jpg", "voice.m4a" to "new.m4a"))
        assertTrue(renamed.startsWith("photo.jpg stays text"))
        assertTrue(renamed.contains("attachment://new.jpg?w=65"))
        assertEquals(listOf("new.jpg", "new.m4a"), AttachmentMarkup.fileNames(renamed))
    }

    @Test fun boardImageNodesAndAttachmentTokensAreBothRemapped() {
        val content = """{"im":[{"a":"photo.jpg","x":10}],"att":["![img](attachment://photo.jpg)"],"t":[{"t":"photo.jpg"}]}"""
        val renamed = ShareImportPolicy.renameContent(content, true, mapOf("photo.jpg" to "new.jpg"))
        val board = JSONObject(renamed)
        assertEquals("new.jpg", board.getJSONArray("im").getJSONObject(0).getString("a"))
        assertEquals("photo.jpg", board.getJSONArray("t").getJSONObject(0).getString("t"))
        assertEquals(listOf("new.jpg"), AttachmentMarkup.fileNames(renamed))
    }

    @Test fun missingAttachmentsCannotReferToAnotherLocalNote() {
        assertThrows(IllegalArgumentException::class.java) {
            ShareImportPolicy.renameContent("![img](attachment://existing.jpg)", false, emptyMap())
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShareImportPolicy.renameContent("""{"im":[{"a":"existing.jpg"}]}""", true, emptyMap())
        }
    }

    @Test fun sizeLimitIsEnforcedWhileReadingNotAfterAllocation() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        assertEquals(4, ShareImportPolicy.readBounded(ByteArrayInputStream(bytes), 4).size)
        assertThrows(IllegalArgumentException::class.java) { ShareImportPolicy.readBounded(ByteArrayInputStream(bytes), 3) }
    }
}