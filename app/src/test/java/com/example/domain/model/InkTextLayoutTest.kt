package com.example.domain.model

import com.example.data.share.TextImportPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class InkTextLayoutTest {
    @Test fun writingAnchorRoundTripsWithoutMovingEarlierContent() {
        val anchor = InkTextLayout.belowInk(180.5f)
        val content = "Existing text\n${InkTextLayout.encode(anchor)}\nLater text"
        assertEquals(197, InkTextLayout.decode(content.lines()[1]))
        assertEquals(97f, InkTextLayout.gapBefore(anchor, 100f), 0.001f)
        assertEquals(0f, InkTextLayout.gapBefore(anchor, 240f), 0.001f)
        assertEquals("Existing text\n\nLater text", InkTextLayout.strip(content))
    }

    @Test fun anchorsDoNotLeakIntoPreviewsSearchOrExternalTextImports() {
        val content = "Before\n[[inkspace:200]]\nAfter"
        assertFalse(AttachmentMarkup.stripTokens(content).contains("inkspace"))
        assertFalse(ReadableContent.text(content).contains("inkspace"))
        assertFalse(TextImportPolicy.sanitize(content).contains("[[inkspace:"))
    }

    @Test fun malformedAndOutOfRangeAnchorsAreNotAccepted() {
        listOf("[[inkspace:-1]]", "[[inkspace:NaN]]", "[[inkspace:9999999]]", "[[inkspace:1e6]]").forEach {
            assertNull(InkTextLayout.decode(it))
        }
        assertThrows(IllegalArgumentException::class.java) { InkTextLayout.belowInk(Float.NaN) }
    }
}