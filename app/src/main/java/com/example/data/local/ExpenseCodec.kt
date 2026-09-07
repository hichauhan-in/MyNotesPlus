package com.example.data.local

import com.example.domain.model.ExpAccount
import com.example.domain.model.ExpItem
import com.example.domain.model.ExpSection
import com.example.domain.model.ExpTransaction
import com.example.domain.model.ExpenseKind
import com.example.domain.model.ExpenseModel
import com.example.domain.model.ExpenseMoney
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal object ExpenseCodec {
    const val VERSION = 5

    fun decode(content: String): ExpenseModel {
        if (content.isBlank()) return ExpenseModel()
        val root = JSONObject(content)
        val version = root.optInt("version", 1)
        require(version in 1..VERSION) { "This tracker needs a newer app version" }
        val model = if (version >= 2) {
            val accounts = root.getJSONArray("accounts").objects().map { account ->
                val balance = if (version >= 4) account.getLong("balanceMinor") else ExpenseMoney.add(
                    account.decimal("balance"), account.decimal("credit"),
                )
                ExpAccount(
                    id = account.identifier(), name = account.optString("name"),
                    iconKey = account.optString("icon", "account"), colorArgb = account.optInt("color"),
                    balance = balance, tags = account.optJSONArray("tags").strings(),
                    sections = account.optJSONArray("sections").objects().map { section ->
                        ExpSection(
                            id = section.identifier(), name = section.optString("name"),
                            iconKey = section.optString("icon", "other"),
                            kind = if (version >= 4) ExpenseKind.valueOf(section.getString("kind")) else legacyKind(section),
                            items = section.optJSONArray("items").objects().map { item ->
                                ExpItem(
                                    id = item.identifier(), name = item.optString("name"),
                                    amount = if (version >= 4) item.getLong("amountMinor") else item.decimal("amount"),
                                    toAccountId = item.nullableString("to"), completedAt = item.nullableLong("completedAt"),
                                    receiptToken = item.nullableString("receipt"), receiptDate = item.nullableString("receiptDate"),
                                )
                            },
                        )
                    },
                )
            }
            if (version >= 4) ExpenseModel(accounts, root.optJSONArray("history").objects().map(::readTransaction))
            else migrateTransfers(accounts, root.optJSONArray("transfers"))
        } else migrateOriginal(root)
        validate(model)
        return model
    }

    fun encode(model: ExpenseModel): String {
        validate(model)
        val accounts = model.accounts.map { account ->
            JSONObject().put("id", account.id).put("name", account.name).put("icon", account.iconKey)
                .put("color", account.colorArgb).put("balanceMinor", account.balance)
                .put("tags", JSONArray(account.tags)).put("sections", JSONArray(account.sections.map { section ->
                    JSONObject().put("id", section.id).put("name", section.name).put("icon", section.iconKey)
                        .put("kind", section.kind.name).put("items", JSONArray(section.items.map { item ->
                            JSONObject().put("id", item.id).put("name", item.name).put("amountMinor", item.amount)
                                .put("to", item.toAccountId).put("completedAt", item.completedAt)
                                .put("receipt", item.receiptToken).put("receiptDate", item.receiptDate)
                        }))
                }))
        }
        return JSONObject().put("version", VERSION).put("accounts", JSONArray(accounts))
            .put("history", JSONArray(model.history.map(::writeTransaction))).toString()
    }

    private fun migrateTransfers(existing: List<ExpAccount>, transfers: JSONArray?): ExpenseModel {
        val accounts = existing.toMutableList()
        transfers.objects().groupBy { it.optString("from").ifBlank { "recovered-transfers" } }.forEach { (sourceId, rows) ->
            var index = accounts.indexOfFirst { it.id == sourceId }
            if (index < 0) {
                accounts.add(ExpAccount(id = sourceId, name = "Recovered account"))
                index = accounts.lastIndex
            }
            val account = accounts[index]
            val section = ExpSection(
                name = "Bank transfers", iconKey = "transfer", kind = ExpenseKind.TRANSFER,
                items = rows.map { row -> ExpItem(
                    id = row.identifier(), name = row.optString("name").ifBlank { "Transfer" },
                    amount = row.decimal("amount"), toAccountId = row.nullableString("to"),
                ) },
            )
            accounts[index] = account.copy(sections = account.sections + section)
        }
        return ExpenseModel(accounts)
    }

    private fun migrateOriginal(root: JSONObject): ExpenseModel {
        val accounts = mutableListOf<ExpAccount>()
        val sections = mutableListOf<ExpSection>()
        if (root.has("sections")) {
            root.getJSONArray("sections").objects().forEach { section ->
                val items = section.optJSONArray("items").objects().map { item ->
                    ExpItem(item.identifier(), item.optString("name"), item.decimal("amount"))
                }
                if (section.optString("kind", "ALLOCATION") == "ACCOUNT") {
                    accounts += items.map { ExpAccount(name = it.name.ifBlank { "Account" }, balance = it.amount) }
                } else {
                    sections += ExpSection(section.identifier(), section.optString("name"), section.optString("icon", "other"), legacyKind(section), items)
                }
            }
        } else {
            root.optJSONArray("entries").objects().groupBy { it.optString("cat", "EXPENSE") }.forEach { (category, rows) ->
                val kind = when (category) {
                    "SAVINGS" -> ExpenseKind.SAVINGS
                    "INVESTMENT" -> ExpenseKind.INVESTMENT
                    else -> ExpenseKind.EXPENSE
                }
                sections += ExpSection(
                    name = kind.label, iconKey = if (kind == ExpenseKind.INVESTMENT) "invest" else kind.name.lowercase(), kind = kind,
                    items = rows.map { ExpItem(it.identifier(), it.optString("name"), it.decimal("amount")) },
                )
            }
            accounts += root.optJSONArray("accounts").objects().map { account ->
                ExpAccount(id = account.identifier(), name = account.optString("name").ifBlank { "Account" }, balance = account.decimal("balance"))
            }
        }
        val income = root.decimal("income")
        if (income != 0L || sections.isNotEmpty()) accounts.add(0, ExpAccount(name = "Main", balance = income, sections = sections))
        return ExpenseModel(accounts)
    }

    private fun legacyKind(section: JSONObject): ExpenseKind = when {
        !section.optBoolean("deduct", true) -> ExpenseKind.TRACKING
        section.optString("icon") == "savings" -> ExpenseKind.SAVINGS
        section.optString("icon") == "invest" -> ExpenseKind.INVESTMENT
        else -> ExpenseKind.EXPENSE
    }

    private fun readTransaction(record: JSONObject) = ExpTransaction(
        id = record.getString("id"), name = record.getString("name"), sectionName = record.getString("sectionName"),
        kind = ExpenseKind.valueOf(record.getString("kind")), amount = record.getLong("amountMinor"),
        accountId = record.getString("accountId"), accountName = record.getString("accountName"),
        balanceDelta = record.getLong("balanceDelta"), balanceAfter = record.getLong("balanceAfter"),
        toAccountId = record.nullableString("toAccountId"), toAccountName = record.nullableString("toAccountName"),
        toBalanceAfter = record.nullableLong("toBalanceAfter"), sectionId = record.nullableString("sectionId"),
        itemId = record.nullableString("itemId"), completedAt = record.getLong("completedAt"),
        reversedAt = record.nullableLong("reversedAt"), reversalOf = record.nullableString("reversalOf"),
        receiptToken = record.nullableString("receipt"), receiptDate = record.nullableString("receiptDate"),
    )

    private fun writeTransaction(record: ExpTransaction) = JSONObject()
        .put("id", record.id).put("name", record.name).put("sectionName", record.sectionName).put("kind", record.kind.name)
        .put("amountMinor", record.amount).put("accountId", record.accountId).put("accountName", record.accountName)
        .put("balanceDelta", record.balanceDelta).put("balanceAfter", record.balanceAfter)
        .put("toAccountId", record.toAccountId).put("toAccountName", record.toAccountName).put("toBalanceAfter", record.toBalanceAfter)
        .put("sectionId", record.sectionId).put("itemId", record.itemId).put("completedAt", record.completedAt)
        .put("reversedAt", record.reversedAt).put("reversalOf", record.reversalOf)
        .put("receipt", record.receiptToken).put("receiptDate", record.receiptDate)

    private fun validate(model: ExpenseModel) {
        fun unique(ids: List<String>) {
            require(ids.all { it.isNotBlank() } && ids.distinct().size == ids.size) { "Duplicate or missing tracker identifiers" }
        }
        unique(model.accounts.map { it.id })
        unique(model.history.map { it.id })
        model.accounts.forEach { account ->
            require(account.balance in -ExpenseMoney.MAX_MINOR..ExpenseMoney.MAX_MINOR) { "Invalid account balance" }
            unique(account.sections.map { it.id })
            account.sections.forEach { section ->
                require(section.kind != ExpenseKind.ADJUSTMENT) { "Invalid section type" }
                unique(section.items.map { it.id })
                require(section.items.all { it.amount in 0..ExpenseMoney.MAX_MINOR }) { "Invalid transaction amount" }
            }
        }
    }

    private fun JSONObject.identifier(): String = nullableString("id")?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
    private fun JSONObject.decimal(key: String): Long = ExpenseMoney.fromDecimal(optString(key, "0"))
    private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else getLong(key)
    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }
}