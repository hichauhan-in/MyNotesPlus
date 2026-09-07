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

private data class CompletionRequest(val accountId: String, val sectionId: String, val itemId: String)
private data class SectionRemoval(val accountId: String, val sectionId: String)

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
) {
    val loaded = remember(seedKey) { runCatching { ExpenseCodec.decode(content) } }
    var model by remember(seedKey) { mutableStateOf(loaded.getOrDefault(ExpenseModel())) }
    var selectedId by rememberSaveable(seedKey) { mutableStateOf<String?>(null) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var editAccount by remember { mutableStateOf<ExpAccount?>(null) }
    var showSectionDialog by remember { mutableStateOf(false) }
    var balanceAccountId by remember { mutableStateOf<String?>(null) }
    var completion by remember { mutableStateOf<CompletionRequest?>(null) }
    var reversalId by remember { mutableStateOf<String?>(null) }
    var removeAccountId by remember { mutableStateOf<String?>(null) }
    var removeSection by remember { mutableStateOf<SectionRemoval?>(null) }
    var allActivity by rememberSaveable(seedKey) { mutableStateOf(false) }
    val readOnly = LocalReadOnly.current
    val context = LocalContext.current
    val money = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")) }
    val format: (Long) -> String = { money.format(BigDecimal.valueOf(it, 2)) }
    val account = model.accounts.firstOrNull { it.id == selectedId } ?: model.accounts.firstOrNull()

    fun update(next: ExpenseModel, immediate: Boolean = false) {
        if (readOnly || loaded.isFailure) return
        runCatching {
            val encoded = ExpenseCodec.encode(next)
            if (immediate) onCommitContent(encoded) else onContentChange(encoded)
            model = next
        }.onFailure { Toast.makeText(context, "Tracker was not changed: ${it.message}", Toast.LENGTH_LONG).show() }
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
            section.copy(items = section.items.map { if (it.id == itemId && it.completedAt == null) transform(it) else it })
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
                            readOnly = readOnly, format = format,
                            onName = { name -> changeItem(account.id, section.id, item.id) { it.copy(name = name) } },
                            onAmount = { amount -> changeItem(account.id, section.id, item.id) { it.copy(amount = amount) } },
                            onTarget = { target -> changeItem(account.id, section.id, item.id) { it.copy(toAccountId = target) } },
                            onComplete = { completion = CompletionRequest(account.id, section.id, item.id) },
                            onRepeat = {
                                changeSection(account.id, section.id) { it.copy(items = it.items + item.copy(id = UUID.randomUUID().toString(), completedAt = null)) }
                            },
                            onDelete = { changeSection(account.id, section.id) { it.copy(items = it.items.filterNot { row -> row.id == item.id }) } },
                        )
                    }
                    if (!readOnly) item(key = "add:${account.id}:${section.id}") {
                        TextButton(onClick = { changeSection(account.id, section.id) { it.copy(items = it.items + ExpItem()) } }) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add transaction")
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
    completion?.let { request ->
        if (!readOnly) {
            val preview = runCatching { model.complete(request.accountId, request.sectionId, request.itemId) }
            TransactionConfirmation(
                title = "Complete transaction?", before = model, preview = preview, format = format,
                onDismiss = { completion = null },
                onConfirm = {
                    runCatching { model.complete(request.accountId, request.sectionId, request.itemId) }
                        .onSuccess { update(it, immediate = true) }
                        .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                    completion = null
                },
            )
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
            "Remove account?", "Its pending transactions will be removed. Completed activity will be kept. Other account balances will not change.",
            onDismiss = { removeAccountId = null },
        ) {
            update(model.copy(accounts = model.accounts.filterNot { it.id == accountId }), immediate = true)
            removeAccountId = null
        }
    }
    removeSection?.let { request ->
        if (!readOnly) ExpenseDeleteDialog(
            "Remove section?", "Pending rows will be removed. Completed activity and account balances will be kept.",
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
            Text("Current balance", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(format(account.balance), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (!readOnly) ExpenseIconButton(Icons.Rounded.Edit, "Set or correct balance", onClick = onAdjust)
            }
            if (account.tags.isNotEmpty()) {
                Text(account.tags.joinToString(" / "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val planned = account.pendingOutflow()
            if (planned > 0) {
                Spacer(Modifier.height(8.dp))
                Text("Pending outflow  ${format(planned)}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "After pending outflow  ${format(account.balance - planned)}",
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
        ExpenseKindPicker(section.kind, enabled = !readOnly && section.items.none { it.completedAt != null }, onSelect = onKind)
        val pending = section.items.filter { it.completedAt == null }.sumOf { it.amount }
        val completed = section.items.filter { it.completedAt != null }.sumOf { it.amount }
        Text("Pending ${format(pending)} / Completed ${format(completed)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExpenseItemRow(
    item: ExpItem, kind: ExpenseKind, accounts: List<ExpAccount>, readOnly: Boolean, format: (Long) -> String,
    onName: (String) -> Unit, onAmount: (Long) -> Unit, onTarget: (String) -> Unit,
    onComplete: () -> Unit, onRepeat: () -> Unit, onDelete: () -> Unit,
) {
    val completed = item.completedAt != null
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (completed) 0.3f else 0.15f)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (completed) {
                    Icon(Icons.Rounded.CheckCircle, "Completed", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(8.dp))
                }
                if (completed || readOnly) Text(item.name.ifBlank { "Unnamed transaction" }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                else OutlinedTextField(value = item.name, onValueChange = onName, label = { Text("Transaction") }, singleLine = true, modifier = Modifier.weight(1f))
                if (!readOnly) {
                    if (completed) ExpenseIconButton(Icons.Rounded.ContentCopy, "Repeat as a new pending transaction", onClick = onRepeat)
                    else ExpenseIconButton(Icons.Rounded.Close, "Remove pending transaction", onClick = onDelete)
                }
            }
            if (kind == ExpenseKind.TRANSFER) {
                ExpenseAccountPicker(accounts, item.toAccountId, !readOnly && !completed, onTarget)
            }
            if (completed || readOnly) {
                Text(format(item.amount), style = MaterialTheme.typography.titleMedium, color = kind.tint())
            } else ExpenseAmountField(item.amount, onAmount, "Amount")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (completed) "Completed ${expenseDate(requireNotNull(item.completedAt))}" else "Pending",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!readOnly && !completed) Button(
                    onClick = onComplete,
                    enabled = item.name.isNotBlank() && item.amount > 0 && (kind != ExpenseKind.TRANSFER || accounts.any { it.id == item.toAccountId }),
                ) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Complete")
                }
            }
        }
    }
}

@Composable
private fun ExpenseActivityRow(record: ExpTransaction, accountId: String?, format: (Long) -> String, readOnly: Boolean, onReverse: () -> Unit) {
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
        HorizontalDivider(Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
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