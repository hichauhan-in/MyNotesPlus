package com.example.domain.model

import com.example.data.local.ExpenseCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptParserTest {
    @Test fun extractsLabelledTotalAndDateWithoutUsingTaxOrChange() {
        val receipt = ReceiptParser.parse("City Groceries\nDate: 07/09/2026\nSubtotal 900.00\nTax 100.00\nGrand Total INR 1,000.00\nCash tender 2000.00\nChange 1000.00")
        assertEquals("City Groceries", receipt.merchant)
        assertEquals(1000_00L, receipt.amount)
        assertEquals("2026-09-07", receipt.date)
    }

    @Test fun doesNotGuessATotalFromUnlabelledNumbers() {
        val receipt = ReceiptParser.parse("Shop\nPhone 9999999999\nItem 250.00\nSubtotal 250.00\nDate 31/02/2026")
        assertNull(receipt.amount)
        assertNull(receipt.date)
    }

    @Test fun receiptBelongsToOneExecutionNotEveryReuse() {
        val model = ExpenseModel(listOf(ExpAccount(id = "bank", name = "Bank", balance = 5000_00, sections = listOf(
            ExpSection(id = "expenses", name = "Groceries", iconKey = "other", kind = ExpenseKind.EXPENSE, items = listOf(
                ExpItem(id = "groceries", name = "Groceries", amount = 1000_00, receiptToken = "![img](attachment://receipt.jpg)", receiptDate = "2026-09-07"),
            )),
        ))))
        val recorded = model.complete("bank", "expenses", "groceries", now = 100)
        assertEquals("![img](attachment://receipt.jpg)", recorded.history.single().receiptToken)
        assertNull(recorded.accounts.single().sections.single().items.single().receiptToken)
        val reused = recorded.complete("bank", "expenses", "groceries", now = 200)
        assertNull(reused.history.last().receiptToken)
        assertEquals(reused, ExpenseCodec.decode(ExpenseCodec.encode(reused)))
    }
}