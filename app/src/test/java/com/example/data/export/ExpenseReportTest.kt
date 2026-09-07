package com.example.data.export

import com.example.data.local.ExpenseCodec
import com.example.domain.model.ExpAccount
import com.example.domain.model.ExpItem
import com.example.domain.model.ExpSection
import com.example.domain.model.ExpenseKind
import com.example.domain.model.ExpenseModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseReportTest {
    private fun completed(): ExpenseModel = ExpenseModel(accounts = listOf(
        ExpAccount(id = "hdfc", name = "HDFC", balance = 50_000_00, sections = listOf(
            ExpSection(id = "section", name = "Savings", iconKey = "transfer", kind = ExpenseKind.TRANSFER,
                items = listOf(ExpItem(id = "item", name = "Monthly saving", amount = 10_000_00, toAccountId = "sbi"))),
        )),
        ExpAccount(id = "sbi", name = "SBI", balance = 0),
    )).complete("hdfc", "section", "item", now = 100)

    @Test fun reportIncludesBalancesAndBothSidesOfCompletedTransfer() {
        val text = ExpenseReport.lines(ExpenseCodec.encode(completed())).joinToString("\n")
        assertTrue(text.contains("Current balance: INR 40000.00"))
        assertTrue(text.contains("HDFC: change -INR 10000.00"))
        assertTrue(text.contains("SBI: change +INR 10000.00"))
        assertTrue(text.contains("Completed"))
    }

    @Test fun reversalsRemainPartOfTheExportedAuditRecord() {
        val completed = completed()
        val reversed = completed.reverse(completed.history.single().id, now = 200)
        val text = ExpenseReport.lines(ExpenseCodec.encode(reversed)).joinToString("\n")
        assertTrue(text.contains("[Reusable / Not recorded yet] Monthly saving"))
        assertTrue(text.contains("Saved outflows: INR 10000.00"))
        assertTrue(text.contains("Reversed"))
        assertTrue(text.contains("Reversal"))
        assertTrue(text.contains("SBI: change -INR 10000.00"))
    }

    @Test fun htmlEscapesUserContent() {
        val model = ExpenseModel(accounts = listOf(ExpAccount(name = "<script>alert('x')</script>")))
        val html = ExpenseReport.html(ExpenseCodec.encode(model))
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
    }

    @Test fun markdownCannotBeTerminatedByUserSuppliedCodeFences() {
        val model = ExpenseModel(accounts = listOf(ExpAccount(name = "Bank\n```\n<script>")))
        val markdown = ExpenseReport.markdown(ExpenseCodec.encode(model))
        assertTrue(markdown.lines().all { it.startsWith("    ") })
    }
}