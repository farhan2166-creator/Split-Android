package com.splitapp.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.splitapp.model.*
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

private enum class ScopeMode(val title: String) {
    GROUP("Full group"), SINGLE("One person"), MULTIPLE("Selected people")
}

private enum class DateMode(val title: String) {
    ALL("All time"), THIS_MONTH("This month"), LAST_MONTH("Last month"), CUSTOM("Custom range")
}

private fun dayStart(timestamp: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timestamp
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun nextDay(timestamp: Long): Long = Calendar.getInstance().apply {
    timeInMillis = dayStart(timestamp)
    add(Calendar.DAY_OF_MONTH, 1)
}.timeInMillis

private fun calendarMonth(offset: Int): Pair<Long, Long> {
    val start = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.MONTH, offset)
    }
    val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
    return start.timeInMillis to end.timeInMillis
}

@Composable
fun LedgerScreen(
    detail: GroupDetail,
    onBack: () -> Unit,
    onSave: (LedgerReport) -> Unit
) {
    val context = LocalContext.current
    val members = detail.members
    var scope by remember(detail.group.id) { mutableStateOf(ScopeMode.GROUP) }
    var dateMode by remember(detail.group.id) { mutableStateOf(DateMode.ALL) }
    var oneMember by remember(detail.group.id) { mutableStateOf(members.firstOrNull()?.id.orEmpty()) }
    val selected = remember(detail.group.id) { mutableStateListOf<String>() }
    var startDate by remember(detail.group.id) { mutableLongStateOf(dayStart(System.currentTimeMillis())) }
    var endDate by remember(detail.group.id) { mutableLongStateOf(dayStart(System.currentTimeMillis())) }
    var error by remember { mutableStateOf<String?>(null) }

    fun pickDate(initial: Long, onPick: (Long) -> Unit) {
        val c = Calendar.getInstance().apply { timeInMillis = initial }
        DatePickerDialog(context, { _, year, month, day ->
            val selectedDay = Calendar.getInstance().apply {
                set(year, month, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            onPick(selectedDay.timeInMillis)
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    Scaffold(
        topBar = { AppHeader("PDF Ledger", detail.group.name, onBack) },
        bottomBar = {
            Surface(shadowElevation = 6.dp, modifier = Modifier.navigationBarsPadding()) {
                Button(
                    enabled = members.isNotEmpty(),
                    onClick = {
                        val ids = when (scope) {
                            ScopeMode.GROUP -> members.map { it.id }.toSet()
                            ScopeMode.SINGLE -> setOf(oneMember)
                            ScopeMode.MULTIPLE -> selected.toSet()
                        }
                        val bounds = when (dateMode) {
                            DateMode.ALL -> LedgerRange()
                            DateMode.THIS_MONTH -> calendarMonth(0).let { LedgerRange(it.first, it.second, "This month") }
                            DateMode.LAST_MONTH -> calendarMonth(-1).let { LedgerRange(it.first, it.second, "Last month") }
                            DateMode.CUSTOM -> if (startDate <= endDate) {
                                LedgerRange(startDate, nextDay(endDate),
                                    "${DateFormat.getDateInstance().format(Date(startDate))} to ${DateFormat.getDateInstance().format(Date(endDate))}")
                            } else null
                        }
                        when {
                            ids.isEmpty() -> error = "Select at least one participant"
                            bounds == null -> error = "Start date cannot be after end date"
                            else -> try {
                                error = null
                                onSave(LedgerReportBuilder.build(detail, ids, bounds))
                            } catch (e: Exception) {
                                error = e.message ?: "Unable to prepare ledger"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)
                ) { Text("Save PDF ledger") }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SurfaceCard {
                Text("Choose report coverage", style = MaterialTheme.typography.titleMedium)
                Text("Group ledger, one person or multiple selected people.", style = MaterialTheme.typography.bodySmall, color = Muted)
                Spacer(Modifier.height(10.dp))
                ScopeMode.entries.forEach { choice ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = scope == choice, onClick = { scope = choice })
                        Text(choice.title)
                    }
                }
                if (scope == ScopeMode.SINGLE) {
                    Spacer(Modifier.height(8.dp))
                    Text("Participant")
                    PersonPicker(members, oneMember) { oneMember = it }
                }
                if (scope == ScopeMode.MULTIPLE) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { selected.clear(); selected.addAll(members.map { it.id }) }) { Text("Select all") }
                        TextButton(onClick = { selected.clear() }) { Text("Clear") }
                    }
                    members.forEach { member ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(
                                checked = member.id in selected,
                                onCheckedChange = { checked ->
                                    if (checked && member.id !in selected) selected.add(member.id)
                                    else if (!checked) selected.remove(member.id)
                                }
                            )
                            Text(member.name)
                        }
                    }
                }
            }
            SurfaceCard {
                Text("Date range", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                DateMode.entries.forEach { option ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = dateMode == option, onClick = { dateMode = option })
                        Text(option.title)
                    }
                }
                if (dateMode == DateMode.CUSTOM) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { pickDate(startDate) { startDate = it } }, modifier = Modifier.fillMaxWidth()) {
                        Text("From: ${DateFormat.getDateInstance().format(Date(startDate))}")
                    }
                    OutlinedButton(onClick = { pickDate(endDate) { endDate = it } }, modifier = Modifier.fillMaxWidth()) {
                        Text("To (inclusive): ${DateFormat.getDateInstance().format(Date(endDate))}")
                    }
                }
            }
            SurfaceCard {
                Text("Included in the PDF", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Opening balance, expense payments, each person's allocated shares, repayments, running balances and closing balance. Includes a group activity appendix.", color = Muted)
                Spacer(Modifier.height(8.dp))
                Text("The report is calculated from existing records. Saving a PDF does not change any expenses or balances.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
