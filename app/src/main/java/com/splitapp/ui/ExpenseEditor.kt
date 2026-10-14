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
import com.splitapp.data.SplitViewModel
import com.splitapp.model.*
import java.util.UUID

private enum class Mode(val title: String) { Equal("Equal"), Shares("Shares"), Percent("Percent"), Exact("Exact") }
private val categories = listOf("Food", "Transport", "Stay", "Shopping", "Bills", "Entertainment", "Other")

@Composable
fun ExpenseEditor(detail: GroupDetail, expenseId: String?, vm: SplitViewModel, onBack: () -> Unit) {
    val existing = detail.expenses.firstOrNull { it.id == expenseId }
    var title by remember(expenseId) { mutableStateOf(existing?.title.orEmpty()) }
    var amountText by remember(expenseId) { mutableStateOf(existing?.let { Money.input(it.amountMinor) }.orEmpty()) }
    var note by remember(expenseId) { mutableStateOf(existing?.note.orEmpty()) }
    var category by remember(expenseId) { mutableStateOf(existing?.category ?: "Food") }
    var payer by remember(expenseId) { mutableStateOf(existing?.paidBy ?: detail.members.first().id) }
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

    Scaffold(
        topBar = {
            AppHeader(if (existing == null) "New expense" else "Edit expense", detail.group.name, onBack) {
                if (existing != null) TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        },
        bottomBar = {
            Surface(modifier = Modifier.navigationBarsPadding(), shadowElevation = 5.dp) {
                Button(onClick = {
                    val amount = Money.parse(amountText)
                    val allocations = if (amount == null || amount <= 0 || selected.isEmpty()) null else computeAllocations(amount, selected.toList(), mode, inputs)
                    when {
                        title.isBlank() -> error = "Enter a description"
                        amount == null || amount <= 0 -> error = "Enter a valid amount (up to 2 decimal places)"
                        selected.isEmpty() -> error = "Choose at least one person to split with"
                        allocations == null -> error = when (mode) {
                            Mode.Percent -> "Percentages must add up to exactly 100%"
                            Mode.Exact -> "Exact amounts must add up to the total"
                            else -> "Enter positive shares for everyone selected"
                        }
                        else -> {
                            error = null
                            val id = existing?.id ?: UUID.randomUUID().toString()
                            vm.saveExpense(
                                Expense(id, detail.group.id, title.trim(), category, amount, payer, note.trim(), existing?.timestamp ?: System.currentTimeMillis()),
                                allocations.map { Allocation(id, it.key, it.value) }, onBack
                            )
                        }
                    }
                }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Text(if (existing == null) "Save expense" else "Save changes")
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            SurfaceCard {
                Text("EXPENSE DETAILS", color = Muted, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(title, { title = it }, label = { Text("What was this for?") }, placeholder = { Text("Dinner, hotel, groceries...") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    amountText, { amountText = it }, label = { Text("Amount · ${detail.group.currency}") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("Category", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(5.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    categories.forEach { item -> FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) }) }
                }
                Spacer(Modifier.height(12.dp))
                Text("Paid by", style = MaterialTheme.typography.labelLarge)
                PersonPicker(detail.members, payer) { payer = it }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3)
            }
            SurfaceCard {
                Text("SPLIT BETWEEN", color = Muted, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Text("Choose who benefited. The person who paid can be included or excluded.", color = Muted, style = MaterialTheme.typography.bodySmall)
                detail.members.forEach { member ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(
                            checked = member.id in selected,
                            onCheckedChange = { checked ->
                                if (checked && member.id !in selected) selected.add(member.id)
                                if (!checked) selected.remove(member.id)
                                if (mode != Mode.Equal) initializeInputs(mode)
                            }
                        )
                        Text(member.name, modifier = Modifier.weight(1f))
                        if (mode != Mode.Equal && member.id in selected) {
                            OutlinedTextField(
                                value = inputs[member.id].orEmpty(), onValueChange = { inputs[member.id] = it },
                                modifier = Modifier.width(108.dp), singleLine = true,
                                suffix = { if (mode == Mode.Percent) Text("%") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Split method", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Mode.entries.forEach { option ->
                        FilterChip(selected = mode == option, onClick = { mode = option; initializeInputs(option) }, label = { Text(option.title) })
                    }
                }
                val amount = Money.parse(amountText)
                val preview = if (amount != null && amount > 0) computeAllocations(amount, selected.toList(), mode, inputs) else null
                if (preview != null) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))
                    detail.members.filter { it.id in selected }.forEach { member ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(member.name, color = Muted)
                            Text("${detail.group.currency} ${Money.format(preview[member.id] ?: 0)}", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(5.dp))
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
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
