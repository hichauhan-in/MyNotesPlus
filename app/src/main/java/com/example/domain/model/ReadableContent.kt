package com.example.domain.model

import com.example.data.export.ExpenseReport
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.json.JSONObject

@OptIn(ExperimentalEncodingApi::class)
internal object ReadableContent {
    private val block = Regex("\\[\\[(table|callout|ink|scribble):([A-Za-z0-9+/=]+)]]")

    fun text(content: String, type: String = "TEXT"): String = when (type) {
        "EXPENSE" -> runCatching { ExpenseReport.lines(content).joinToString("\n") }.getOrDefault("")
        "SCRIBBLE" -> runCatching {
            val texts = JSONObject(content).optJSONArray("t")
            if (texts == null) "" else (0 until texts.length()).joinToString("\n") { texts.getJSONObject(it).optString("t") }
        }.getOrDefault("")
        else -> AttachmentMarkup.stripTokens(block.replace(content) { match ->
            runCatching {
                when (match.groupValues[1]) {
                    "callout" -> JSONObject(String(Base64.decode(match.groupValues[2]), Charsets.UTF_8)).optString("t")
                    "table" -> {
                        val rows = JSONObject(String(Base64.decode(match.groupValues[2]), Charsets.UTF_8)).getJSONArray("c")
                        (0 until rows.length()).joinToString("\n") { index ->
                            val row = rows.getJSONArray(index)
                            (0 until row.length()).joinToString(" | ") { row.optString(it) }
                        }
                    }
                    else -> ""
                }
            }.getOrDefault("")
        }).trim()
    }

    fun matches(text: String, query: String): Boolean = terms(query).all { text.contains(it, ignoreCase = true) }

    fun terms(query: String): List<String> = query.trim().split(Regex("\\s+")).filter(String::isNotBlank).take(20)

    fun snippet(text: String, query: String, length: Int = 180): String {
        val compact = text.replace(Regex("\\s+"), " ").trim()
        val match = terms(query).map { compact.indexOf(it, ignoreCase = true) }.filter { it >= 0 }.minOrNull() ?: 0
        val start = (match - 40).coerceAtLeast(0)
        return (if (start > 0) "..." else "") + compact.drop(start).take(length) + if (compact.length > start + length) "..." else ""
    }
}