package com.splitapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.splitapp.data.SplitViewModel
import com.splitapp.model.*
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Monthly limits are guidance, not a restriction on legitimate expenses. */
@Composable
fun BudgetPanel(detail: GroupDetail, vm: SplitViewModel) {
    var month by remember(detail.group.id) { mutableStateOf(YearMonth.now()) }
    var editing by remember(detail.group.id) { mutableStateOf(false) }
    val summary = remember(detail, month) { BudgetMath.forMonth(detail, month) }
    val label = remember(month) { month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())) }
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("MONTHLY BUDGET", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = { editing = true }) {
                Text(if (summary.limitMinor == null) "Set limit" else "Edit limit")
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("${summary.expensesCount} expenses", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            IconButton(onClick = { month = month.plusMonths(1) },
                enabled = month < YearMonth.now()) { Text("›") }
        }
        Spacer(Modifier.height(6.dp))
        Text("Spent", style = MaterialTheme.typography.labelMedium, color = Muted)
        MoneyLabel(summary.spentMinor, detail.group.currency, large = true)
        val limit = summary.limitMinor
        if (limit != null) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { summary.progress }, modifier = Modifier.fillMaxWidth(),
                color = if (summary.health == BudgetHealth.OVER_LIMIT)
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            val remaining = summary.remainingMinor ?: 0L
            Text(
                if (remaining < 0L) "Over budget by ${detail.group.currency} ${Money.format(-remaining)}"
                else "${detail.group.currency} ${Money.format(remaining)} remaining of ${Money.format(limit)}",
                color = if (summary.health == BudgetHealth.OVER_LIMIT) MaterialTheme.colorScheme.error else Muted,
                style = MaterialTheme.typography.bodyMedium
            )
            if (summary.health == BudgetHealth.NEAR_LIMIT) {
                Text("Approaching your monthly limit", color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelMedium)
            }
        } else {
            Spacer(Modifier.height(6.dp))
            Text("Set a monthly limit to track how much remains.", style = MaterialTheme.typography.bodySmall,
                color = Muted)
        }
        Text("Repayments never count as spending. Past months use their recorded expense dates.",
            style = MaterialTheme.typography.labelSmall, color = Muted)
    }
    if (editing) {
        var value by remember(detail.group.id, editing) {
            mutableStateOf(detail.group.monthlyBudgetMinor?.let(Money::input).orEmpty())
        }
        val parsed = Money.parse(value)
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Group monthly budget") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("This amount applies to every calendar month and can be changed anytime.")
                    OutlinedTextField(
                        value = value, onValueChange = { value = it },
                        label = { Text("Monthly limit · ${detail.group.currency}") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    Text("This does not block adding expenses or change any balances.",
                        color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = parsed != null && parsed > 0L, onClick = {
                    vm.setMonthlyBudget(detail.group.id, parsed) { editing = false }
                }) { Text("Save limit") }
            },
            dismissButton = {
                Row {
                    if (detail.group.monthlyBudgetMinor != null) {
                        TextButton(onClick = { vm.setMonthlyBudget(detail.group.id, null) { editing = false } }) {
                            Text("Remove")
                        }
                    }
                    TextButton(onClick = { editing = false }) { Text("Cancel") }
                }
            }
        )
    }
}
