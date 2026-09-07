package com.example.ui.editor

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ExpenseCodec
import com.example.domain.model.ExpAccount
import com.example.domain.model.ExpItem
import com.example.domain.model.ExpSection
import com.example.domain.model.ExpTransaction
import com.example.domain.model.ExpenseKind
import com.example.domain.model.ExpenseModel
import com.example.domain.model.ExpenseMoney
import com.example.ui.util.responsiveHorizontalPadding
import java.math.BigDecimal
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private data class SectionRemoval(val accountId: String, val sectionId: String)
private data class ReceiptTarget(val accountId: String, val sectionId: String, val itemId: String? = null)

@Composable
internal fun ExpenseEditor(
    seedKey: String,
    title: String,
    content: String,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    meta: @Composable () -> Unit = {},
    onCommitContent: (String) -> Unit = onContentChange,
    saving: Boolean = false,
) {
    val loaded = remember(seedKey) { runCatching { ExpenseCodec.decode(content) } }
    var model by remember(seedKey) { mutableStateOf(loaded.getOrDefault(ExpenseModel())) }
    var selectedId by rememberSaveable(seedKey) { mutableStateOf<String?>(null) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var editAccount by remember { mutableStateOf<ExpAccount?>(null) }
    var showSectionDialog by remember { mutableStateOf(false) }
    var balanceAccountId by remember { mutableStateOf<String?>(null) }
    var actionPending by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var receiptTarget by remember { mutableStateOf<ReceiptTarget?>(null) }
    var reversalId by remember { mutableStateOf<String?>(null) }
    var removeAccountId by remember { mutableStateOf<String?>(null) }
    var removeSection by remember { mutableStateOf<SectionRemoval?>(null) }
    var allActivity by rememberSaveable(seedKey) { mutableStateOf(false) }
    val readOnly = LocalReadOnly.current
    val context = LocalContext.current
    val money = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")) }
    val format: (Long) -> String = { money.format(BigDecimal.valueOf(it, 2)) }
    val account = model.accounts.firstOrNull { it.id == selectedId } ?: model.accounts.firstOrNull()

    LaunchedEffect(actionPending, saving) {
        if (actionPending && !saving) actionPending = false
    }

    fun update(next: ExpenseModel, immediate: Boolean = false): Boolean {
        if (readOnly || loaded.isFailure) return false
        return runCatching {
            val encoded = ExpenseCodec.encode(next)
            if (immediate) onCommitContent(encoded) else onContentChange(encoded)
            model = next
            true
        }.onFailure { Toast.makeText(context, "Tracker was not changed: ${it.message}", Toast.LENGTH_LONG).show() }.getOrDefault(false)
    }
    fun changeAccount(accountId: String, transform: (ExpAccount) -> ExpAccount) {
        update(model.copy(accounts = model.accounts.map { if (it.id == accountId) transform(it) else it }))
    }
    fun changeSection(accountId: String, sectionId: String, transform: (ExpSection) -> ExpSection) {
        changeAccount(accountId) { current ->
            current.copy(sections = current.sections.map { if (it.id == sectionId) transform(it) else it })
        }
    }
    fun changeItem(accountId: String, sectionId: String, itemId: String, transform: (ExpItem) -> ExpItem) {
        changeSection(accountId, sectionId) { section ->
            section.copy(items = section.items.map { if (it.id == itemId) transform(it) else it })
        }
    }

    val sidePadding = responsiveHorizontalPadding(compact = 16.dp)
    LazyColumn(
        modifier = modifier.fillMaxWidth().imePadding(),
        contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 8.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "title") {
            BasicTextField(
                value = title, onValueChange = onTitleChange, readOnly = readOnly,
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (title.isEmpty()) Text("My money", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inner()
                },
            )
            Spacer(Modifier.height(12.dp))
            meta()
        }
        if (loaded.isFailure) {
            item {
                Text("This tracker could not be opened. Its saved content has not been changed.", color = MaterialTheme.colorScheme.error)
                Text(loaded.exceptionOrNull()?.message.orEmpty(), style = MaterialTheme.typography.bodySmall)
            }
        } else {
            item(key = "accounts") {
                if (model.accounts.isNotEmpty()) {
                    Text("Across accounts  ${format(model.accounts.sumOf { it.balance })}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    model.accounts.forEach { current ->
                        FilterChip(
                            selected = current.id == account?.id, onClick = { selectedId = current.id },
                            label = { Text(current.name.ifBlank { "Account" }, maxLines = 1) },
                            leadingIcon = { Icon(Icons.Rounded.AccountBalance, null, Modifier.size(18.dp)) },
                        )
                    }
                    if (!readOnly) ExpenseIconButton(Icons.Rounded.Add, "Add account") { editAccount = null; showAccountDialog = true }
                }
            }
            if (account == null) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.AccountBalance, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text("No accounts yet", style = MaterialTheme.typography.titleMedium)
                        if (!readOnly) TextButton(onClick = { editAccount = null; showAccountDialog = true }) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add account")
                        }
                    }
                }
            } else {
                item(key = "balance:${account.id}") {
                    BalanceHeader(
                        account = account, format = format, readOnly = readOnly,
                        onAdjust = { balanceAccountId = account.id },
                        onEdit = { editAccount = account; showAccountDialog = true },
                        onDelete = { removeAccountId = account.id },
                    )
                }
                actionError?.let { message ->
                    item(key = "action-error") {
                        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                account.sections.forEach { section ->
                    item(key = "section:${account.id}:${section.id}") {
                        ExpenseSectionHeader(
                            section = section, readOnly = readOnly, format = format,
                            onName = { name -> changeSection(account.id, section.id) { it.copy(name = name) } },
                            onKind = { kind -> changeSection(account.id, section.id) { it.copy(kind = kind) } },
                            onDelete = { removeSection = SectionRemoval(account.id, section.id) },
                        )
                    }
                    items(section.items, key = { "item:${account.id}:${section.id}:${it.id}" }) { item ->
                        ExpenseItemRow(
                            item = item, kind = section.kind, accounts = model.accounts.filter { it.id != account.id },
                            readOnly = readOnly, format = format, busy = saving || actionPending,
                            onReceipt = { receiptTarget = ReceiptTarget(account.id, section.id, item.id) },
                            onName = { name -> changeItem(account.id, section.id, item.id) { it.copy(name = name) } },
                            onAmount = { amount -> changeItem(account.id, section.id, item.id) { it.copy(amount = amount) } },
                            onTarget = { target -> changeItem(account.id, section.id, item.id) { it.copy(toAccountId = target) } },
                            onComplete = {
                                if (!saving && !actionPending) {
                                    actionError = null
                                    runCatching { model.complete(account.id, section.id, item.id) }
                                        .onSuccess { next ->
                                            actionPending = true
                                            update(next, immediate = true)
                                        }
                                        .onFailure { actionError = it.message ?: "Unable to record this action" }
                                }
                            },
                            onDelete = { changeSection(account.id, section.id) { it.copy(items = it.items.filterNot { row -> row.id == item.id }) } },
                        )
                    }
                    if (!readOnly) item(key = "add:${account.id}:${section.id}") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { changeSection(account.id, section.id) { it.copy(items = it.items + ExpItem()) } }) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add saved action")
                        }
                        Spacer(Modifier.weight(1f))
                        if (section.kind in listOf(ExpenseKind.EXPENSE, ExpenseKind.SAVINGS, ExpenseKind.INVESTMENT)) {
                            ExpenseIconButton(Icons.Rounded.DocumentScanner, "Add receipt", enabled = !saving) { receiptTarget = ReceiptTarget(account.id, section.id) }
                        }
                        }
                    }
                }
                if (!readOnly) item(key = "add-section:${account.id}") {
                    OutlinedButton(onClick = { showSectionDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add section")
                    }
                }
            }
            if (model.history.isNotEmpty()) {
                item(key = "activity-header") {
                    HorizontalDivider(Modifier.padding(top = 16.dp, bottom = 12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.History, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Activity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        FilterChip(selected = allActivity, onClick = { allActivity = !allActivity }, label = { Text("All accounts") })
                    }
                }
                val visibleHistory = model.history.asReversed().filter { allActivity || account == null || it.accountId == account.id || it.toAccountId == account.id }
                items(visibleHistory, key = { "history:${it.id}" }) { record ->
                    ExpenseActivityRow(record, if (allActivity) null else account?.id, format, readOnly) { reversalId = record.id }
                }
            }
        }
    }

    receiptTarget?.let { target ->
        val targetAccount = model.accounts.firstOrNull { it.id == target.accountId }
        val section = targetAccount?.sections?.firstOrNull { it.id == target.sectionId }
        if (!readOnly && section != null) ReceiptCapture(
            initial = section.items.firstOrNull { it.id == target.itemId },
            onSave = { saved ->
                update(model.copy(accounts = model.accounts.map { current ->
                    if (current.id != target.accountId) current else current.copy(sections = current.sections.map { currentSection ->
                        if (currentSection.id != target.sectionId) currentSection else currentSection.copy(items =
                            if (target.itemId == null) currentSection.items + saved else currentSection.items.map { if (it.id == target.itemId) saved else it })
                    })
                }), immediate = true)
            },
            onDismiss = { receiptTarget = null },
        )
    }

    if (showAccountDialog && !readOnly) {
        ExpenseAccountDialog(
            initial = editAccount,
            onDismiss = { showAccountDialog = false },
            onSave = { saved ->
                val next = if (editAccount == null) {
                    model.copy(accounts = model.accounts + saved.copy(balance = 0)).adjustBalance(saved.id, saved.balance)
                } else model.copy(accounts = model.accounts.map { if (it.id == saved.id) saved else it })
                update(next, immediate = true)
                selectedId = saved.id
                showAccountDialog = false
            },
        )
    }
    if (showSectionDialog && account != null && !readOnly) {
        ExpenseSectionDialog(onDismiss = { showSectionDialog = false }) { section ->
            changeAccount(account.id) { it.copy(sections = it.sections + section) }
            showSectionDialog = false
        }
    }
    model.accounts.firstOrNull { it.id == balanceAccountId }?.let { current ->
        if (!readOnly) BalanceDialog(current, onDismiss = { balanceAccountId = null }) { balance ->
            update(model.adjustBalance(current.id, balance), immediate = true)
            balanceAccountId = null
        }
    }
    reversalId?.let { transactionId ->
        if (!readOnly) TransactionConfirmation(
            title = "Reverse transaction?", before = model,
            preview = runCatching { model.reverse(transactionId) }, format = format,
            onDismiss = { reversalId = null },
            onConfirm = {
                runCatching { model.reverse(transactionId) }
                    .onSuccess { update(it, immediate = true) }
                    .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                reversalId = null
            },
        )
    }
    removeAccountId?.let { accountId ->
        if (!readOnly) ExpenseDeleteDialog(
            "Remove account?", "Its saved actions will be removed. Recorded activity will be kept. Other account balances will not change.",
            onDismiss = { removeAccountId = null },
        ) {
            update(model.copy(accounts = model.accounts.filterNot { it.id == accountId }), immediate = true)
            removeAccountId = null
        }
    }
    removeSection?.let { request ->
        if (!readOnly) ExpenseDeleteDialog(
            "Remove section?", "Saved actions will be removed. Recorded activity and account balances will be kept.",
            onDismiss = { removeSection = null },
        ) {
            update(model.copy(accounts = model.accounts.map { current ->
                if (current.id != request.accountId) current else current.copy(sections = current.sections.filterNot { it.id == request.sectionId })
            }), immediate = true)
            removeSection = null
        }
    }
}

@Composable
private fun BalanceHeader(account: ExpAccount, format: (Long) -> String, readOnly: Boolean, onAdjust: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val accent = if (account.colorArgb == 0) MaterialTheme.colorScheme.primary else Color(account.colorArgb)
    var menu by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AccountBalance, null, tint = accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(account.name.ifBlank { "Account" }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (!readOnly) Box {
                    ExpenseIconButton(Icons.Rounded.MoreVert, "Account options") { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit account") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Remove account") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Text("Recorded balance", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(format(account.balance), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (!readOnly) ExpenseIconButton(Icons.Rounded.Edit, "Set or correct balance", onClick = onAdjust)
            }
            if (account.tags.isNotEmpty()) {
                Text(account.tags.joinToString(" / "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val planned = account.configuredOutflow()
            if (planned > 0) {
                Spacer(Modifier.height(8.dp))
                Text("Saved outflows  ${format(planned)}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "After saved outflows  ${format(account.balance - planned)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (planned > account.balance) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExpenseSectionHeader(section: ExpSection, readOnly: Boolean, format: (Long) -> String, onName: (String) -> Unit, onKind: (ExpenseKind) -> Unit, onDelete: () -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        HorizontalDivider(Modifier.padding(bottom = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(section.kind.icon(), null, Modifier.size(20.dp), tint = section.kind.tint())
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = section.name, onValueChange = onName, readOnly = readOnly,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.weight(1f),
            )
            if (!readOnly) ExpenseIconButton(Icons.Rounded.Delete, "Remove section", onClick = onDelete)
        }
        ExpenseKindPicker(section.kind, enabled = !readOnly, onSelect = onKind)
        Text("${section.items.size} saved ${if (section.items.size == 1) "action" else "actions"} / ${format(section.items.sumOf { it.amount })}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExpenseItemRow(
    item: ExpItem, kind: ExpenseKind, accounts: List<ExpAccount>, readOnly: Boolean, format: (Long) -> String,
    busy: Boolean,
    onReceipt: () -> Unit,
    onName: (String) -> Unit, onAmount: (Long) -> Unit, onTarget: (String) -> Unit,
    onComplete: () -> Unit, onDelete: () -> Unit,
) {
    var editing by rememberSaveable(item.id) { mutableStateOf(item.name.isBlank() || item.amount == 0L) }
    var viewReceipt by remember { mutableStateOf(false) }
    val valid = item.name.isNotBlank() && item.amount > 0 &&
        (kind != ExpenseKind.TRANSFER || accounts.any { it.id == item.toAccountId })
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!editing || readOnly) Text(item.name.ifBlank { "Unnamed action" }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                else OutlinedTextField(value = item.name, onValueChange = onName, label = { Text("Action name") }, singleLine = true, modifier = Modifier.weight(1f))
                if (!readOnly) {
                    ExpenseIconButton(
                        if (editing) Icons.Rounded.Check else Icons.Rounded.Edit,
                        if (editing) "Save action" else "Edit saved action",
                        enabled = !busy && (!editing || valid),
                    ) { editing = !editing }
                    if (editing) ExpenseIconButton(Icons.Rounded.Close, "Remove saved action", enabled = !busy, onClick = onDelete)
                }
            }
            if (kind == ExpenseKind.TRANSFER) {
                if (editing && !readOnly) ExpenseAccountPicker(accounts, item.toAccountId, !busy, onTarget)
                else Text("To ${accounts.firstOrNull { it.id == item.toAccountId }?.name ?: "unavailable account"}", style = MaterialTheme.typography.bodySmall)
            }
            if (!editing || readOnly) {
                Text(format(item.amount), style = MaterialTheme.typography.titleMedium, color = kind.tint())
            } else ExpenseAmountField(item.amount, onAmount, "Amount")
            if (item.receiptToken != null) {
                TextButton(onClick = { viewReceipt = true }) { Icon(Icons.Rounded.ReceiptLong, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Receipt${item.receiptDate?.let { " / $it" }.orEmpty()}") }
            }
            if (editing && !readOnly && kind in listOf(ExpenseKind.EXPENSE, ExpenseKind.SAVINGS, ExpenseKind.INVESTMENT)) {
                TextButton(onClick = onReceipt, enabled = !busy) { Icon(Icons.Rounded.DocumentScanner, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Attach receipt") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.completedAt?.let { "Last recorded ${expenseDate(it)}" } ?: "Not recorded yet",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!readOnly && !editing) Button(
                    onClick = onComplete,
                    enabled = valid && !busy,
                ) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Record")
                }
            }
        }
    }
    if (viewReceipt && item.receiptToken != null) ReceiptImage(item.receiptToken, onDismiss = { viewReceipt = false })
}

@Composable
private fun ExpenseActivityRow(record: ExpTransaction, accountId: String?, format: (Long) -> String, readOnly: Boolean, onReverse: () -> Unit) {
    var viewReceipt by remember(record.id) { mutableStateOf(false) }
    val incoming = accountId != null && record.toAccountId == accountId
    val delta = if (incoming) { if (record.reversalOf == null) record.amount else -record.amount } else record.balanceDelta
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (record.reversalOf != null) Icons.Rounded.Undo else record.kind.icon(), null, Modifier.size(18.dp), tint = record.kind.tint())
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(record.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(record.sectionName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!readOnly && record.reversedAt == null && record.reversalOf == null) ExpenseIconButton(Icons.Rounded.Undo, "Reverse this transaction", onClick = onReverse)
        }
        Text(
            (if (delta > 0) "+" else if (delta < 0) "-" else "") + format(if (delta == 0L) record.amount else kotlin.math.abs(delta)),
            style = MaterialTheme.typography.titleSmall,
            color = if (delta < 0) MaterialTheme.colorScheme.error else if (delta > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
        )
        val route = if (record.toAccountName != null) "${record.accountName} -> ${record.toAccountName}" else record.accountName
        Text(route, style = MaterialTheme.typography.bodySmall)
        Text("${record.accountName} balance after: ${format(record.balanceAfter)}", style = MaterialTheme.typography.labelSmall)
        if (record.toBalanceAfter != null) Text("${record.toAccountName} balance after: ${format(record.toBalanceAfter)}", style = MaterialTheme.typography.labelSmall)
        val status = when {
            record.reversalOf != null -> "Reversal"
            record.reversedAt != null -> "Reversed"
            else -> "Completed"
        }
        Text("$status / ${expenseDate(record.completedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (record.receiptToken != null) TextButton(onClick = { viewReceipt = true }) {
            Icon(Icons.Rounded.ReceiptLong, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Receipt${record.receiptDate?.let { " / $it" }.orEmpty()}")
        }
        HorizontalDivider(Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
    if (viewReceipt && record.receiptToken != null) ReceiptImage(record.receiptToken, onDismiss = { viewReceipt = false })
}

@Composable
private fun TransactionConfirmation(title: String, before: ExpenseModel, preview: Result<ExpenseModel>, format: (Long) -> String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val after = preview.getOrNull()
                if (after == null) Text(preview.exceptionOrNull()?.message ?: "Transaction is unavailable", color = MaterialTheme.colorScheme.error)
                else {
                    val record = after.history.last()
                    Text(record.name, fontWeight = FontWeight.SemiBold)
                    Text("Recorded balances", style = MaterialTheme.typography.labelLarge)
                    before.accounts.filter { it.id == record.accountId || it.id == record.toAccountId }.forEach { current ->
                        val next = after.accounts.first { it.id == current.id }
                        Column {
                            Text(current.name, fontWeight = FontWeight.SemiBold)
                            Text("${format(current.balance)} -> ${format(next.balance)}")
                        }
                    }
                    if (record.kind == ExpenseKind.TRACKING) Text("Budget record only", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = preview.isSuccess) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BalanceDialog(account: ExpAccount, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var balance by remember(account.id) { mutableStateOf(account.balance.coerceAtLeast(0)) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Set recorded balance") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(account.name)
            ExpenseAmountField(balance, { balance = it }, "Current balance")
        } },
        confirmButton = { TextButton(onClick = { onSave(balance) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ExpenseSectionDialog(onDismiss: () -> Unit, onSave: (ExpSection) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(ExpenseKind.EXPENSE) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("New section") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Section name") }, placeholder = { Text("Daily expenses, salary, investments") }, modifier = Modifier.fillMaxWidth())
            ExpenseKindPicker(kind, enabled = true) { kind = it }
        } },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(ExpSection(name = name.trim(), iconKey = kind.name.lowercase(), kind = kind)) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ExpenseAccountDialog(initial: ExpAccount?, onDismiss: () -> Unit, onSave: (ExpAccount) -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var balance by remember { mutableStateOf(0L) }
    var tags by remember { mutableStateOf(initial?.tags.orEmpty()) }
    var customTag by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(initial?.colorArgb ?: 0) }
    val palette = listOf(0, 0xFF26A69A.toInt(), 0xFF42A5F5.toInt(), 0xFFEC407A.toInt(), 0xFFEF5350.toInt())
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (initial == null) "New account" else "Edit account") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Account name") }, modifier = Modifier.fillMaxWidth())
                if (initial == null) ExpenseAmountField(balance, { balance = it }, "Opening balance")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    palette.forEachIndexed { index, swatch ->
                        IconButton(onClick = { color = swatch }) {
                            Box(Modifier.size(30.dp).background(if (swatch == 0) MaterialTheme.colorScheme.primary else Color(swatch), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(if (color == swatch) Icons.Rounded.Check else Icons.Rounded.AccountBalance, "Account colour ${index + 1}", Modifier.size(18.dp), tint = Color.White)
                            }
                        }
                    }
                }
                Text("Tags", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf("Salary", "Primary", "Savings", "Emergency", "Cash") + tags).distinct().forEach { tag ->
                        FilterChip(selected = tag in tags, onClick = { tags = if (tag in tags) tags - tag else tags + tag }, label = { Text(tag) })
                    }
                }
                OutlinedTextField(value = customTag, onValueChange = { customTag = it }, label = { Text("Custom tag") }, modifier = Modifier.fillMaxWidth(), trailingIcon = {
                    ExpenseIconButton(Icons.Rounded.Add, "Add tag", enabled = customTag.isNotBlank()) {
                        tags = (tags + customTag.trim()).distinct()
                        customTag = ""
                    }
                })
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
            onSave(initial?.copy(name = name.trim(), tags = tags, colorArgb = color) ?: ExpAccount(name = name.trim(), balance = balance, tags = tags, colorArgb = color))
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ExpenseKindPicker(kind: ExpenseKind, enabled: Boolean, onSelect: (ExpenseKind) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, enabled = enabled) {
            Icon(kind.icon(), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(kind.label)
            if (enabled) Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open && enabled, onDismissRequest = { open = false }) {
            ExpenseKind.entries.filter { it != ExpenseKind.ADJUSTMENT }.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, leadingIcon = { Icon(option.icon(), null) }, onClick = { open = false; onSelect(option) })
            }
        }
    }
}

@Composable
private fun ExpenseAccountPicker(accounts: List<ExpAccount>, selectedId: String?, enabled: Boolean, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = accounts.firstOrNull { it.id == selectedId }
    Box {
        OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("To: ${selected?.name ?: "Choose account"}", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (enabled) Icon(Icons.Rounded.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open && enabled, onDismissRequest = { open = false }) {
            accounts.forEach { account ->
                DropdownMenuItem(text = { Text(account.name) }, onClick = { open = false; onSelect(account.id) })
            }
        }
    }
}

@Composable
private fun ExpenseAmountField(value: Long, onChange: (Long) -> Unit, label: String) {
    var text by remember { mutableStateOf(if (value == 0L) "" else ExpenseMoney.text(value)) }
    LaunchedEffect(value) {
        if ((ExpenseMoney.parse(text) ?: 0L) != value) text = if (value == 0L) "" else ExpenseMoney.text(value)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            if (ExpenseMoney.inputPattern.matches(raw)) {
                text = raw
                if (raw.isBlank() || raw == ".") onChange(0) else ExpenseMoney.parse(raw)?.let(onChange)
            }
        },
        label = { Text(label) }, prefix = { Text("INR ") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ExpenseDeleteDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseIconButton(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(description) } }, state = rememberTooltipState()) {
        IconButton(onClick = onClick, enabled = enabled) { Icon(icon, description, Modifier.size(20.dp)) }
    }
}

private fun ExpenseKind.icon(): ImageVector = when (this) {
    ExpenseKind.CREDIT -> Icons.Rounded.ArrowDownward
    ExpenseKind.EXPENSE -> Icons.Rounded.ArrowUpward
    ExpenseKind.SAVINGS -> Icons.Rounded.Savings
    ExpenseKind.INVESTMENT -> Icons.Rounded.TrendingUp
    ExpenseKind.TRANSFER -> Icons.Rounded.SwapHoriz
    ExpenseKind.TRACKING -> Icons.Rounded.ReceiptLong
    ExpenseKind.ADJUSTMENT -> Icons.Rounded.Payments
}

@Composable
private fun ExpenseKind.tint(): Color = when (this) {
    ExpenseKind.CREDIT -> MaterialTheme.colorScheme.tertiary
    ExpenseKind.EXPENSE -> MaterialTheme.colorScheme.error
    ExpenseKind.INVESTMENT -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun expenseDate(timestamp: Long): String {
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    return remember(timestamp) { formatter.format(Date(timestamp)) }
}