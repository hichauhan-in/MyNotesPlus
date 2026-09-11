package com.example.data.share

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal object TextImportPolicy {
    const val MAX_BYTES = 2 * 1024 * 1024
    const val MAX_CHARS = 1_000_000

    fun decode(bytes: ByteArray): String {
        require(bytes.size <= MAX_BYTES) { "Text imports are limited to 2 MB" }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        return sanitize(text)
    }

    fun sanitize(text: String): String {
        require(text.length <= MAX_CHARS) { "Text imports are limited to one million characters" }
        require(!text.contains('\u0000')) { "This is not a text document" }
        return text.replace("\r\n", "\n").replace('\r', '\n')
            .replace("attachment://", "external-attachment:", ignoreCase = true)
            .replace(Regex("\\[\\[(table|callout|scribble|ink|inkspace):", RegexOption.IGNORE_CASE), "[$1:")
    }
}