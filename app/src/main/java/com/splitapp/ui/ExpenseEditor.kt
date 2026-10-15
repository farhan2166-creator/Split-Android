package com.splitapp.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import com.splitapp.data.SplitViewModel
import com.splitapp.model.*
import java.util.UUID

private enum class Mode(val title: String) { Equal("Equal"), Shares("Shares"), Percent("Percent"), Exact("Exact") }
private val categories = listOf("Food", "Transport", "Stay", "Groceries", "Shopping", "Entertainment", "Bills", "Healthcare", "Other")

@Composable
fun ExpenseEditor(detail: GroupDetail, expenseId: String?, vm: SplitViewModel, onBack: () -> Unit) {
    val existing = detail.expenses.firstOrNull { it.id == expenseId }
    var title by remember(expenseId) { mutableStateOf(existing?.title.orEmpty()) }
    var amountText by remember(expenseId) { mutableStateOf(existing?.let { Money.input(it.amountMinor) }.orEmpty()) }
    var note by remember(expenseId) { mutableStateOf(existing?.note.orEmpty()) }
    var expenseDate by remember(expenseId) { mutableLongStateOf(existing?.timestamp ?: System.currentTimeMillis()) }
    val context = LocalContext.current
    var category by remember(expenseId) { mutableStateOf(existing?.category ?: "Food") }
    var payer by remember(expenseId) { mutableStateOf(existing?.paidBy ?: detail.members.first().id) }
    var multiplePayers by remember(expenseId) { mutableStateOf(detail.contributions.count { it.expenseId == expenseId } > 1) }
    val payerInputs = remember(expenseId) {
        mutableStateMapOf<String, String>().apply {
            detail.contributions.filter { it.expenseId == expenseId }.forEach { put(it.memberId, Money.input(it.amountMinor)) }
        }
    }
    var mode by remember(expenseId) { mutableStateOf(if (existing == null) Mode.Equal else Mode.Exact) }
    val original = detail.allocations.filter { it.expenseId == expenseId }
    val selected = remember(expenseId) {
        mutableStateListOf<String>().apply { addAll(if (existing == null) detail.members.map { it.id } else original.map { it.memberId }) }
    }
    val inputs = remember(expenseId) {
        mutableStateMapOf<String, String>().apply {
            original.forEach { put(it.memberId, Money.input(it.amountMinor)) }
        }
    }
    var error by remember { mutableStateOf<String?>(null) }
    var showAdvanced by remember(expenseId) {
        mutableStateOf(multiplePayers || mode != Mode.Equal || note.isNotBlank())
    }
    var splitMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    var customCategory by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun initializeInputs(newMode: Mode) {
        val ids = selected.toList()
        val amount = Money.parse(amountText) ?: 0L
        inputs.clear()
        when (newMode) {
            Mode.Equal -> Unit
            Mode.Shares -> ids.forEach { inputs[it] = "1" }
            Mode.Percent -> if (ids.isNotEmpty()) SplitMath.allocate(10000, ids.associateWith { 1L }).forEach { (id, minor) -> inputs[id] = Money.input(minor) }
            Mode.Exact -> if (amount > 0 && ids.isNotEmpty()) SplitMath.allocate(amount, ids.associateWith { 1L }).forEach { (id, minor) -> inputs[id] = Money.input(minor) }
        }
    }

    val categoryOptions = remember(detail.expenses) {
        (categories + detail.expenses.map { it.category }).distinct()
    }
    Scaffold(
        topBar = {
            AppHeader(if (existing == null) "Add expense" else "Edit expense", detail.group.name, onBack) {
                if (existing != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        bottomBar = {
            Surface(modifier = Modifier.navigationBarsPadding(), shadowElevation = 5.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp)) {
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(5.dp))
                    }
                    Button(onClick = {
                        val amount = Money.parse(amountText)
                        val allocations = if (amount == null || amount <= 0L || selected.isEmpty())
                            null else computeAllocations(amount, selected.toList(), mode, inputs)
                        val funding = if (amount == null || amount <= 0L) null
                            else if (!multiplePayers) {
                                listOf(Contribution(existing?.id ?: "", payer, amount))
                            } else {
                                if (payerInputs.values.any {
                                    it.isNotBlank() && Money.parse(it) == null
                                }) null else detail.members.mapNotNull { member ->
                                    val paid = Money.parse(payerInputs[member.id].orEmpty()) ?: 0L
                                    if (paid > 0L) Contribution(existing?.id ?: "", member.id, paid)
                                    else null
                                }.takeIf { parts ->
                                    parts.isNotEmpty() && parts.sumOf { it.amountMinor } == amount
                                }
                            }
                        when {
                            title.isBlank() -> error = "Enter an expense title"
                            amount == null || amount <= 0L -> error = "Enter a valid amount"
                            selected.isEmpty() -> error = "Select at least one traveler"
                            funding == null -> {
                                showAdvanced = true
                                error = "Amounts paid must add up exactly to the total"
                            }
                            allocations == null -> error = when (mode) {
                                Mode.Percent -> "Percentages must total exactly 100%"
                                Mode.Exact -> "Assigned amounts must equal the total"
                                else -> "Enter a valid share for each selected traveler"
                            }
                            else -> {
                                error = null
                                val id = existing?.id ?: UUID.randomUUID().toString()
                                vm.saveExpense(
                                    Expense(id, detail.group.id, title.trim(), category,
                                        amount, payer, note.trim(), expenseDate),
                                    allocations.map { Allocation(id, it.key, it.value) },
                                    onBack,
                                    funding.map { it.copy(expenseId = id) }
                                )
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (existing == null) "Add expense" else "Save changes")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactPanel {
                Text("EXPENSE DETAILS", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(7.dp))
                OutlinedTextField(
                    value = title, onValueChange = { title = it; error = null },
                    label = { Text("Title") },
                    placeholder = { Text("Hotel, fuel, dinner...") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it; error = null },
                    label = { Text("Amount · ${detail.group.currency}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    prefix = { Text("${detail.group.currency} ") }
                )
                Spacer(Modifier.height(8.dp))
                Text("Paid by", style = MaterialTheme.typography.labelMedium,
                    color = Muted)
                if (!multiplePayers) {
                    PersonPicker(detail.members, payer) { payer = it }
                } else {
                    TextButton(onClick = { showAdvanced = true }) {
                        Text("Multiple payers selected · Edit contributions")
                    }
                }
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { categoryMenu = true },
                            contentPadding = PaddingValues(horizontal = 9.dp)
                        ) {
                            Text("${categoryEmoji(category)}  $category",
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Spacer(Modifier.width(4.dp))
                            Text("⌄")
                        }
                        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            categoryOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text("${categoryEmoji(option)}  $option") },
                                    onClick = { category = option; categoryMenu = false }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("+ Custom category") },
                                onClick = { categoryMenu = false; customCategory = true })
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val calendar = Calendar.getInstance().apply { timeInMillis = expenseDate }
                            android.app.DatePickerDialog(context, { _, year, month, day ->
                                expenseDate = Calendar.getInstance().apply {
                                    timeInMillis = expenseDate
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, day)
                                }.timeInMillis
                            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)).show()
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 7.dp)
                    ) {
                        Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(expenseDate)),
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
            CompactPanel {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Split between", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { splitMenu = true }) {
                            Text(mode.title + "  ⌄")
                        }
                        DropdownMenu(expanded = splitMenu, onDismissRequest = { splitMenu = false }) {
                            Mode.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(when(option) {
                                        Mode.Equal -> "Equally"
                                        Mode.Shares -> "As parts"
                                        Mode.Percent -> "As percentages"
                                        Mode.Exact -> "As amounts"
                                    }) },
                                    leadingIcon = {
                                        if (mode == option) Text("✓")
                                    },
                                    onClick = {
                                        mode = option
                                        initializeInputs(option)
                                        splitMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
                val amount = Money.parse(amountText)
                val preview = if (amount != null && amount > 0L)
                    computeAllocations(amount, selected.toList(), mode, inputs) else null
                detail.members.forEach { member ->
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 43.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = member.id in selected,
                            onCheckedChange = { checked ->
                                if (checked && member.id !in selected) selected.add(member.id)
                                if (!checked) selected.remove(member.id)
                                if (mode != Mode.Equal) initializeInputs(mode)
                                error = null
                            }
                        )
                        Text(member.name, modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium)
                        if (mode == Mode.Equal) {
                            Text(
                                preview?.get(member.id)?.let { Money.format(it) } ?: "—",
                                color = if (member.id in selected) MaterialTheme.colorScheme.primary else Muted,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else if (member.id in selected) {
                            OutlinedTextField(
                                value = inputs[member.id].orEmpty(),
                                onValueChange = { inputs[member.id] = it; error = null },
                                modifier = Modifier.width(105.dp),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                suffix = { if (mode == Mode.Percent) Text("%") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }
                    }
                }
                Text(
                    if (mode == Mode.Equal) "Only checked travelers share this expense."
                    else "Enter each selected traveler's share. The total must match.",
                    color = Muted, style = MaterialTheme.typography.labelSmall
                )
            }
            TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showAdvanced) "Hide extra options  ⌃" else "Multi-payer bill or note?  More options  ⌄")
            }
            if (showAdvanced) {
                CompactPanel {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Paid by multiple travelers", modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = multiplePayers, onCheckedChange = { enabled ->
                            multiplePayers = enabled
                            if (enabled && payerInputs.isEmpty()) payerInputs[payer] = amountText
                        })
                    }
                    if (multiplePayers) {
                        Text("Enter only the actual amount each traveler paid.",
                            color = Muted, style = MaterialTheme.typography.labelSmall)
                        detail.members.forEach { member ->
                            OutlinedTextField(
                                value = payerInputs[member.id].orEmpty(),
                                onValueChange = { payerInputs[member.id] = it },
                                label = { Text("${member.name} paid · ${detail.group.currency}") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = note, onValueChange = { note = it },
                        label = { Text("Note (optional)") },
                        minLines = 1, maxLines = 2, modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
    if (customCategory) {
        TextEntryDialog(
            title = "Add a category",
            label = "Category name",
            onDismiss = { customCategory = false }
        ) { value ->
            if (value.isNotBlank()) {
                category = value.trim().take(40)
                customCategory = false
            }
        }
    }
    if (confirmDelete && existing != null) {
        AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete expense?") },
            text = { Text("This will remove the expense and recalculate every balance. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { vm.deleteExpense(detail.group.id, existing.id, onBack); confirmDelete = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

private fun computeAllocations(total: Long, selected: List<String>, mode: Mode, inputs: Map<String, String>): Map<String, Long>? {
    if (selected.isEmpty() || total <= 0) return null
    return when (mode) {
        Mode.Equal -> SplitMath.allocate(total, selected.associateWith { 1L })
        Mode.Shares -> {
            val weights = selected.associateWith { inputs[it]?.toLongOrNull() ?: return null }
            if (weights.values.any { it <= 0 }) null else SplitMath.allocate(total, weights)
        }
        Mode.Percent -> {
            val percent = selected.associateWith { Money.parse(inputs[it].orEmpty()) ?: return null }
            if (percent.values.any { it <= 0 } || percent.values.sum() != 10000L) null
            else SplitMath.allocate(total, percent)
        }
        Mode.Exact -> {
            val pieces = selected.associateWith { Money.parse(inputs[it].orEmpty()) ?: return null }
            if (pieces.values.sum() != total) null else pieces
        }
    }
}
