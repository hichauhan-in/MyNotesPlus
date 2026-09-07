package com.example.ui.editor

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalMall
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalNeuColors
import com.example.ui.theme.neumorphicRaised
import com.example.ui.util.responsiveHorizontalPadding
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

// ---- Model ---------------------------------------------------------------------

private data class ExpItem(val id: String, val name: String, val amount: Double)

private data class ExpSection(
    val id: String,
    val name: String,
    val iconKey: String,
    /** When true, this section's total is subtracted from the account's available balance. */
    val deductFromBalance: Boolean,
    val items: List<ExpItem>,
)

private data class ExpAccount(
    val id: String,
    val name: String,
    val iconKey: String,
    val colorArgb: Int,
    val balance: Double,
    val tags: List<String>,
    val sections: List<ExpSection>,
    /** A monthly credit (e.g. salary) the user tops up; adds on top of [balance] in the total. */
    val credit: Double = 0.0,
)

/** A saved, repeatable money move between two accounts. Tapping "Transfer" applies it. */
private data class ExpTransfer(
    val id: String,
    val name: String,
    val fromId: String,
    val toId: String,
    val amount: Double,
)

private data class ExpenseModel(
    val accounts: List<ExpAccount>,
    val transfers: List<ExpTransfer> = emptyList(),
)

private fun newItem() = ExpItem(UUID.randomUUID().toString(), "", 0.0)

private fun ExpAccount.deductible(): Double =
    sections.filter { it.deductFromBalance }.sumOf { s -> s.items.sumOf { it.amount } }

private fun parseExpense(content: String): ExpenseModel = runCatching {
    if (content.isBlank()) return@runCatching ExpenseModel(emptyList())
    val obj = JSONObject(content)
    if (obj.optInt("version", 1) >= 2 && obj.has("accounts")) {
        val accounts = parseAccounts(obj.getJSONArray("accounts")).accounts
        ExpenseModel(accounts, parseTransfers(obj.optJSONArray("transfers")))
    } else {
        migrateOld(obj)
    }
}.getOrDefault(ExpenseModel(emptyList()))

private fun parseTransfers(arr: JSONArray?): List<ExpTransfer> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        ExpTransfer(
            o.optString("id", UUID.randomUUID().toString()),
            o.optString("name"),
            o.optString("from"),
            o.optString("to"),
            o.optDouble("amount", 0.0),
        )
    }
}

private fun parseAccounts(arr: JSONArray): ExpenseModel {
    val accounts = (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val tags = o.optJSONArray("tags")?.let { t ->
            (0 until t.length()).map { t.optString(it) }.filter { it.isNotBlank() }
        } ?: emptyList()
        val secArr = o.optJSONArray("sections") ?: JSONArray()
        val sections = (0 until secArr.length()).map { j ->
            val so = secArr.getJSONObject(j)
            val itemsArr = so.optJSONArray("items") ?: JSONArray()
            val items = (0 until itemsArr.length()).map { k ->
                val io = itemsArr.getJSONObject(k)
                ExpItem(io.optString("id", UUID.randomUUID().toString()), io.optString("name"), io.optDouble("amount", 0.0))
            }
            ExpSection(
                so.optString("id", UUID.randomUUID().toString()),
                so.optString("name"),
                so.optString("icon", "other"),
                so.optBoolean("deduct", true),
                items,
            )
        }
        ExpAccount(
            o.optString("id", UUID.randomUUID().toString()),
            o.optString("name"),
            o.optString("icon", "account"),
            o.optInt("color", 0),
            o.optDouble("balance", 0.0),
            tags,
            sections,
            o.optDouble("credit", 0.0),
        )
    }
    return ExpenseModel(accounts)
}

/** Migrates the older single-income model (or the legacy entries model) into one "Main" account. */
private fun migrateOld(obj: JSONObject): ExpenseModel {
    val income = obj.optDouble("income", 0.0)
    val allocationSections = mutableListOf<ExpSection>()
    val accountItems = mutableListOf<ExpItem>()

    if (obj.has("sections")) {
        val secArr = obj.getJSONArray("sections")
        for (i in 0 until secArr.length()) {
            val o = secArr.getJSONObject(i)
            val itemsArr = o.optJSONArray("items") ?: JSONArray()
            val items = (0 until itemsArr.length()).map { j ->
                val it2 = itemsArr.getJSONObject(j)
                ExpItem(it2.optString("id", UUID.randomUUID().toString()), it2.optString("name"), it2.optDouble("amount", 0.0))
            }
            if (o.optString("kind", "ALLOCATION") == "ACCOUNT") {
                accountItems += items
            } else {
                allocationSections += ExpSection(
                    o.optString("id", UUID.randomUUID().toString()),
                    o.optString("name"), o.optString("icon", "other"), true, items,
                )
            }
        }
    } else {
        val entArr = obj.optJSONArray("entries") ?: JSONArray()
        val entries = (0 until entArr.length()).map { entArr.getJSONObject(it) }
        fun itemsFor(cat: String) = entries.filter { it.optString("cat", "EXPENSE") == cat }.map {
            ExpItem(it.optString("id", UUID.randomUUID().toString()), it.optString("name"), it.optDouble("amount", 0.0))
        }
        itemsFor("EXPENSE").takeIf { it.isNotEmpty() }?.let {
            allocationSections += ExpSection(UUID.randomUUID().toString(), "Expenses", "expense", true, it)
        }
        itemsFor("SAVINGS").takeIf { it.isNotEmpty() }?.let {
            allocationSections += ExpSection(UUID.randomUUID().toString(), "Savings", "savings", true, it)
        }
        itemsFor("INVESTMENT").takeIf { it.isNotEmpty() }?.let {
            allocationSections += ExpSection(UUID.randomUUID().toString(), "Investments", "invest", true, it)
        }
        val accArr = obj.optJSONArray("accounts") ?: JSONArray()
        for (i in 0 until accArr.length()) {
            val o = accArr.getJSONObject(i)
            accountItems += ExpItem(o.optString("id", UUID.randomUUID().toString()), o.optString("name"), o.optDouble("balance", 0.0))
        }
    }

    val accounts = mutableListOf<ExpAccount>()
    if (income != 0.0 || allocationSections.isNotEmpty()) {
        accounts += ExpAccount(UUID.randomUUID().toString(), "Main", "account", 0, income, emptyList(), allocationSections)
    }
    accountItems.forEach {
        accounts += ExpAccount(UUID.randomUUID().toString(), it.name.ifBlank { "Account" }, "account", 0, it.amount, emptyList(), emptyList())
    }
    return ExpenseModel(accounts)
}

private fun serializeExpense(model: ExpenseModel): String {
    val accArr = JSONArray()
    model.accounts.forEach { a ->
        val secArr = JSONArray()
        a.sections.forEach { s ->
            val itemsArr = JSONArray()
            s.items.forEach { itemsArr.put(JSONObject().put("id", it.id).put("name", it.name).put("amount", it.amount)) }
            secArr.put(
                JSONObject()
                    .put("id", s.id).put("name", s.name).put("icon", s.iconKey)
                    .put("deduct", s.deductFromBalance).put("items", itemsArr),
            )
        }
        val tagsArr = JSONArray()
        a.tags.forEach { tagsArr.put(it) }
        accArr.put(
            JSONObject()
                .put("id", a.id).put("name", a.name).put("icon", a.iconKey).put("color", a.colorArgb)
                .put("balance", a.balance).put("credit", a.credit).put("tags", tagsArr).put("sections", secArr),
        )
    }
    val transfersArr = JSONArray()
    model.transfers.forEach { t ->
        transfersArr.put(
            JSONObject().put("id", t.id).put("name", t.name)
                .put("from", t.fromId).put("to", t.toId).put("amount", t.amount),
        )
    }
    return JSONObject().put("version", 3).put("accounts", accArr).put("transfers", transfersArr).toString()
}

private val sectionIconOptions: List<Pair<String, ImageVector>> = listOf(
    "account" to Icons.Rounded.AccountBalance,
    "salary" to Icons.Rounded.Payments,
    "expense" to Icons.Rounded.ShoppingCart,
    "savings" to Icons.Rounded.Savings,
    "invest" to Icons.Rounded.TrendingUp,
    "home" to Icons.Rounded.Home,
    "bills" to Icons.Rounded.ReceiptLong,
    "food" to Icons.Rounded.Restaurant,
    "car" to Icons.Rounded.DirectionsCar,
    "health" to Icons.Rounded.FavoriteBorder,
    "education" to Icons.Rounded.School,
    "travel" to Icons.Rounded.Flight,
    "shopping" to Icons.Rounded.LocalMall,
    "fun" to Icons.Rounded.Movie,
    "gift" to Icons.Rounded.CardGiftcard,
    "phone" to Icons.Rounded.Smartphone,
    "pet" to Icons.Rounded.Pets,
    "other" to Icons.Rounded.Category,
)

private fun sectionIcon(key: String): ImageVector =
    sectionIconOptions.firstOrNull { it.first == key }?.second ?: Icons.Rounded.Category

private val accountColorChoices = listOf(
    0,
    0xFF7E57C2.toInt(),
    0xFF5C6BC0.toInt(),
    0xFF42A5F5.toInt(),
    0xFF26A69A.toInt(),
    0xFF66BB6A.toInt(),
    0xFFFFCA28.toInt(),
    0xFFEF6C00.toInt(),
    0xFFEC407A.toInt(),
    0xFFEF5350.toInt(),
)

private val presetTags = listOf("Salary", "Primary", "Savings", "Emergency", "Joint", "Business", "Bills", "Cash")

private val moneyFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 2 }
private fun formatMoney(v: Double): String = "₹" + moneyFormat.format(v)
private fun plainAmount(v: Double): String =
    if (v == 0.0) "" else if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

// ---- Editor --------------------------------------------------------------------

/**
 * A multi-account money tracker: swipeable account tabs, each with its own balance, tags and
 * user-created sections (savings / investments / bills …). Send money in or out, transfer between
 * your own accounts, and mark which sections spend from the balance. Nothing is added by default -
 * the user builds their accounts and sections.
 */
@Composable
internal fun ExpenseEditor(
    seedKey: String,
    title: String,
    content: String,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    meta: @Composable () -> Unit = {},
) {
    var model by remember { mutableStateOf(parseExpense(content)) }
    LaunchedEffect(seedKey) { model = parseExpense(content) }
    var selected by remember { mutableStateOf(0) }
    var showAddAccount by remember { mutableStateOf(false) }
    var editAccountId by remember { mutableStateOf<String?>(null) }
    var showSend by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf(false) }
    var showAddSection by remember { mutableStateOf(false) }
    val readOnly = LocalReadOnly.current
    val context = LocalContext.current

    fun update(m: ExpenseModel) {
        model = m
        onContentChange(serializeExpense(m))
    }
    fun updateAccount(id: String, transform: (ExpAccount) -> ExpAccount) =
        update(model.copy(accounts = model.accounts.map { if (it.id == id) transform(it) else it }))
    fun updateSection(accId: String, secId: String, transform: (ExpSection) -> ExpSection) =
        updateAccount(accId) { a -> a.copy(sections = a.sections.map { if (it.id == secId) transform(it) else it }) }
    fun updateTransfer(id: String, transform: (ExpTransfer) -> ExpTransfer) =
        update(model.copy(transfers = model.transfers.map { if (it.id == id) transform(it) else it }))
    fun addTransfer() {
        val from = model.accounts.getOrNull(0)?.id.orEmpty()
        val to = model.accounts.getOrNull(1)?.id ?: model.accounts.getOrNull(0)?.id.orEmpty()
        update(model.copy(transfers = model.transfers + ExpTransfer(UUID.randomUUID().toString(), "", from, to, 0.0)))
    }
    fun removeTransfer(id: String) = update(model.copy(transfers = model.transfers.filterNot { it.id == id }))
    fun executeTransfer(t: ExpTransfer) {
        val from = model.accounts.firstOrNull { it.id == t.fromId }
        val to = model.accounts.firstOrNull { it.id == t.toId }
        when {
            from == null || to == null || from.id == to.id ->
                Toast.makeText(context, "Pick two different accounts", Toast.LENGTH_SHORT).show()
            t.amount <= 0.0 ->
                Toast.makeText(context, "Enter an amount to transfer", Toast.LENGTH_SHORT).show()
            else -> {
                update(
                    model.copy(
                        accounts = model.accounts.map {
                            when (it.id) {
                                from.id -> it.copy(balance = it.balance - t.amount)
                                to.id -> it.copy(balance = it.balance + t.amount)
                                else -> it
                            }
                        },
                    ),
                )
                Toast.makeText(
                    context,
                    "Transferred ${formatMoney(t.amount)} · ${from.name.ifBlank { "From" }} → ${to.name.ifBlank { "To" }}",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    val accounts = model.accounts
    val selectedIndex = selected.coerceIn(0, maxOf(0, accounts.size - 1))
    val account = accounts.getOrNull(selectedIndex)
    val defaultAccent = MaterialTheme.colorScheme.primary
    val accentFor: (ExpAccount) -> Color = { a -> a.colorArgb.takeIf { it != 0 }?.let { Color(it) } ?: defaultAccent }

    val sidePadding = responsiveHorizontalPadding(compact = 16.dp)
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .imePadding(),
        contentPadding = PaddingValues(start = sidePadding, end = sidePadding, bottom = 96.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Box {
                if (title.isEmpty()) {
                    Text(
                        text = "e.g. My money",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
                BasicTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    readOnly = readOnly,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(12.dp))
            meta()
            Spacer(Modifier.height(16.dp))
            AccountTabs(
                accounts = accounts,
                selectedIndex = selectedIndex,
                accentFor = accentFor,
                showAdd = !readOnly,
                onSelect = { selected = it },
                onAdd = { showAddAccount = true },
            )
            Spacer(Modifier.height(16.dp))
        }

        if (account == null) {
            item {
                EmptyAccounts(readOnly = readOnly, onAdd = { showAddAccount = true })
                Spacer(Modifier.height(24.dp))
            }
        } else {
            item(key = "header_${account.id}") {
                AccountHeaderCard(
                    account = account,
                    accent = accentFor(account),
                    readOnly = readOnly,
                    canTransfer = accounts.size > 1,
                    onBalance = { updateAccount(account.id) { a -> a.copy(balance = it) } },
                    onCredit = { updateAccount(account.id) { a -> a.copy(credit = it) } },
                    onSend = { showSend = true },
                    onTransfer = { showTransfer = true },
                    onEdit = { editAccountId = account.id },
                    onDelete = {
                        update(model.copy(accounts = model.accounts.filterNot { it.id == account.id }))
                        selected = 0
                    },
                    onRenameText = { newName -> updateAccount(account.id) { it.copy(name = newName) } },
                )
                Spacer(Modifier.height(14.dp))
            }
            items(account.sections, key = { it.id }) { section ->
                SectionCard(
                    section = section,
                    accent = accentFor(account),
                    onRename = { v -> updateSection(account.id, section.id) { it.copy(name = v) } },
                    onToggleDeduct = { updateSection(account.id, section.id) { it.copy(deductFromBalance = !it.deductFromBalance) } },
                    onAddItem = { updateSection(account.id, section.id) { it.copy(items = it.items + newItem()) } },
                    onItemName = { itemId, v ->
                        updateSection(account.id, section.id) { s -> s.copy(items = s.items.map { if (it.id == itemId) it.copy(name = v) else it }) }
                    },
                    onItemAmount = { itemId, v ->
                        updateSection(account.id, section.id) { s -> s.copy(items = s.items.map { if (it.id == itemId) it.copy(amount = v) else it }) }
                    },
                    onDeleteItem = { itemId ->
                        updateSection(account.id, section.id) { s -> s.copy(items = s.items.filterNot { it.id == itemId }) }
                    },
                    onRemoveSection = {
                        updateAccount(account.id) { a -> a.copy(sections = a.sections.filterNot { it.id == section.id }) }
                    },
                )
                Spacer(Modifier.height(14.dp))
            }
            if (!readOnly) {
                item {
                    AddSectionButton(onClick = { showAddSection = true })
                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        // Bank transfers are note-level (they move money between accounts), so they live at the
        // bottom, below whichever account is open. Needs at least two accounts to be useful.
        if (accounts.size >= 2 && (model.transfers.isNotEmpty() || !readOnly)) {
            item(key = "bank_transfers") {
                BankTransfersCard(
                    accounts = accounts,
                    transfers = model.transfers,
                    accent = account?.let(accentFor) ?: defaultAccent,
                    readOnly = readOnly,
                    onAdd = { addTransfer() },
                    onName = { id, v -> updateTransfer(id) { it.copy(name = v) } },
                    onFrom = { id, accId -> updateTransfer(id) { it.copy(fromId = accId) } },
                    onTo = { id, accId -> updateTransfer(id) { it.copy(toId = accId) } },
                    onAmount = { id, v -> updateTransfer(id) { it.copy(amount = v) } },
                    onExecute = { executeTransfer(it) },
                    onRemove = { removeTransfer(it) },
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showAddAccount) {
        AccountDialog(
            title = "New account",
            confirmLabel = "Add",
            onConfirm = { name, icon, color, tags ->
                update(model.copy(accounts = model.accounts + ExpAccount(UUID.randomUUID().toString(), name, icon, color, 0.0, tags, emptyList())))
                selected = model.accounts.lastIndex
                showAddAccount = false
            },
            onDismiss = { showAddAccount = false },
        )
    }

    editAccountId?.let { id ->
        val a = accounts.firstOrNull { it.id == id }
        if (a == null) {
            editAccountId = null
        } else {
            AccountDialog(
                title = "Edit account",
                confirmLabel = "Save",
                initialName = a.name,
                initialIcon = a.iconKey,
                initialColor = a.colorArgb,
                initialTags = a.tags,
                onConfirm = { name, icon, color, tags ->
                    updateAccount(id) { it.copy(name = name, iconKey = icon, colorArgb = color, tags = tags) }
                    editAccountId = null
                },
                onDismiss = { editAccountId = null },
            )
        }
    }

    if (showSend && account != null) {
        SendMoneyDialog(
            accountName = account.name.ifBlank { "this account" },
            onDone = { delta -> updateAccount(account.id) { it.copy(balance = it.balance + delta) } },
            onDismiss = { showSend = false },
        )
    }

    if (showTransfer && account != null) {
        TransferDialog(
            fromName = account.name.ifBlank { "This account" },
            others = accounts.filter { it.id != account.id },
            onTransfer = { targetId, amount ->
                update(
                    model.copy(
                        accounts = model.accounts.map {
                            when (it.id) {
                                account.id -> it.copy(balance = it.balance - amount)
                                targetId -> it.copy(balance = it.balance + amount)
                                else -> it
                            }
                        },
                    ),
                )
            },
            onDismiss = { showTransfer = false },
        )
    }

    if (showAddSection && account != null) {
        AddSectionDialog(
            onAdd = { name, icon, deduct ->
                updateAccount(account.id) { a ->
                    a.copy(sections = a.sections + ExpSection(UUID.randomUUID().toString(), name, icon, deduct, emptyList()))
                }
                showAddSection = false
            },
            onDismiss = { showAddSection = false },
        )
    }
}

// ---- Account tabs --------------------------------------------------------------

@Composable
private fun AccountTabs(
    accounts: List<ExpAccount>,
    selectedIndex: Int,
    accentFor: (ExpAccount) -> Color,
    showAdd: Boolean,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        accounts.forEachIndexed { index, account ->
            AccountChip(
                name = account.name.ifBlank { "Account" },
                accent = accentFor(account),
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
            )
        }
        if (showAdd) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "Add account",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun AccountChip(name: String, accent: Color, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(if (selected) Color.White else accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EmptyAccounts(readOnly: Boolean, onAdd: () -> Unit) {
    ExpenseCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.AccountBalance,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "No accounts yet",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (readOnly) "This tracker has no accounts." else "Add an account to start tracking balances.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!readOnly) {
            Spacer(Modifier.height(14.dp))
            AddSectionButton(onClick = onAdd, label = "Add account")
        }
    }
}

// ---- Account header ------------------------------------------------------------

@Composable
private fun AccountHeaderCard(
    account: ExpAccount,
    accent: Color,
    readOnly: Boolean,
    canTransfer: Boolean,
    onBalance: (Double) -> Unit,
    onCredit: (Double) -> Unit,
    onSend: () -> Unit,
    onTransfer: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRenameText: (String) -> Unit,
) {
    val deductible = account.deductible()
    val total = account.balance + account.credit
    val available = total - deductible
    ExpenseCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(sectionIcon(account.iconKey), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            SectionNameField(value = account.name, onChange = onRenameText, placeholder = "Account name", modifier = Modifier.weight(1f))
            if (!readOnly) AccountMenu(onEdit = onEdit, onDelete = onDelete)
        }

        if (account.tags.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                account.tags.forEach { tag -> TagChip(tag = tag, accent = accent) }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Balance",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        MoneyField(
            value = account.balance,
            onChange = onBalance,
            textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        )
        // Monthly credit (e.g. salary): the user just updates this each month and it adds to the total.
        if (!readOnly || account.credit > 0.0) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Credited this month (salary)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            MoneyField(
                value = account.credit,
                onChange = onCredit,
                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            )
        }
        if (account.credit > 0.0) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatMoney(total),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = accent,
                )
            }
        }
        if (deductible > 0.0) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Available after allocations  " + formatMoney(available),
                style = MaterialTheme.typography.bodySmall,
                color = if (available >= 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }

        if (!readOnly) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(icon = Icons.Rounded.Payments, label = "Send money", accent = accent, onClick = onSend)
                if (canTransfer) {
                    PillButton(icon = Icons.Rounded.SwapHoriz, label = "Transfer", accent = accent, onClick = onTransfer)
                }
            }
        }
    }
}

@Composable
private fun PillButton(icon: ImageVector, label: String, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = accent)
    }
}

@Composable
private fun TagChip(tag: String, accent: Color) {
    Text(
        text = tag,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = accent,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun AccountMenu(onEdit: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Account options", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Edit account") },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                onClick = { open = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text("Delete account") },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { open = false; onDelete() },
            )
        }
    }
}

// ---- Section card --------------------------------------------------------------

@Composable
private fun SectionCard(
    section: ExpSection,
    accent: Color,
    onRename: (String) -> Unit,
    onToggleDeduct: () -> Unit,
    onAddItem: () -> Unit,
    onItemName: (String, String) -> Unit,
    onItemAmount: (String, Double) -> Unit,
    onDeleteItem: (String) -> Unit,
    onRemoveSection: () -> Unit,
) {
    val total = section.items.sumOf { it.amount }
    val readOnly = LocalReadOnly.current
    ExpenseCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(sectionIcon(section.iconKey), contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            SectionNameField(value = section.name, onChange = onRename, placeholder = "Section name", modifier = Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            Text(text = formatMoney(total), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accent)
            if (!readOnly) SectionMenu(onRemove = onRemoveSection)
        }
        Spacer(Modifier.height(6.dp))
        DeductToggle(deduct = section.deductFromBalance, enabled = !readOnly, onToggle = onToggleDeduct)

        if (section.items.isNotEmpty()) Spacer(Modifier.height(6.dp))
        section.items.forEach { item ->
            key(item.id) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlainField(value = item.name, placeholder = "Name", onChange = { onItemName(item.id, it) }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    MoneyField(value = item.amount, onChange = { onItemAmount(item.id, it) }, textStyle = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(110.dp))
                    if (!readOnly) DeleteDot { onDeleteItem(item.id) }
                }
            }
        }
        if (!readOnly) {
            Spacer(Modifier.height(6.dp))
            AddRow(onAddItem)
        }
    }
}

@Composable
private fun DeductToggle(deduct: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (deduct) Icons.Rounded.Check else Icons.Rounded.Close,
            contentDescription = null,
            tint = if (deduct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (deduct) "Spends from balance" else "Tracked separately",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionNameField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Box(modifier) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            readOnly = LocalReadOnly.current,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SectionMenu(onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier.size(30.dp).clip(CircleShape).clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Section options", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Remove section") },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { open = false; onRemove() },
            )
        }
    }
}

@Composable
private fun AddSectionButton(onClick: () -> Unit, label: String = "Add section") {
    val neu = LocalNeuColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .neumorphicRaised(26.dp, neu, elevation = 6.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onClick)
                .padding(start = 8.dp, end = 22.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ---- Bank transfers ------------------------------------------------------------

@Composable
private fun BankTransfersCard(
    accounts: List<ExpAccount>,
    transfers: List<ExpTransfer>,
    accent: Color,
    readOnly: Boolean,
    onAdd: () -> Unit,
    onName: (String, String) -> Unit,
    onFrom: (String, String) -> Unit,
    onTo: (String, String) -> Unit,
    onAmount: (String, Double) -> Unit,
    onExecute: (ExpTransfer) -> Unit,
    onRemove: (String) -> Unit,
) {
    ExpenseCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Bank transfers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("Move money between your accounts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (transfers.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (readOnly) "No transfers saved."
                else "Save a from → to transfer once, then run it with a single tap every month.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        transfers.forEach { t ->
            key(t.id) {
                Spacer(Modifier.height(12.dp))
                TransferRow(
                    transfer = t,
                    accounts = accounts,
                    accent = accent,
                    readOnly = readOnly,
                    onName = { onName(t.id, it) },
                    onFrom = { onFrom(t.id, it) },
                    onTo = { onTo(t.id, it) },
                    onAmount = { onAmount(t.id, it) },
                    onExecute = { onExecute(t) },
                    onRemove = { onRemove(t.id) },
                )
            }
        }
        if (!readOnly) {
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onAdd)
                    .padding(vertical = 6.dp, horizontal = 2.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add transfer", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add transfer", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun TransferRow(
    transfer: ExpTransfer,
    accounts: List<ExpAccount>,
    accent: Color,
    readOnly: Boolean,
    onName: (String) -> Unit,
    onFrom: (String) -> Unit,
    onTo: (String) -> Unit,
    onAmount: (Double) -> Unit,
    onExecute: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlainField(value = transfer.name, placeholder = "Transfer name (e.g. Rent)", onChange = onName, modifier = Modifier.weight(1f))
            if (!readOnly) DeleteDot { onRemove() }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            AccountDropdown(label = "From", accounts = accounts, selectedId = transfer.fromId, enabled = !readOnly, modifier = Modifier.weight(1f), onSelect = onFrom)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.ArrowForward, contentDescription = "to", tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            AccountDropdown(label = "To", accounts = accounts, selectedId = transfer.toId, enabled = !readOnly, modifier = Modifier.weight(1f), onSelect = onTo)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Amount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                MoneyField(value = transfer.amount, onChange = onAmount, textStyle = MaterialTheme.typography.titleMedium)
            }
            if (!readOnly) {
                Spacer(Modifier.width(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accent)
                        .clickable(onClick = onExecute)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Transfer", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun AccountDropdown(
    label: String,
    accounts: List<ExpAccount>,
    selectedId: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val selected = accounts.firstOrNull { it.id == selectedId }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(enabled = enabled) { open = true }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
                Text(
                    text = selected?.name?.ifBlank { "Account" } ?: "Select",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (enabled) Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                accounts.forEach { acc ->
                    DropdownMenuItem(
                        text = { Text(acc.name.ifBlank { "Account" }) },
                        onClick = { open = false; onSelect(acc.id) },
                    )
                }
            }
        }
    }
}

// ---- Dialogs -------------------------------------------------------------------

@Composable
private fun AddSectionDialog(onAdd: (name: String, iconKey: String, deduct: Boolean) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var iconKey by remember { mutableStateOf("savings") }
    var deduct by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New section") },
        text = {
            Column {
                DialogField(value = name, onChange = { name = it }, placeholder = "Section name")
                Spacer(Modifier.height(16.dp))
                Text("Icon", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                IconPickerRow(selected = iconKey, onSelect = { iconKey = it })
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Spends from balance", deduct, Modifier.fillMaxWidth()) { deduct = true }
                    ChoiceChip("Track separately", !deduct, Modifier.fillMaxWidth()) { deduct = false }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (deduct) "Its total is subtracted from this account's available balance." else "Just tracked here - it doesn't change the balance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onAdd(name.trim(), iconKey, deduct) }) { Text("Add", fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AccountDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (name: String, iconKey: String, colorArgb: Int, tags: List<String>) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
    initialIcon: String = "account",
    initialColor: Int = 0,
    initialTags: List<String> = emptyList(),
) {
    var name by remember { mutableStateOf(initialName) }
    var iconKey by remember { mutableStateOf(initialIcon) }
    var color by remember { mutableStateOf(initialColor) }
    var tags by remember { mutableStateOf(initialTags) }
    var customTag by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                DialogField(value = name, onChange = { name = it }, placeholder = "Account name")
                Spacer(Modifier.height(16.dp))
                Text("Icon", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                IconPickerRow(selected = iconKey, onSelect = { iconKey = it })
                Spacer(Modifier.height(16.dp))
                Text("Colour", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    accountColorChoices.forEach { c -> ColorSwatch(colorArgb = c, selected = c == color, onClick = { color = c }) }
                }
                Spacer(Modifier.height(16.dp))
                Text("Tags", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (presetTags + tags.filter { it !in presetTags }).distinct().forEach { tag ->
                        ChoiceChip(label = tag, selected = tag in tags) {
                            tags = if (tag in tags) tags - tag else tags + tag
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { DialogField(value = customTag, onChange = { customTag = it }, placeholder = "Add a custom tag") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            val t = customTag.trim()
                            if (t.isNotBlank() && t !in tags) tags = tags + t
                            customTag = ""
                        },
                        enabled = customTag.isNotBlank(),
                    ) { Text("Add") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim(), iconKey, color, tags) }) { Text(confirmLabel, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SendMoneyDialog(accountName: String, onDone: (delta: Double) -> Unit, onDismiss: () -> Unit) {
    var add by remember { mutableStateOf(true) }
    var amountText by remember { mutableStateOf("") }
    val amount = amountText.toDoubleOrNull() ?: 0.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send money") },
        text = {
            Column {
                Text("Add to or take from $accountName.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChoiceChip("Add", add) { add = true }
                    ChoiceChip("Subtract", !add) { add = false }
                }
                Spacer(Modifier.height(12.dp))
                NumberDialogField(value = amountText, onChange = { amountText = it }, placeholder = "Amount")
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (amount > 0.0) { onDone(if (add) amount else -amount); onDismiss() } },
                enabled = amount > 0.0,
            ) { Text("Done", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TransferDialog(fromName: String, others: List<ExpAccount>, onTransfer: (targetId: String, amount: Double) -> Unit, onDismiss: () -> Unit) {
    var targetId by remember { mutableStateOf(others.firstOrNull()?.id) }
    var amountText by remember { mutableStateOf("") }
    val amount = amountText.toDoubleOrNull() ?: 0.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transfer") },
        text = {
            Column {
                Text("Move money from $fromName to another account.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("To", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    others.forEach { acc ->
                        ChoiceChip(label = acc.name.ifBlank { "Account" }, selected = acc.id == targetId) { targetId = acc.id }
                    }
                }
                Spacer(Modifier.height(12.dp))
                NumberDialogField(value = amountText, onChange = { amountText = it }, placeholder = "Amount")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val t = targetId
                    if (t != null && amount > 0.0) { onTransfer(t, amount); onDismiss() }
                },
                enabled = targetId != null && amount > 0.0,
            ) { Text("Transfer", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun IconPickerRow(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        sectionIconOptions.forEach { (key, icon) -> IconChoice(icon = icon, selected = key == selected, onClick = { onSelect(key) }) }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        textAlign = TextAlign.Center,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ColorSwatch(colorArgb: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .border(
                width = 2.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .padding(4.dp)
            .clip(CircleShape)
            .background(if (colorArgb != 0) Color(colorArgb) else MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun DialogField(value: String, onChange: (String) -> Unit, placeholder: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text(text = placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NumberDialogField(value: String, onChange: (String) -> Unit, placeholder: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text(text = placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
        BasicTextField(
            value = value,
            onValueChange = { raw -> onChange(raw.filter { it.isDigit() || it == '.' }) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun IconChoice(icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ---- Shared building blocks ----------------------------------------------------

@Composable
private fun ExpenseCard(content: @Composable () -> Unit) {
    val neu = LocalNeuColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphicRaised(18.dp, neu, elevation = 5.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        content()
    }
}

@Composable
private fun AddRow(onAdd: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onAdd)
            .padding(vertical = 6.dp, horizontal = 2.dp),
    ) {
        Icon(Icons.Rounded.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Add", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun DeleteDot(onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun PlainField(value: String, placeholder: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        if (value.isEmpty()) {
            Text(text = placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            readOnly = LocalReadOnly.current,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MoneyField(value: Double, onChange: (Double) -> Unit, textStyle: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(plainAmount(value)) }
    LaunchedEffect(value) {
        // Keep the field in sync when the balance changes from outside (send / transfer).
        val external = plainAmount(value)
        if ((text.toDoubleOrNull() ?: 0.0) != value) text = external
    }
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Text(text = "₹", style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(2.dp))
        Box(modifier = Modifier.weight(1f, fill = false)) {
            if (text.isEmpty()) {
                Text(text = "0", style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            }
            BasicTextField(
                value = text,
                onValueChange = { raw ->
                    val filtered = raw.filter { it.isDigit() || it == '.' }
                    text = filtered
                    onChange(filtered.toDoubleOrNull() ?: 0.0)
                },
                singleLine = true,
                textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                readOnly = LocalReadOnly.current,
            )
        }
    }
}
