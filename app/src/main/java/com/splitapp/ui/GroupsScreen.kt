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
        topBar = { AppHeader("Split", "Fair expenses, easy settlements") { TextButton(onClick = { showAbout = true }) { Text("About") } } },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { showCreate = true }) { Text("+ New group") } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp), contentPadding = PaddingValues(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Spacer(Modifier.height(10.dp))
                HeroPanel(
                    kicker = "YOUR SHARED SPACE",
                    title = "Share moments. Not money worries.",
                    supporting = "Organise everyday expenses and settle with confidence."
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        Column {
                            Text("${groups.count { !it.archived }}", color = androidx.compose.ui.graphics.Color.White,
                                style = MaterialTheme.typography.headlineSmall)
                            Text("Active groups", color = androidx.compose.ui.graphics.Color(0xFFCFEBE3),
                                style = MaterialTheme.typography.labelMedium)
                        }
                        Column {
                            Text("${groups.count { it.archived }}", color = androidx.compose.ui.graphics.Color.White,
                                style = MaterialTheme.typography.headlineSmall)
                            Text("Archived", color = androidx.compose.ui.graphics.Color(0xFFCFEBE3),
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SectionTitle("Quick actions")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DashboardAction("New group", "Start splitting", "＋", { showCreate = true }, Modifier.weight(1f))
                    DashboardAction("Back up", "Keep data safe", "↥", onExportBackup, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                SectionTitle("Your groups")
                OutlinedTextField(
                    value = filter, onValueChange = { filter = it },
                    label = { Text("Search your groups") }, singleLine = true,
                    leadingIcon = { Text("⌕") },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    FilterChip(selected = !showArchived, onClick = { showArchived = false }, label = { Text("Active") })
                    FilterChip(selected = showArchived, onClick = { showArchived = true }, label = { Text("Archived") })
                }
                SectionTitle(if (showArchived) "Archived groups" else "Active groups", "${visibleGroups.size} shown")
            }
            if (visibleGroups.isEmpty()) item { EmptyState(if (groups.isEmpty()) "No groups yet" else if (showArchived) "No archived groups" else "No matching groups", if (groups.isEmpty()) "Create a group to start sharing expenses." else if (showArchived) "Archived groups appear here." else "Try a different search.") }
            items(visibleGroups, key = { it.id }) { group ->
                SurfaceCard(Modifier.clickable { onOpen(group.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(group.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(5.dp))
                            Text("${if (group.archived) "Archived · " else ""}${group.currency}  ·  ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(group.createdAt))}", color = Muted)
                        }
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                            Text("↗", modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            item {
                SectionTitle("Data & privacy")
                SurfaceCard {
                    Text("Your records stay on your device.", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("Save a full backup before changing phones or reinstalling.",
                        color = Muted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onExportBackup, modifier = Modifier.fillMaxWidth()) { Text("Export backup") }
                    OutlinedButton(onClick = onImportBackup, modifier = Modifier.fillMaxWidth()) { Text("Restore from backup") }
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
