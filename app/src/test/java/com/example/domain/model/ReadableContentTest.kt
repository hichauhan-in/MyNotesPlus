package com.example.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
class ReadableContentTest {
    @Test fun tablesAndCalloutsExposeReadableTextNotEncoding() {
        val table = Base64.encode("""{"c":[["Category","Amount"],["Groceries","1000"]]}""".toByteArray())
        val callout = Base64.encode("""{"t":"Remember insurance renewal"}""".toByteArray())
        val text = ReadableContent.text("[[table:$table]]\n[[callout:$callout]]\n![img](attachment://private.jpg)")
        assertTrue(ReadableContent.matches(text, "groceries 1000"))
        assertTrue(ReadableContent.matches(text, "INSURANCE renewal"))
        assertFalse(text.contains("private.jpg"))
        assertFalse(text.contains(table))
    }

    @Test fun boardTextIsSearchableWithoutCoordinates() {
        assertEquals("Project idea", ReadableContent.text("""{"t":[{"t":"Project idea","x":400}]}""", "SCRIBBLE"))
    }

    @Test fun malformedBlocksDoNotBreakSearch() {
        assertEquals("Before  After", ReadableContent.text("Before [[table:AAAA]] After"))
    }

    @Test fun snippetCentersOnTheMatchingContent() {
        val text = "Introduction ".repeat(40) + "target phrase" + " details".repeat(50)
        assertTrue(ReadableContent.snippet(text, "target").contains("target phrase"))
        assertTrue(ReadableContent.snippet(text, "target").startsWith("..."))
    }
}