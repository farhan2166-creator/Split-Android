package com.splitapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.splitapp.data.SplitViewModel
import com.splitapp.model.SplitGroup
import java.text.DateFormat
import java.util.Date

@Composable
fun GroupsScreen(groups: List<SplitGroup>, vm: SplitViewModel, onOpen: (String) -> Unit) {
    var showCreate by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    val visibleGroups = remember(groups, filter) { groups.filter { it.name.contains(filter.trim(), ignoreCase = true) || it.currency.contains(filter.trim(), ignoreCase = true) } }
    Scaffold(
        topBar = { AppHeader("Split", "Fair expenses, easy settlements") },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { showCreate = true }, text = { Text("+ New group") }) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp), contentPadding = PaddingValues(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Spacer(Modifier.height(10.dp))
                SurfaceCard {
                    Text("Share costs, not confusion", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Organize a trip, home, dinner or event. Record what everyone pays, then settle the difference.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                }
                Spacer(Modifier.height(12.dp))
                SectionTitle("Quick start")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DashboardAction("New group", "Start splitting", "＋", { showCreate = true }, Modifier.weight(1f))
                    DashboardAction("Your groups", "${groups.size} saved", "▦", {}, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = filter, onValueChange = { filter = it },
                    label = { Text("Find a group") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                SectionTitle("Your groups", "${visibleGroups.size} shown")
            }
            if (visibleGroups.isEmpty()) item { EmptyState(if (groups.isEmpty()) "No groups yet" else "No matching groups", if (groups.isEmpty()) "Create a group to start sharing expenses." else "Try a different search.") }
            items(visibleGroups, key = { it.id }) { group ->
                SurfaceCard(Modifier.clickable { onOpen(group.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(group.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(5.dp))
                            Text("${group.currency}  ·  ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(group.createdAt))}", color = Muted)
                        }
                        Text("→", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
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
