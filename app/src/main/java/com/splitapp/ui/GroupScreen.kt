package com.splitapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
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
    onLedger: () -> Unit,
    onTourStatement: (String?) -> Unit,
    onShare: (String, String) -> Unit,
    selectedTab: Int, onTabChange: (Int) -> Unit
) {
    val tab = selectedTab
    var addMember by remember { mutableStateOf(false) }
    var editMemberId by remember { mutableStateOf<String?>(null) }
    var rename by remember { mutableStateOf(false) }
    var archiveConfirm by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var manageTravelers by remember { mutableStateOf(false) }
    var suggestionsOpen by remember { mutableStateOf(false) }
    var searchExpanded by remember(detail.group.id) { mutableStateOf(false) }
    var search by remember(detail.group.id) { mutableStateOf("") }
    var categoryFilter by remember(detail.group.id) { mutableStateOf("All") }
    var deleteTransfer by remember { mutableStateOf<String?>(null) }
    val balances = remember(detail) { SplitMath.balances(detail) }
    val settlements = remember(balances) { SplitMath.settle(balances) }
    val names = detail.members.associate { it.id to it.name }
    val total = detail.expenses.sumOf { it.amountMinor }
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("split_group_identity", android.content.Context.MODE_PRIVATE)
    }
    val identityKey = "member_${detail.group.id}"
    var myMemberId by remember(detail.group.id) {
        mutableStateOf(preferences.getString(identityKey, null))
    }
    var showMePicker by remember(detail.group.id) { mutableStateOf(false) }
    val statement = remember(detail) { TourSettlementBuilder.build(detail) }
    val myStatement = statement.members.firstOrNull { it.member.id == myMemberId }
    Scaffold(
        topBar = {
            AppHeader(detail.group.name, "${detail.members.size} travelers · ${detail.group.currency}", onBack) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        searchExpanded = !searchExpanded
                        if (!searchExpanded) {
                            search = ""
                            categoryFilter = "All"
                        }
                        onTabChange(0)
                    }) {
                        Text("⌕", style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Text("⋮", style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("Manage travelers") }, onClick = {
                                menuExpanded = false; manageTravelers = true
                            })
                            DropdownMenuItem(text = { Text("Edit group name") }, onClick = {
                                menuExpanded = false; rename = true
                            })
                            DropdownMenuItem(text = { Text("Share balances") }, onClick = {
                                menuExpanded = false
                                onShare(summary(detail, balances), "text/plain")
                            })
                            DropdownMenuItem(text = { Text("Spending insights") }, onClick = {
                                menuExpanded = false; onTabChange(2)
                            })
                            DropdownMenuItem(text = { Text("Detailed PDF ledger") }, onClick = {
                                menuExpanded = false; onLedger()
                            })
                            DropdownMenuItem(text = { Text(if (detail.group.archived) "Restore group" else "Archive group") },
                                onClick = { menuExpanded = false; archiveConfirm = true })
                        }
                    }
                }
            }
        },
        bottomBar = {
            SplitBottomTabs(
                listOf("Activity" to "▤", "Balances" to "⇄", "Insights" to "◉", "Overview" to "▦"),
                tab
            ) { onTabChange(it) }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            // Keep other tabs uncluttered: summary metrics belong to Overview only.
            if (tab == 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    CompactQuickAction("Expense", "+", { onExpense(null) }, Modifier.weight(1f))
                    CompactQuickAction("Repay", "↗", { onPayment(null, null, null) }, Modifier.weight(1f))
                    CompactQuickAction("Settle PDF", "▤", { onTourStatement(myMemberId) }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(5.dp))
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
            when (tab) {
                0 -> {
                    item {
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Recent activity", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                            TextButton(onClick = { manageTravelers = true }) {
                                Text("${detail.members.size} travelers  ›")
                            }
                        }
                        if (searchExpanded) {
                            OutlinedTextField(
                                value = search, onValueChange = { search = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Search activity") },
                                singleLine = true,
                                trailingIcon = {
                                    TextButton(onClick = { search = ""; categoryFilter = "All"; searchExpanded = false }) { Text("✕") }
                                }
                            )
                            Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                (listOf("All") + detail.expenses.map { it.category }.distinct()).forEach { value ->
                                    FilterChip(selected = categoryFilter == value,
                                        onClick = { categoryFilter = value }, label = { Text(value) })
                                }
                            }
                        }
                    }
                    val activity: List<Any> = (detail.expenses.filter { expense ->
                        (categoryFilter == "All" || expense.category == categoryFilter) &&
                        (search.isBlank() || expense.title.contains(search, true) || expense.category.contains(search, true) || names[expense.paidBy].orEmpty().contains(search, true) || expense.note.contains(search, true))
                    } + detail.transfers.filter { transfer ->
                        categoryFilter == "All" && (search.isBlank() ||
                        (if (transfer.kind == TransferKind.ADVANCE) "cash advance" else "repayment").contains(search, true) || names[transfer.fromMember].orEmpty().contains(search, true) || names[transfer.toMember].orEmpty().contains(search, true) || transfer.note.contains(search, true))
                    }).sortedByDescending {
                        when (it) { is Expense -> it.timestamp; is Transfer -> it.timestamp; else -> 0L }
                    }
                    if (activity.isEmpty()) item { EmptyState(if (detail.expenses.isEmpty() && detail.transfers.isEmpty()) "No transactions yet" else "No matching activity", if (detail.expenses.isEmpty() && detail.transfers.isEmpty()) "Add an expense such as dinner, tickets or rent." else "Clear the search or change the category.") }
                    var lastDay: String? = null
                    activity.forEach { entry ->
                        val transactionTime = when (entry) {
                            is Expense -> entry.timestamp
                            is Transfer -> entry.timestamp
                            else -> 0L
                        }
                        val day = java.text.DateFormat.getDateInstance(DateFormat.LONG)
                            .format(Date(transactionTime))
                        if (day != lastDay) {
                            item(key = "date_${transactionTime}_${day}") {
                                Text(day, color = Muted, fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(start = 5.dp, top = 9.dp, bottom = 3.dp))
                            }
                            lastDay = day
                        }
                        item(key = when (entry) {
                            is Expense -> "e${entry.id}"
                            is Transfer -> "t${entry.id}"
                            else -> ""
                        }) {
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
                                        Text(if (entry.kind == TransferKind.ADVANCE) "Cash advance" else "Repayment", fontWeight = FontWeight.SemiBold)
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
                }
                1 -> {
                    item {
                        PersonalBalanceBanner(
                            myStatement?.balanceMinor, detail.group.currency,
                            onChooseMe = { showMePicker = true },
                            onViewSuggestions = { suggestionsOpen = true }
                        )
                        SectionTitle("Balances", "${detail.members.size} travelers")
                    }
                    items(detail.members, key = { it.id }) { member ->
                        TravelerBalanceCard(member, balances[member.id] ?: 0L,
                            detail.group.currency, member.id == myMemberId)
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
                2 -> {
                    item {
                        SectionTitle("Plan your spending")
                        BudgetPanel(detail, vm)
                    }
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
                    item { SectionTitle("Spending by member") }
                    val spendByMember = detail.members.associate { member ->
                        member.id to detail.expenses.sumOf { expense ->
                            detail.allocations.filter { it.expenseId == expense.id && it.memberId == member.id }.sumOf { it.amountMinor }
                        }
                    }
                    items(detail.members, key = { "spent_" + it.id }) { member ->
                        SurfaceCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(member.name, Modifier.weight(1f))
                                MoneyLabel(spendByMember[member.id] ?: 0L, detail.group.currency)
                            }
                        }
                    }
                    item { SectionTitle("Monthly spending") }
                    val monthly = detail.expenses.groupBy {
                        java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.ROOT).format(Date(it.timestamp))
                    }.mapValues { pair -> pair.value.sumOf { it.amountMinor } }.toSortedMap(compareByDescending { it })
                    items(monthly.entries.toList(), key = { "month_" + it.key }) { entry ->
                        SurfaceCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(entry.key, Modifier.weight(1f))
                                MoneyLabel(entry.value, detail.group.currency)
                            }
                        }
                    }
                    item {
                        SectionTitle("Records & sharing")
                        SurfaceCard {
                            Text("Your group data stays on this device.", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { onShare(summary(detail, balances), "text/plain") }, modifier = Modifier.fillMaxWidth()) { Text("Share balance summary") }
                            OutlinedButton(onClick = { onTourStatement(myMemberId) }, modifier = Modifier.fillMaxWidth()) { Text("One-page tour settlement PDF") }
                            OutlinedButton(onClick = onLedger, modifier = Modifier.fillMaxWidth()) { Text("Detailed PDF ledger: people and date range") }
                            OutlinedButton(onClick = { onShare(exportCsv(detail), "text/csv") }, modifier = Modifier.fillMaxWidth()) { Text("Export transactions as CSV") }
                        }
                    }
                }
                3 -> {
                    item {
                        Spacer(Modifier.height(5.dp))
                        TourOverview(statement, myStatement) { showMePicker = true }
                        Spacer(Modifier.height(11.dp))
                        SectionTitle("Tour settlement")
                        Text(
                            "The same four figures are included in the single-page PDF, " +
                                "together with every traveler's contributions, shares and balances.",
                            style = MaterialTheme.typography.bodySmall, color = Muted
                        )
                        Spacer(Modifier.height(9.dp))
                        Button(
                            onClick = { onTourStatement(myMemberId) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text("Save one-page settlement PDF")
                        }
                    }
                }
            }
            }
        }
    }
    if (suggestionsOpen) {
        AlertDialog(
            onDismissRequest = { suggestionsOpen = false },
            title = { Text("Suggested settlements") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (settlements.isEmpty()) Text("Everyone is settled.", color = Muted)
                    settlements.forEach { payment ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${names[payment.fromMember]} → ${names[payment.toMember]}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text("${detail.group.currency} ${Money.format(payment.amountMinor)}",
                                    style = MaterialTheme.typography.labelMedium, color = Muted)
                            }
                            TextButton(onClick = {
                                suggestionsOpen = false
                                onPayment(payment.fromMember, payment.toMember, payment.amountMinor)
                            }) { Text("Record") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { suggestionsOpen = false }) { Text("Close") } }
        )
    }
    if (manageTravelers) {
        AlertDialog(
            onDismissRequest = { manageTravelers = false },
            title = { Text("Travelers") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Tap a traveler to rename them. Choose Me to identify yourself on this phone.",
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                    detail.members.forEach { person ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = androidx.compose.foundation.shape.CircleShape) {
                                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                                    Text(person.name.take(1).uppercase(), fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = {
                                manageTravelers = false
                                editMemberId = person.id
                            }, modifier = Modifier.weight(1f)) {
                                Text(person.name, maxLines = 1)
                            }
                            if (person.id == myMemberId) {
                                Text("Me", color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium)
                            } else {
                                TextButton(onClick = {
                                    myMemberId = person.id
                                    preferences.edit().putString(identityKey, person.id).apply()
                                }) { Text("Set Me") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { manageTravelers = false; addMember = true }) {
                    Text("+ Add traveler")
                }
            },
            dismissButton = {
                TextButton(onClick = { manageTravelers = false }) { Text("Close") }
            }
        )
    }
    if (showMePicker) {
        AlertDialog(
            onDismissRequest = { showMePicker = false },
            title = { Text("Who am I in this group?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Select your traveler name. Split remembers it on this phone only.",
                        color = Muted, style = MaterialTheme.typography.bodySmall)
                    detail.members.forEach { person ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = myMemberId == person.id,
                                onClick = {
                                    myMemberId = person.id
                                    preferences.edit().putString(identityKey, person.id).apply()
                                    showMePicker = false
                                }
                            )
                            TextButton(onClick = {
                                myMemberId = person.id
                                preferences.edit().putString(identityKey, person.id).apply()
                                showMePicker = false
                            }) { Text(person.name) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMePicker = false }) { Text("Close") }
            },
            dismissButton = {
                TextButton(onClick = {
                    myMemberId = null
                    preferences.edit().remove(identityKey).apply()
                    showMePicker = false
                }) { Text("Clear Me") }
            }
        )
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
            text = { Text("Removing this cash movement recalculates everyone’s balance. This cannot be undone.") },
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

internal fun categoryEmoji(category: String): String = when (category) {
    "Food" -> "🍽"; "Transport" -> "🚕"; "Stay" -> "🛏"
    "Groceries" -> "🛒"; "Shopping" -> "🛍"; "Bills" -> "🧾"
    "Entertainment" -> "🎟"; "Healthcare" -> "⚕"; else -> "💳"
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
        appendLine(listOf(if (transfer.kind == TransferKind.ADVANCE) "advance" else "repayment", date(transfer.timestamp), if (transfer.kind == TransferKind.ADVANCE) "Cash advance" else "Repayment", names[transfer.fromMember].orEmpty(), names[transfer.toMember].orEmpty(), Money.input(transfer.amountMinor), detail.group.currency, "", transfer.note).joinToString(",", transform = ::csvCell))
    }
}
