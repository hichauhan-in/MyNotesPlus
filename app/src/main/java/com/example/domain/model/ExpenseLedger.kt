package com.example.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

internal object ExpenseMoney {
    const val MAX_MINOR = 99_999_999_999_999L
    val inputPattern = Regex("[0-9]{0,12}(\\.[0-9]{0,2})?")

    fun parse(text: String): Long? {
        if (!inputPattern.matches(text) || text.isBlank() || text == ".") return null
        return runCatching { BigDecimal(text).movePointRight(2).longValueExact() }
            .getOrNull()?.takeIf { it in 0..MAX_MINOR }
    }

    fun fromDecimal(text: String): Long = BigDecimal(text)
        .setScale(2, RoundingMode.HALF_EVEN).movePointRight(2).longValueExact()
        .also { require(it in -MAX_MINOR..MAX_MINOR) { "Amount is too large" } }

    fun text(amount: Long): String = BigDecimal.valueOf(amount, 2).stripTrailingZeros().toPlainString()

    fun add(balance: Long, delta: Long): Long = Math.addExact(balance, delta)
        .also { require(it in -MAX_MINOR..MAX_MINOR) { "Balance is too large" } }
}

internal enum class ExpenseKind(val label: String, val direction: Int) {
    EXPENSE("Expense", -1),
    CREDIT("Credit", 1),
    SAVINGS("Savings", -1),
    INVESTMENT("Investment", -1),
    TRANSFER("Transfer", -1),
    TRACKING("Budget only", 0),
    ADJUSTMENT("Balance adjustment", 0),
}

internal data class ExpItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val amount: Long = 0,
    val toAccountId: String? = null,
    val completedAt: Long? = null,
)

internal data class ExpSection(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val iconKey: String,
    val kind: ExpenseKind,
    val items: List<ExpItem> = emptyList(),
)

internal data class ExpAccount(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val iconKey: String = "account",
    val colorArgb: Int = 0,
    val balance: Long = 0,
    val tags: List<String> = emptyList(),
    val sections: List<ExpSection> = emptyList(),
) {
    fun pendingOutflow(): Long = sections.filter { it.kind.direction < 0 }
        .sumOf { section -> section.items.filter { it.completedAt == null }.sumOf { it.amount } }
}

internal data class ExpTransaction(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sectionName: String,
    val kind: ExpenseKind,
    val amount: Long,
    val accountId: String,
    val accountName: String,
    val balanceDelta: Long,
    val balanceAfter: Long,
    val toAccountId: String? = null,
    val toAccountName: String? = null,
    val toBalanceAfter: Long? = null,
    val sectionId: String? = null,
    val itemId: String? = null,
    val completedAt: Long,
    val reversedAt: Long? = null,
    val reversalOf: String? = null,
)

internal data class ExpenseModel(
    val accounts: List<ExpAccount> = emptyList(),
    val history: List<ExpTransaction> = emptyList(),
) {
    fun complete(accountId: String, sectionId: String, itemId: String, now: Long = System.currentTimeMillis()): ExpenseModel {
        val account = accounts.singleOrNull { it.id == accountId } ?: error("Account no longer exists")
        val section = account.sections.singleOrNull { it.id == sectionId } ?: error("Section no longer exists")
        val item = section.items.singleOrNull { it.id == itemId } ?: error("Transaction no longer exists")
        require(item.completedAt == null) { "This transaction is already completed" }
        require(item.name.isNotBlank()) { "Add a transaction name" }
        require(item.amount in 1..ExpenseMoney.MAX_MINOR) { "Enter a valid amount greater than zero" }
        require(section.kind != ExpenseKind.ADJUSTMENT) { "Use the balance adjustment control" }
        val target = if (section.kind == ExpenseKind.TRANSFER) {
            require(item.toAccountId != accountId) { "Choose a different destination account" }
            accounts.singleOrNull { it.id == item.toAccountId } ?: error("Choose a destination account")
        } else null
        val delta = item.amount * section.kind.direction
        require(delta >= 0 || account.balance >= item.amount) { "Not enough balance in ${account.name}" }
        val balanceAfter = ExpenseMoney.add(account.balance, delta)
        val targetBalanceAfter = target?.let { ExpenseMoney.add(it.balance, item.amount) }
        val transaction = ExpTransaction(
            name = item.name.trim(), sectionName = section.name, kind = section.kind,
            amount = item.amount, accountId = account.id, accountName = account.name,
            balanceDelta = delta, balanceAfter = balanceAfter,
            toAccountId = target?.id, toAccountName = target?.name, toBalanceAfter = targetBalanceAfter,
            sectionId = section.id, itemId = item.id, completedAt = now,
        )
        return copy(
            accounts = accounts.map { current ->
                when (current.id) {
                    account.id -> current.copy(
                        balance = balanceAfter,
                        sections = current.sections.map { currentSection ->
                            if (currentSection.id != sectionId) currentSection else currentSection.copy(
                                items = currentSection.items.map { if (it.id == itemId) it.copy(completedAt = now) else it },
                            )
                        },
                    )
                    target?.id -> current.copy(balance = requireNotNull(targetBalanceAfter))
                    else -> current
                }
            },
            history = history + transaction,
        )
    }

    fun reverse(transactionId: String, now: Long = System.currentTimeMillis()): ExpenseModel {
        val transaction = history.singleOrNull { it.id == transactionId } ?: error("Transaction no longer exists")
        require(transaction.reversedAt == null && transaction.reversalOf == null) { "This transaction is already reversed" }
        val account = accounts.singleOrNull { it.id == transaction.accountId } ?: error("Restore the source account before reversing")
        val target = transaction.toAccountId?.let { targetId ->
            accounts.singleOrNull { it.id == targetId } ?: error("Restore the destination account before reversing")
        }
        val balanceAfter = ExpenseMoney.add(account.balance, -transaction.balanceDelta)
        require(transaction.balanceDelta <= 0 || balanceAfter >= 0) { "Not enough balance to reverse this credit" }
        val targetBalanceAfter = target?.let {
            require(it.balance >= transaction.amount) { "Not enough balance in ${it.name} to reverse this transfer" }
            ExpenseMoney.add(it.balance, -transaction.amount)
        }
        val reversal = transaction.copy(
            id = UUID.randomUUID().toString(), balanceDelta = -transaction.balanceDelta,
            balanceAfter = balanceAfter, toBalanceAfter = targetBalanceAfter,
            completedAt = now, reversedAt = null, reversalOf = transaction.id,
        )
        return copy(
            accounts = accounts.map { current ->
                when (current.id) {
                    account.id -> current.copy(
                        balance = balanceAfter,
                        sections = current.sections.map { section ->
                            if (section.id != transaction.sectionId) section else section.copy(
                                items = section.items.map { item ->
                                    if (item.id == transaction.itemId) item.copy(completedAt = null) else item
                                },
                            )
                        },
                    )
                    target?.id -> current.copy(balance = requireNotNull(targetBalanceAfter))
                    else -> current
                }
            },
            history = history.map { if (it.id == transactionId) it.copy(reversedAt = now) else it } + reversal,
        )
    }

    fun adjustBalance(accountId: String, balance: Long, now: Long = System.currentTimeMillis()): ExpenseModel {
        require(balance in 0..ExpenseMoney.MAX_MINOR) { "Enter a valid balance" }
        val account = accounts.singleOrNull { it.id == accountId } ?: error("Account no longer exists")
        if (balance == account.balance) return this
        val delta = Math.subtractExact(balance, account.balance)
        val transaction = ExpTransaction(
            name = if (history.none { it.accountId == accountId || it.toAccountId == accountId }) "Opening balance" else "Balance correction",
            sectionName = "Balance", kind = ExpenseKind.ADJUSTMENT, amount = kotlin.math.abs(delta),
            accountId = accountId, accountName = account.name, balanceDelta = delta,
            balanceAfter = balance, completedAt = now,
        )
        return copy(
            accounts = accounts.map { if (it.id == accountId) it.copy(balance = balance) else it },
            history = history + transaction,
        )
    }
}