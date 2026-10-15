package com.splitapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.splitapp.data.SplitViewModel
import com.splitapp.model.SplitGroup
import java.text.DateFormat
import java.util.Date

@Composable
fun GroupsScreen(
    groups: List<SplitGroup>, vm: SplitViewModel, onOpen: (String) -> Unit,
    appearance: AppearanceMode, onAppearanceChange: (AppearanceMode) -> Unit,
    onExportBackup: () -> Unit, onImportBackup: () -> Unit,
    selectedTab: Int, onTabChange: (Int) -> Unit,
    filter: String, onFilterChange: (String) -> Unit
) {
    val context = LocalContext.current
    val packageInfo = remember(context) {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    var showCreate by remember { mutableStateOf(false) }
    val tab = selectedTab
    val archived = tab == 1
    val visibleGroups = remember(groups, filter, archived) {
        groups.filter {
            it.archived == archived &&
                (it.name.contains(filter.trim(), ignoreCase = true) ||
                 it.currency.contains(filter.trim(), ignoreCase = true))
        }
    }
    Scaffold(
        topBar = {
            AppHeader(
                if (tab == 2) "Your Split" else "Split",
                when (tab) {
                    1 -> "Finished groups, safely kept"
                    2 -> "Offline preferences and data"
                    else -> "Your shared expenses"
                }
            ) {
                if (tab != 2) TextButton(onClick = { showCreate = true }) { Text("+ New") }
            }
        },
        bottomBar = {
            SplitBottomTabs(
                listOf("Groups" to "▦", "Archived" to "◷", "Settings" to "⚙"),
                tab
            ) {
                onTabChange(it)
                onFilterChange("")
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp)
        ) {
            if (tab == 2) {
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CompactPanel {
                        Text("Your data stays on your phone", style = MaterialTheme.typography.titleMedium)
                        Text("No sign-in, server or email permissions required.",
                            color = Muted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onExportBackup, Modifier.fillMaxWidth()) {
                            Text("Export full backup")
                        }
                        OutlinedButton(onClick = onImportBackup, Modifier.fillMaxWidth()) {
                            Text("Restore from backup")
                        }
                    }
                    CompactPanel {
                        Text("Appearance", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("Comfortable in daylight and at night", color = Muted,
                            style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(7.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            AppearanceMode.entries.forEach { choice ->
                                FilterChip(
                                    selected = appearance == choice,
                                    onClick = { onAppearanceChange(choice) },
                                    label = { Text(choice.name.lowercase().replaceFirstChar { it.uppercase() }) }
                                )
                            }
                        }
                    }
                    CompactPanel {
                        Text("About Split", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(5.dp))
                        Text("Version ${packageInfo.versionName ?: "Unknown"}")
                        Text("Build ${packageInfo.longVersionCode} · ${context.packageName}",
                            color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (archived) "ARCHIVED GROUPS" else "YOUR GROUPS",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall)
                        Text("${visibleGroups.size} shown · ${groups.size} total",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                    }
                    if (!archived) {
                        FilledTonalButton(onClick = { showCreate = true }) { Text("+ Create") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = filter, onValueChange = onFilterChange,
                    placeholder = { Text("Search by group or currency") },
                    singleLine = true, leadingIcon = { Text("⌕") },
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium
                )
                Spacer(Modifier.height(9.dp))
                if (visibleGroups.isEmpty()) {
                    EmptyState(
                        when {
                            filter.isNotBlank() -> "No matching groups"
                            archived -> "Nothing archived yet"
                            else -> "No groups yet"
                        },
                        when {
                            filter.isNotBlank() -> "Try a different search."
                            archived -> "Finished groups can be archived without losing their history."
                            else -> "Create your first group to start sharing expenses."
                        }
                    )
                } else {
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        items(visibleGroups, key = { it.id }) { group ->
                            Surface(
                                onClick = { onOpen(group.id) },
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp, MaterialTheme.colorScheme.outlineVariant
                                ),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(group.name, fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "${group.currency} · " +
                                                DateFormat.getDateInstance(DateFormat.MEDIUM)
                                                    .format(Date(group.createdAt)),
                                            color = Muted, style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    Text("›", style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showCreate) CreateGroupDialog(onDismiss = { showCreate = false }) {
        name, currency, firstMember ->
        vm.createGroup(name, currency, firstMember) { id ->
            showCreate = false
            onOpen(id)
        }
    }
}

@Composable
private fun CreateGroupDialog(onDismiss: () -> Unit, create: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("You") }
    var currency by remember { mutableStateOf("PKR") }
    val currencies = listOf("PKR", "USD", "EUR", "GBP", "AED", "INR")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create a group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Group name") }, placeholder = { Text("Weekend trip") }, singleLine = true)
                OutlinedTextField(first, { first = it }, label = { Text("Your name in this group") }, singleLine = true)
                Text("Group currency", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    currencies.take(3).forEach { item -> FilterChip(selected = item == currency, onClick = { currency = item }, label = { Text(item) }) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    currencies.drop(3).forEach { item -> FilterChip(selected = item == currency, onClick = { currency = item }, label = { Text(item) }) }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank() && first.isNotBlank(), onClick = { create(name, currency, first) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
