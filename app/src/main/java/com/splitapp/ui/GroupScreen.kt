package com.splitapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.splitapp.data.SplitViewModel
import com.splitapp.model.*
import java.text.DateFormat
import java.util.Date
import java.util.UUID

@Composable
fun GroupScreen(
    detail: GroupDetail,
    vm: SplitViewModel,
    onBack: () -> Unit,
    onExpense: (String?) -> Unit,
    onPayment: (String?, String?, Long?) -> Unit,
    onShare: (String, String) -> Unit
) {
    var tab by remember(detail.group.id) { mutableIntStateOf(0) }
    var addMember by remember { mutableStateOf(false) }
    var editMemberId by remember { mutableStateOf<String?>(null) }
    var rename by remember { mutableStateOf(false) }
    var archiveConfirm by remember { mutableStateOf(false) }
    var search by remember(detail.group.id) { mutableStateOf("") }
    var categoryFilter by remember(detail.group.id) { mutableStateOf("All") }
    var deleteTransfer by remember { mutableStateOf<String?>(null) }
    val balances = remember(detail) { SplitMath.balances(detail) }
    val settlements = remember(balances) { SplitMath.settle(balances) }
    val names = detail.members.associate { it.id to it.name }
    val total = detail.expenses.sumOf { it.amountMinor }
    Scaffold(
        topBar = {
            AppHeader(detail.group.name, "${detail.members.size} people  ·  ${detail.group.currency}", onBack) {
                Row {
                    TextButton(onClick = { archiveConfirm = true }) { Text(if (detail.group.archived) "Restore" else "Archive") }
                    TextButton(onClick = { rename = true }) { Text("Edit") }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (tab == 1) onPayment(null, null, null) else onExpense(null) },
                content = { Text(if (tab == 1) "+ Payment" else "+ Expense") }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            item {
                Spacer(Modifier.height(5.dp))
                SurfaceCard {
                    Text("TOTAL SHARED EXPENSES", color = Muted, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(7.dp))
                    MoneyLabel(total, detail.group.currency, large = true)
                    Spacer(Modifier.height(13.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${detail.expenses.size} expenses", color = Muted)
                        Text("${detail.transfers.size} repayments", color = Muted)
                    }
                }
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DashboardAction("Add expense", "Record a cost", "＋", { onExpense(null) }, Modifier.weight(1f))
                    DashboardAction("Repayment", "Settle up", "↗", { onPayment(null, null, null) }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("Activity", "Balances", "Insights").forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = tab == index,
                            onClick = { tab = index },
                            shape = SegmentedButtonDefaults.itemShape(index, 3)
                        ) { Text(label) }
                    }
                }
            }
            when (tab) {
                0 -> {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle("Participants", "${detail.members.size} members")
                            TextButton(onClick = { addMember = true }) { Text("+ Add") }
                        }
                        Text("Tap a name to rename a participant.", color = Muted, style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            detail.members.forEach { member ->
                                AssistChip(onClick = { editMemberId = member.id }, label = { Text(member.name) })
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        SectionTitle("All activity")
                        OutlinedTextField(
                            value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("Search expenses or participants") }, singleLine = true
                        )
                        Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (listOf("All") + detail.expenses.map { it.category }.distinct()).forEach { value ->
                                FilterChip(selected = categoryFilter == value, onClick = { categoryFilter = value }, label = { Text(value) })
                            }
                        }
                    }
                    val activity: List<Any> = (detail.expenses.filter { expense ->
                        (categoryFilter == "All" || expense.category == categoryFilter) &&
                        (search.isBlank() || expense.title.contains(search, true) || expense.category.contains(search, true) || names[expense.paidBy].orEmpty().contains(search, true) || expense.note.contains(search, true))
                    } + detail.transfers.filter { transfer ->
                        categoryFilter == "All" && (search.isBlank() || "repayment".contains(search, true) || names[transfer.fromMember].orEmpty().contains(search, true) || names[transfer.toMember].orEmpty().contains(search, true) || transfer.note.contains(search, true))
                    }).sortedByDescending {
                        when (it) { is Expense -> it.timestamp; is Transfer -> it.timestamp; else -> 0L }
                    }
                    if (activity.isEmpty()) item { EmptyState(if (detail.expenses.isEmpty() && detail.transfers.isEmpty()) "No transactions yet" else "No matching activity", if (detail.expenses.isEmpty() && detail.transfers.isEmpty()) "Add an expense such as dinner, tickets or rent." else "Clear the search or change the category.") }
                    items(activity, key = { when (it) { is Expense -> "e${it.id}"; is Transfer -> "t${it.id}"; else -> "" } }) { entry ->
                        when (entry) {
                            is Expense -> SurfaceCard(Modifier.clickable { onExpense(entry.id) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(categoryEmoji(entry.category), style = MaterialTheme.typography.headlineSmall)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(entry.title, fontWeight = FontWeight.SemiBold)
                                        Text(detail.contributions.filter { it.expenseId == entry.id }.let { contributions -> if (contributions.size > 1) "${contributions.size} people paid · ${entry.category}" else "${names[entry.paidBy] ?: "Unknown"} paid · ${entry.category}" }, color = Muted, style = MaterialTheme.typography.bodySmall)
                                        Text(date(entry.timestamp), color = Muted, style = MaterialTheme.typography.labelSmall)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        MoneyLabel(entry.amountMinor, detail.group.currency)
                                        TextButton(onClick = {
                                            val copiedId = UUID.randomUUID().toString()
                                            val copies = detail.allocations.filter { it.expenseId == entry.id }
                                                .map { it.copy(expenseId = copiedId) }
                                            val funding = detail.contributions.filter { it.expenseId == entry.id }
                                                .map { it.copy(expenseId = copiedId) }
                                            vm.saveExpense(entry.copy(id = copiedId, timestamp = System.currentTimeMillis()), copies, {
                                                vm.showMessage("Expense copied. You can edit it from the activity list.")
                                            }, funding.ifEmpty { listOf(Contribution(copiedId, entry.paidBy, entry.amountMinor)) })
                                        }, contentPadding = PaddingValues(0.dp)) { Text("Duplicate") }
                                    }
                                }
                            }
                            is Transfer -> SurfaceCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("↗", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Repayment", fontWeight = FontWeight.SemiBold)
                                        Text("${names[entry.fromMember]} → ${names[entry.toMember]}", color = Muted, style = MaterialTheme.typography.bodySmall)
                                        Text(date(entry.timestamp), color = Muted, style = MaterialTheme.typography.labelSmall)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        MoneyLabel(entry.amountMinor, detail.group.currency)
                                        TextButton(onClick = { deleteTransfer = entry.id }, contentPadding = PaddingValues(0.dp)) { Text("Delete") }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    item {
                        SectionTitle("Individual balances")
                        Text("Positive means they should receive money; negative means they owe.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    items(detail.members, key = { it.id }) { member ->
                        val amount = balances[member.id] ?: 0L
                        SurfaceCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(member.name, fontWeight = FontWeight.Bold)
                                    Text(when { amount > 0 -> "Gets back"; amount < 0 -> "Owes"; else -> "Settled" }, style = MaterialTheme.typography.bodySmall, color = Muted)
                                }
                                MoneyLabel(kotlin.math.abs(amount), detail.group.currency, if (amount < 0) Color(0xFFB34F3D) else MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    item { SectionTitle("Suggested repayments", "${settlements.size} transfers") }
                    if (settlements.isEmpty()) item { EmptyState("Everyone is settled", "No outstanding repayments in this group.") }
                    items(settlements) { settlement ->
                        SurfaceCard {
                            Text("${names[settlement.fromMember]} pays ${names[settlement.toMember]}", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(7.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) { MoneyLabel(settlement.amountMinor, detail.group.currency) }
                                Button(onClick = { onPayment(settlement.fromMember, settlement.toMember, settlement.amountMinor) }) { Text("Record paid") }
                            }
                        }
                    }
                }
                else -> {
                    item {
                        SectionTitle("By category")
                        Text("Expenses only; repayments are excluded.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    val categories = detail.expenses.groupBy { it.category }.mapValues { it.value.sumOf { e -> e.amountMinor } }.entries.sortedByDescending { it.value }
                    if (categories.isEmpty()) item { EmptyState("No insights yet", "Add expenses to see where money goes.") }
                    items(categories) { entry ->
                        SurfaceCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${categoryEmoji(entry.key)}  ${entry.key}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                MoneyLabel(entry.value, detail.group.currency)
                            }
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(progress = { if (total == 0L) 0f else entry.value.toFloat() / total.toFloat() }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    item {
                        SectionTitle("Records & sharing")
                        SurfaceCard {
                            Text("Your group data stays on this device.", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { onShare(summary(detail, balances), "text/plain") }, modifier = Modifier.fillMaxWidth()) { Text("Share balance summary") }
                            OutlinedButton(onClick = { onShare(exportCsv(detail), "text/csv") }, modifier = Modifier.fillMaxWidth()) { Text("Export transactions as CSV") }
                        }
                    }
                }
            }
        }
    }
    if (archiveConfirm) {
        AlertDialog(
            onDismissRequest = { archiveConfirm = false },
            title = { Text(if (detail.group.archived) "Restore group?" else "Archive group?") },
            text = { Text(if (detail.group.archived) "Move this group back to Active." else "Move this group to Archived. All expenses and balances will be preserved.") },
            confirmButton = { TextButton(onClick = {
                vm.setArchived(detail.group.id, !detail.group.archived) {
                    archiveConfirm = false
                    onBack()
                }
            }) { Text(if (detail.group.archived) "Restore" else "Archive") } },
            dismissButton = { TextButton(onClick = { archiveConfirm = false }) { Text("Cancel") } }
        )
    }
    if (addMember) {
        TextEntryDialog("Add participant", "Name", onDismiss = { addMember = false }) { name ->
            vm.addMember(detail.group.id, name) { addMember = false }
        }
    }
    editMemberId?.let { id ->
        val member = detail.members.firstOrNull { it.id == id }
        if (member != null) {
            TextEntryDialog("Rename participant", "Name", initial = member.name, onDismiss = { editMemberId = null }) { name ->
                vm.renameMember(detail.group.id, id, name) { editMemberId = null }
            }
        }
    }
    if (rename) {
        TextEntryDialog("Rename group", "Group name", initial = detail.group.name, onDismiss = { rename = false }) { name ->
            vm.renameGroup(detail.group.id, name) { rename = false }
        }
    }
    deleteTransfer?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteTransfer = null },
            title = { Text("Delete this repayment?") },
            text = { Text("The balance will be recalculated as if this payment was never recorded.") },
            confirmButton = { TextButton(onClick = { vm.deleteTransfer(detail.group.id, id); deleteTransfer = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteTransfer = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun TextEntryDialog(title: String, label: String, initial: String = "", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        OutlinedTextField(value, onValueChange = { value = it }, label = { Text(label) }, singleLine = true)
    }, confirmButton = {
        TextButton(enabled = value.isNotBlank(), onClick = { onSave(value.trim()) }) { Text("Save") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

private fun categoryEmoji(category: String): String = when (category) {
    "Food" -> "🍽"; "Transport" -> "🚕"; "Stay" -> "🏠"; "Shopping" -> "🛍"; "Bills" -> "🧾"; "Entertainment" -> "🎟"; else -> "💳"
}

private fun date(timestamp: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))

private fun summary(detail: GroupDetail, balances: Map<String, Long>): String = buildString {
    appendLine("${detail.group.name} — Split summary")
    appendLine("Total: ${detail.group.currency} ${Money.format(detail.expenses.sumOf { it.amountMinor })}")
    detail.members.forEach { member ->
        val amount = balances[member.id] ?: 0L
        appendLine("${member.name}: ${if (amount > 0) "receives" else if (amount < 0) "owes" else "settled"} ${detail.group.currency} ${Money.format(kotlin.math.abs(amount))}")
    }
    appendLine("Suggested repayments:")
    SplitMath.settle(balances).forEach { p ->
        appendLine("${detail.members.first { it.id == p.fromMember }.name} → ${detail.members.first { it.id == p.toMember }.name}: ${detail.group.currency} ${Money.format(p.amountMinor)}")
    }
}

private fun exportCsv(detail: GroupDetail): String = buildString {
    val names = detail.members.associate { it.id to it.name }
    appendLine("type,date,title,from,to,amount,currency,category,note")
    detail.expenses.forEach { expense ->
        appendLine(listOf("expense", date(expense.timestamp), expense.title, names[expense.paidBy].orEmpty(), "", Money.input(expense.amountMinor), detail.group.currency, expense.category, expense.note).joinToString(",", transform = ::csvCell))
    }
    detail.contributions.forEach { contribution ->
        appendLine(listOf("contribution", "", "", names[contribution.memberId].orEmpty(), "", Money.input(contribution.amountMinor), detail.group.currency, "", "expense_id=${contribution.expenseId}").joinToString(",", transform = ::csvCell))
    }
    detail.allocations.forEach { allocation ->
        appendLine(listOf("split", "", "", "", names[allocation.memberId].orEmpty(), Money.input(allocation.amountMinor), detail.group.currency, "", "expense_id=${allocation.expenseId}").joinToString(",", transform = ::csvCell))
    }
    detail.transfers.forEach { transfer ->
        appendLine(listOf("repayment", date(transfer.timestamp), "Repayment", names[transfer.fromMember].orEmpty(), names[transfer.toMember].orEmpty(), Money.input(transfer.amountMinor), detail.group.currency, "", transfer.note).joinToString(",", transform = ::csvCell))
    }
}
