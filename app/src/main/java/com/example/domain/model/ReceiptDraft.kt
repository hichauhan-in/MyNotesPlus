package com.example.domain.model

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

internal data class ReceiptDraft(val merchant: String, val amount: Long?, val date: String?)

internal object ReceiptParser {
    private val totalLabel = Regex("(?i)\\b(grand total|amount paid|amount due|net payable|total payable|total)\\b")
    private val excluded = Regex("(?i)\\b(sub\\s*total|subtotal|tax|change|tender|savings|discount)\\b")
    private val amountAtEnd = Regex("(?:\\u20b9|INR|Rs\\.?)?\\s*([0-9]+(?:,[0-9]{2,3})*(?:\\.[0-9]{1,2})?)\\s*(?:INR|Rs\\.?)?$", RegexOption.IGNORE_CASE)
    private val receiptDate = Regex("\\b(?:[0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{1,2}[/.-][0-9]{1,2}[/.-][0-9]{4})\\b")

    fun parse(text: String): ReceiptDraft {
        val lines = text.take(100_000).lines().map(String::trim).filter(String::isNotBlank)
        val totals = lines.filter { totalLabel.containsMatchIn(it) && !excluded.containsMatchIn(it) }
        val amount = totals.asReversed().firstNotNullOfOrNull { line ->
            amountAtEnd.find(line)?.groupValues?.get(1)?.replace(",", "")?.let(ExpenseMoney::parse)?.takeIf { it > 0 }
        }
        val merchant = lines.firstOrNull { line -> line.any(Char::isLetter) && !totalLabel.containsMatchIn(line) && !line.startsWith("date", true) }.orEmpty().take(120)
        val date = receiptDate.findAll(text.take(100_000)).firstNotNullOfOrNull { match ->
            listOf("yyyy-MM-dd", "d/M/yyyy", "d-M-yyyy", "d.M.yyyy").firstNotNullOfOrNull { pattern ->
                val formatter = SimpleDateFormat(pattern, Locale.ROOT).apply { isLenient = false }
                val position = ParsePosition(0)
                formatter.parse(match.value, position)?.takeIf { position.index == match.value.length }
                    ?.let { SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(it) }
            }
        }
        return ReceiptDraft(merchant, amount, date)
    }
}