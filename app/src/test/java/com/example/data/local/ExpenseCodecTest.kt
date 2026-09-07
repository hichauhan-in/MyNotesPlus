package com.example.data.local

import com.example.domain.model.ExpenseKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseCodecTest {
    private val legacy = """{
        "version":3,
        "accounts":[
            {"id":"hdfc","name":"HDFC","balance":50000,"credit":12500.25,"sections":[
                {"id":"bills","name":"Bills","deduct":true,"items":[{"id":"rent","name":"Rent","amount":10000}]},
                {"id":"budget","name":"Budget","deduct":false,"items":[{"id":"food","name":"Food","amount":500}]}
            ]},
            {"id":"sbi","name":"SBI","balance":20000}
        ],
        "transfers":[{"id":"transfer","name":"Savings","from":"hdfc","to":"sbi","amount":10000}]
    }"""

    @Test fun monthlyCreditIsFoldedIntoBalanceExactlyOnce() {
        val migrated = ExpenseCodec.decode(legacy)
        assertEquals(62_500_25L, migrated.accounts.first().balance)
        val saved = ExpenseCodec.encode(migrated)
        assertFalse(saved.contains("\"credit\":"))
        assertEquals(migrated, ExpenseCodec.decode(saved))
    }

    @Test fun allocationsRemainPendingWithoutChangingTheCurrentBalance() {
        val migrated = ExpenseCodec.decode(legacy)
        val account = migrated.accounts.first()
        assertEquals(62_500_25L, account.balance)
        assertEquals(20_000_00L, account.pendingOutflow())
        assertEquals(ExpenseKind.TRACKING, account.sections[1].kind)
        assertTrue(migrated.history.isEmpty())
    }

    @Test fun savedTransfersBecomePendingItemsOfTheSourceAccount() {
        val migrated = ExpenseCodec.decode(legacy)
        val section = migrated.accounts.first().sections.last()
        assertEquals(ExpenseKind.TRANSFER, section.kind)
        assertEquals("sbi", section.items.single().toAccountId)
        val completed = migrated.complete("hdfc", section.id, "transfer", now = 100)
        assertEquals(52_500_25L, completed.accounts.first().balance)
        assertEquals(30_000_00L, completed.accounts.last().balance)
        assertEquals(completed, ExpenseCodec.decode(ExpenseCodec.encode(completed)))
        val reversed = completed.reverse(completed.history.single().id, now = 200)
        assertEquals(reversed, ExpenseCodec.decode(ExpenseCodec.encode(reversed)))
    }

    @Test fun originalIncomeAndAccountSectionsArePreserved() {
        val migrated = ExpenseCodec.decode("""{"income":123.45,"sections":[
            {"name":"Rent","items":[{"name":"Home","amount":12.34}]},
            {"name":"Accounts","kind":"ACCOUNT","items":[{"name":"Cash","amount":20}]}
        ]}""")
        assertEquals(2, migrated.accounts.size)
        assertEquals(12345L, migrated.accounts[0].balance)
        assertEquals(1234L, migrated.accounts[0].sections.single().items.single().amount)
        assertEquals(2000L, migrated.accounts[1].balance)
    }

    @Test fun orphanedLegacyTransfersAreRecoveredWithoutInventingMoney() {
        val migrated = ExpenseCodec.decode("""{"version":3,"accounts":[],"transfers":[
            {"id":"old","from":"deleted","to":"missing","name":"Saving","amount":10}
        ]}""")
        assertEquals(0L, migrated.accounts.single().balance)
        assertEquals("missing", migrated.accounts.single().sections.single().items.single().toAccountId)
    }

    @Test fun corruptedAndFutureContentIsNotSilentlyReplacedWithAnEmptyTracker() {
        assertThrows(Exception::class.java) { ExpenseCodec.decode("broken JSON") }
        assertThrows(IllegalArgumentException::class.java) { ExpenseCodec.decode("""{"version":6,"accounts":[]}""") }
        assertThrows(IllegalArgumentException::class.java) { ExpenseCodec.decode("""{"version":2,"accounts":[{"id":"same"},{"id":"same"}]}""") }
    }

    @Test fun versionFourBalancesAndHistorySurviveReusableActionMigration() {
        val posted = ExpenseCodec.decode(legacy).complete("hdfc", "bills", "rent", now = 100)
        val previous = JSONObject(ExpenseCodec.encode(posted)).put("version", 4).toString()
        val migrated = ExpenseCodec.decode(previous)
        assertEquals(posted, migrated)
        assertEquals(5, JSONObject(ExpenseCodec.encode(migrated)).getInt("version"))
        assertEquals(42_500_25L, migrated.complete("hdfc", "bills", "rent", now = 200).accounts.first().balance)
    }
}