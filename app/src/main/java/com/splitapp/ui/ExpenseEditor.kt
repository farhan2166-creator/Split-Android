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
import com.splitapp.model.ExpenseSplitMode as Mode
import java.util.UUID

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
    var error by remember(expenseId) { mutableStateOf<String?>(null) }
    var saving by remember(expenseId) { mutableStateOf(false) }
    val draftId = remember(expenseId) { existing?.id ?: UUID.randomUUID().toString() }
    var showAdvanced by remember(expenseId) {
        mutableStateOf(multiplePayers || note.isNotBlank())
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
                    Button(enabled = !saving, onClick = {
                        val amount = Money.parse(amountText)
                        val allocations = if (amount == null || amount <= 0L || selected.isEmpty())
                            null else ExpenseSplitDraft.allocations(amount, selected.toList(), mode, inputs)
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
                                saving = true
                                val id = draftId
                                vm.saveExpense(
                                    Expense(id, detail.group.id, title.trim(), category,
                                        amount, payer, note.trim(), expenseDate),
                                    allocations.map { Allocation(id, it.key, it.value) },
                                    onBack,
                                    funding.map { it.copy(expenseId = id) },
                                    onError = { saving = false }
                                )
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        if (saving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
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
                    ExpenseSplitDraft.allocations(amount, selected.toList(), mode, inputs) else null
                detail.members.forEach { member ->
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 43.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = member.id in selected,
                            onCheckedChange = { checked ->
                                if (checked && member.id !in selected) {
                                    selected.add(member.id)
                                    if (mode != Mode.Equal && inputs[member.id].isNullOrBlank()) {
                                        inputs[member.id] = ExpenseSplitDraft.newParticipantInput(mode)
                                    }
                                }
                                if (!checked) {
                                    selected.remove(member.id)
                                    inputs.remove(member.id)
                                }
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
                    if (mode == Mode.Equal) "${selected.size} traveler(s) share this expense."
                    else "Only checked travelers are charged. Edit their amounts or parts above.",
                    color = Muted, style = MaterialTheme.typography.labelSmall
                )
                if (mode == Mode.Exact || mode == Mode.Percent) {
                    val assigned = ExpenseSplitDraft.enteredTotal(selected, mode, inputs)
                    val expected = if (mode == Mode.Percent) 10000L else amount
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            assigned == null -> "Check the entered values"
                            expected == null -> "Enter a valid total expense amount"
                            else -> "Assigned: ${Money.format(assigned)} / ${Money.format(expected)}" +
                                if (mode == Mode.Percent) "%" else " ${detail.group.currency}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (assigned != null && expected != null && assigned == expected)
                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
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
                            if (enabled) {
                                payerInputs.clear()
                                payerInputs[payer] = amountText
                            }
                            error = null
                        })
                    }
                    if (multiplePayers) {
                        Text("Enter only the actual amount each traveler paid.",
                            color = Muted, style = MaterialTheme.typography.labelSmall)
                        detail.members.forEach { member ->
                            OutlinedTextField(
                                value = payerInputs[member.id].orEmpty(),
                                onValueChange = { payerInputs[member.id] = it; error = null },
                                label = { Text("${member.name} paid · ${detail.group.currency}") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                        }
                        val paid = detail.members.sumOf { member ->
                            Money.parse(payerInputs[member.id].orEmpty()) ?: 0L
                        }
                        val bill = Money.parse(amountText)
                        Text(
                            if (bill == null) "Enter a valid bill total"
                            else "Contributions: ${Money.format(paid)} / ${Money.format(bill)} ${detail.group.currency}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (bill != null && paid == bill) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                        )
                        if (bill != null && paid < bill && payer in detail.members.map { it.id }) {
                            TextButton(onClick = {
                                val previous = Money.parse(payerInputs[payer].orEmpty()) ?: 0L
                                payerInputs[payer] = Money.input(previous + (bill - paid))
                                error = null
                            }) {
                                Text("Assign remaining ${Money.format(bill - paid)} to " +
                                    (detail.members.firstOrNull { it.id == payer }?.name ?: "payer"))
                            }
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
