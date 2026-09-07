package com.example.data.share

import com.example.domain.model.AttachmentMarkup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TextImportPolicyTest {
    @Test fun textAndMarkdownKeepFormattingAndNormalizeLines() {
        assertEquals("# Notes\n- [ ] Milk", TextImportPolicy.decode("\uFEFF# Notes\r\n- [ ] Milk".toByteArray()))
    }

    @Test fun externalTextCannotClaimPrivateAttachmentsOrEncodedBlocks() {
        val content = TextImportPolicy.sanitize("![img](attachment://private.jpg)\n[[table:AAAA]]")
        assertTrue(AttachmentMarkup.fileNames(content).isEmpty())
        assertFalse(content.contains("[[table:"))
    }

    @Test fun invalidEncodingBinaryAndOversizedDocumentsAreRejected() {
        assertThrows(Exception::class.java) { TextImportPolicy.decode(byteArrayOf(0xC3.toByte(), 0x28)) }
        assertThrows(IllegalArgumentException::class.java) { TextImportPolicy.sanitize("binary\u0000file") }
        assertThrows(IllegalArgumentException::class.java) { TextImportPolicy.sanitize("a".repeat(TextImportPolicy.MAX_CHARS + 1)) }
    }
}