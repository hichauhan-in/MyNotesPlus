package com.example.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseLedgerTest {
    private fun tracker(kind: ExpenseKind, amount: Long = 10_000_00L): ExpenseModel = ExpenseModel(
        accounts = listOf(
            ExpAccount(
                id = "hdfc", name = "HDFC", balance = 50_000_00,
                sections = listOf(ExpSection(
                    id = "section", name = "Monthly plan", iconKey = "salary", kind = kind,
                    items = listOf(ExpItem(id = "item", name = "September", amount = amount, toAccountId = "sbi")),
                )),
            ),
            ExpAccount(id = "sbi", name = "SBI", balance = 20_000_00),
        ),
    )

    @Test fun creditOnlyChangesBalanceWhenCompleted() {
        val pending = tracker(ExpenseKind.CREDIT)
        assertEquals(50_000_00L, pending.accounts.first().balance)
        val completed = pending.complete("hdfc", "section", "item", now = 100)
        assertEquals(60_000_00L, completed.accounts.first().balance)
        assertEquals(100L, completed.accounts.first().sections.single().items.single().completedAt)
        assertEquals(1, completed.history.size)
    }

    @Test fun transferMovesExactlyTheSameAmountBetweenAccounts() {
        val pending = tracker(ExpenseKind.TRANSFER)
        val completed = pending.complete("hdfc", "section", "item")
        assertEquals(40_000_00L, completed.accounts[0].balance)
        assertEquals(30_000_00L, completed.accounts[1].balance)
        assertEquals(pending.accounts.sumOf { it.balance }, completed.accounts.sumOf { it.balance })
        assertEquals("SBI", completed.history.single().toAccountName)
    }

    @Test fun savedActionCanRunAgainWithoutDuplicatingItsDefinition() {
        val first = tracker(ExpenseKind.SAVINGS).complete("hdfc", "section", "item", now = 100)
        val second = first.complete("hdfc", "section", "item", now = 200)
        assertEquals(30_000_00L, second.accounts.first().balance)
        assertEquals(1, second.accounts.first().sections.single().items.size)
        assertEquals(200L, second.accounts.first().sections.single().items.single().completedAt)
        assertEquals(2, second.history.size)
        assertEquals(2, second.history.map { it.id }.distinct().size)
        assertEquals(10_000_00L, second.accounts.first().configuredOutflow())
    }

    @Test fun changingSavedAmountDoesNotRewritePreviousExecutions() {
        val first = tracker(ExpenseKind.CREDIT).complete("hdfc", "section", "item", now = 100)
        val account = first.accounts.first()
        val section = account.sections.single()
        val edited = first.copy(accounts = listOf(account.copy(sections = listOf(section.copy(
            items = listOf(section.items.single().copy(amount = 25_000_00)),
        )))) + first.accounts.drop(1))
        val next = edited.complete("hdfc", "section", "item", now = 200)
        assertEquals(85_000_00L, next.accounts.first().balance)
        assertEquals(listOf(10_000_00L, 25_000_00L), next.history.map { it.amount })
    }

    @Test fun repeatedTransferChecksTheCurrentBalanceEveryTime() {
        val first = tracker(ExpenseKind.TRANSFER, 30_000_00).complete("hdfc", "section", "item")
        assertThrows(IllegalArgumentException::class.java) { first.complete("hdfc", "section", "item") }
        assertEquals(20_000_00L, first.accounts.first().balance)
        assertEquals(50_000_00L, first.accounts.last().balance)
        assertEquals(1, first.history.size)
    }

    @Test fun reversingOlderExecutionKeepsTheLatestUseAndOtherBalances() {
        val first = tracker(ExpenseKind.TRANSFER).complete("hdfc", "section", "item", now = 100)
        val second = first.complete("hdfc", "section", "item", now = 200)
        val reversed = second.reverse(first.history.single().id, now = 300)
        assertEquals(40_000_00L, reversed.accounts.first().balance)
        assertEquals(30_000_00L, reversed.accounts.last().balance)
        assertEquals(200L, reversed.accounts.first().sections.single().items.single().completedAt)
        assertEquals(3, reversed.history.size)
    }

    @Test fun insufficientFundsLeaveBothAccountsUnchanged() {
        val pending = tracker(ExpenseKind.TRANSFER, 60_000_00)
        assertThrows(IllegalArgumentException::class.java) { pending.complete("hdfc", "section", "item") }
        assertEquals(50_000_00L, pending.accounts.first().balance)
        assertTrue(pending.history.isEmpty())
    }

    @Test fun externalSavingsAndInvestmentsDebitOnlyTheSource() {
        listOf(ExpenseKind.EXPENSE, ExpenseKind.SAVINGS, ExpenseKind.INVESTMENT).forEach { kind ->
            val completed = tracker(kind).complete("hdfc", "section", "item")
            assertEquals(40_000_00L, completed.accounts.first().balance)
            assertEquals(20_000_00L, completed.accounts.last().balance)
            assertNull(completed.history.single().toAccountId)
        }
    }

    @Test fun trackedBudgetDoesNotDebitBalance() {
        val completed = tracker(ExpenseKind.TRACKING).complete("hdfc", "section", "item")
        assertEquals(50_000_00L, completed.accounts.first().balance)
        assertEquals(0L, completed.history.single().balanceDelta)
    }

    @Test fun completedRowsNoLongerCountAsPending() {
        val pending = tracker(ExpenseKind.EXPENSE)
        assertEquals(10_000_00L, pending.accounts.first().pendingOutflow())
        assertEquals(0L, pending.complete("hdfc", "section", "item").accounts.first().pendingOutflow())
    }

    @Test fun reverseRestoresBothBalancesAndRetainsAuditHistory() {
        val completed = tracker(ExpenseKind.TRANSFER).complete("hdfc", "section", "item", now = 100)
        val reversed = completed.reverse(completed.history.single().id, now = 200)
        assertEquals(50_000_00L, reversed.accounts.first().balance)
        assertEquals(20_000_00L, reversed.accounts.last().balance)
        assertNull(reversed.accounts.first().sections.single().items.single().completedAt)
        assertEquals(2, reversed.history.size)
        assertEquals(200L, reversed.history.first().reversedAt)
        assertEquals(reversed.history.first().id, reversed.history.last().reversalOf)
        assertThrows(IllegalArgumentException::class.java) { reversed.reverse(completed.history.single().id) }
    }

    @Test fun cannotReverseMoneyAlreadySpentByDestination() {
        val completed = tracker(ExpenseKind.TRANSFER).complete("hdfc", "section", "item")
        val spent = completed.adjustBalance("sbi", 0)
        assertThrows(IllegalArgumentException::class.java) { spent.reverse(completed.history.single().id) }
    }

    @Test fun missingAndSameAccountTransfersAreRejected() {
        val pending = tracker(ExpenseKind.TRANSFER)
        val source = pending.accounts.first()
        val missing = pending.copy(accounts = listOf(source))
        assertThrows(IllegalStateException::class.java) { missing.complete("hdfc", "section", "item") }
        val section = source.sections.single()
        val same = pending.copy(accounts = listOf(source.copy(sections = listOf(section.copy(
            items = listOf(section.items.single().copy(toAccountId = "hdfc")),
        )))))
        assertThrows(IllegalArgumentException::class.java) { same.complete("hdfc", "section", "item") }
    }

    @Test fun zeroAndNegativeTransactionsAreRejected() {
        listOf(0L, -100L).forEach { amount ->
            assertThrows(IllegalArgumentException::class.java) {
                tracker(ExpenseKind.CREDIT, amount).complete("hdfc", "section", "item")
            }
        }
    }

    @Test fun moneyUsesExactPaiseAndRejectsInvalidInputs() {
        assertEquals(30L, ExpenseMoney.add(requireNotNull(ExpenseMoney.parse("0.10")), requireNotNull(ExpenseMoney.parse("0.20"))))
        assertEquals("10000.05", ExpenseMoney.text(1_000_005))
        listOf("NaN", "Infinity", "1.2.3", "0.001", "-1", "1e10", "99999999999999999").forEach {
            assertNull(ExpenseMoney.parse(it))
        }
    }

    @Test fun overflowingBalanceDoesNotPostTransaction() {
        val pending = tracker(ExpenseKind.CREDIT).let { model ->
            model.copy(accounts = model.accounts.map { if (it.id == "hdfc") it.copy(balance = ExpenseMoney.MAX_MINOR) else it })
        }
        assertThrows(IllegalArgumentException::class.java) { pending.complete("hdfc", "section", "item") }
        assertTrue(pending.history.isEmpty())
    }
}