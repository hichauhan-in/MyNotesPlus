package com.example.data.export

import com.example.data.local.ExpenseCodec
import com.example.domain.model.ExpTransaction
import com.example.domain.model.ExpenseKind
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object ExpenseReport {
    fun lines(content: String): List<String> {
        val model = ExpenseCodec.decode(content)
        val names = model.accounts.associate { it.id to it.name }
        return buildList {
            if (model.accounts.isEmpty()) add("No accounts")
            model.accounts.forEach { account ->
                add("Account: ${account.name}")
                add("Current balance: ${money(account.balance)}")
                if (account.tags.isNotEmpty()) add("Tags: ${account.tags.joinToString(", ")}")
                add("Pending outflow: ${money(account.pendingOutflow())}")
                account.sections.forEach { section ->
                    add("")
                    add("${section.name} (${section.kind.label})")
                    section.items.forEach { item ->
                        val state = item.completedAt?.let { "Completed ${date(it)}" } ?: "Pending"
                        val destination = if (section.kind == ExpenseKind.TRANSFER) " / To: ${names[item.toAccountId] ?: "Unavailable account"}" else ""
                        add("  [$state] ${item.name}: ${money(item.amount)}$destination")
                    }
                }
                add("")
            }
            if (model.history.isNotEmpty()) {
                add("Activity")
                model.history.forEach { record ->
                    add("${date(record.completedAt)} / ${status(record)} / ${record.name}")
                    add("  ${record.sectionName} (${record.kind.label}): ${money(record.amount)}")
                    add("  ${record.accountName}: change ${signedMoney(record.balanceDelta)}, balance after ${money(record.balanceAfter)}")
                    record.toBalanceAfter?.let { balance ->
                        val delta = if (record.reversalOf == null) record.amount else -record.amount
                        add("  ${record.toAccountName}: change ${signedMoney(delta)}, balance after ${money(balance)}")
                    }
                    record.reversedAt?.let { add("  Reversed at ${date(it)}") }
                    add("  Record: ${record.id}")
                    record.reversalOf?.let { add("  Reverses: $it") }
                }
            }
        }
    }

    fun markdown(content: String): String = lines(content).flatMap { it.lines() }.joinToString("\n") { "    $it" }

    fun html(content: String): String = buildString {
        append("<section><h2>Expense ledger</h2><pre style=\"white-space:pre-wrap;overflow-wrap:anywhere\">")
        append(escape(lines(content).joinToString("\n")))
        append("</pre></section>")
    }

    private fun money(amount: Long): String = "INR " + BigDecimal.valueOf(amount, 2).setScale(2).toPlainString()
    private fun signedMoney(amount: Long): String = (if (amount > 0) "+" else if (amount < 0) "-" else "") + money(kotlin.math.abs(amount))
    private fun date(timestamp: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT).format(Date(timestamp))
    private fun status(record: ExpTransaction): String = when {
        record.reversalOf != null -> "Reversal"
        record.reversedAt != null -> "Reversed"
        else -> "Completed"
    }
    private fun escape(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")
}