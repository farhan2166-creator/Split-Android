package com.splitapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.splitapp.data.SplitViewModel
import com.splitapp.model.SplitGroup
import java.text.DateFormat
import java.util.Date

@Composable
fun GroupsScreen(
    groups: List<SplitGroup>, vm: SplitViewModel, onOpen: (String) -> Unit,
    appearance: AppearanceMode, onAppearanceChange: (AppearanceMode) -> Unit,
    onExportBackup: () -> Unit, onImportBackup: () -> Unit
) {
    val context = LocalContext.current
    val packageInfo = remember(context) {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    var showAbout by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }
    val visibleGroups = remember(groups, filter, showArchived) { groups.filter { it.archived == showArchived && (it.name.contains(filter.trim(), ignoreCase = true) || it.currency.contains(filter.trim(), ignoreCase = true)) } }
    Scaffold(
        topBar = { AppHeader("Split", "Fair expenses, easy settlements") {
            TextButton(onClick = { showAbout = true }) { Text("About") }
        } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp)) {
            CompactPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("YOUR SHARED SPACE", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                        Text("${groups.count { !it.archived }} active · ${groups.count { it.archived }} archived",
                            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Text("◎", style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                CompactQuickAction("New group", "+", { showCreate = true }, Modifier.weight(1f))
                CompactQuickAction("Backup", "↥", onExportBackup, Modifier.weight(1f))
                CompactQuickAction("Restore", "↧", onImportBackup, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = filter, onValueChange = { filter = it },
                placeholder = { Text("Search groups") }, singleLine = true,
                leadingIcon = { Text("⌕") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showArchived, onClick = { showArchived = false }, label = { Text("Active") })
                FilterChip(selected = showArchived, onClick = { showArchived = true }, label = { Text("Archived") })
                Spacer(Modifier.weight(1f))
                Text("${visibleGroups.size} groups", style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            if (visibleGroups.isEmpty()) {
                EmptyState(
                    if (groups.isEmpty()) "No groups yet" else "No matching groups",
                    if (groups.isEmpty()) "Create a group to start splitting expenses." else "Change the filter or search."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(visibleGroups, key = { it.id }) { group ->
                        Surface(
                            onClick = { onOpen(group.id) },
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(group.name, fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                    Text("${group.currency} · ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(group.createdAt))}",
                                        color = Muted, style = MaterialTheme.typography.labelSmall)
                                }
                                Text("›", color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }
        }
    }
    if (showAbout) AlertDialog(
        onDismissRequest = { showAbout = false },
        title = { Text("About Split") },
        text = {
            Column {
                Text("Version: ${packageInfo.versionName ?: "Unknown"}")
                Text("Build code: ${packageInfo.longVersionCode}")
                Text("Package: ${context.packageName}")
                Text("Offline expense sharing", color = Muted)
                Spacer(Modifier.height(16.dp))
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                Text("Choose the look that feels comfortable to you.", color = Muted,
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    AppearanceMode.entries.forEach { option ->
                        FilterChip(
                            selected = appearance == option,
                            onClick = { onAppearanceChange(option) },
                            label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Close") } }
    )
    if (showCreate) CreateGroupDialog(onDismiss = { showCreate = false }) { name, currency, firstMember ->
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
