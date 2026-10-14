package com.splitapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.splitapp.data.BackupSnapshot
import com.splitapp.data.RestoreMode

/**
 * Read-only preview before importing any data.
 * Replacement requires explicit checkbox approval; cancel leaves SQLite untouched.
 */
@Composable
fun BackupRestoreDialog(
    backup: BackupSnapshot,
    onDismiss: () -> Unit,
    onRestore: (RestoreMode) -> Unit
) {
    var mode by remember { mutableStateOf(RestoreMode.MERGE_MISSING) }
    var acknowledge by remember { mutableStateOf(false) }
    val groupCount = backup.groups.size
    val memberCount = backup.groups.sumOf { it.members.size }
    val expenseCount = backup.groups.sumOf { it.expenses.size }
    val paymentCount = backup.groups.sumOf { it.transfers.size }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore Split backup") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Backup verified: checksum, participant references, splits and balances.")
                Text("$groupCount groups • $memberCount participants")
                Text("$expenseCount expenses • $paymentCount repayments")
                HorizontalDivider()
                Row {
                    RadioButton(
                        selected = mode == RestoreMode.MERGE_MISSING,
                        onClick = { mode = RestoreMode.MERGE_MISSING }
                    )
                    Column {
                        Text("Import missing groups", style = MaterialTheme.typography.titleSmall)
                        Text("Keeps all current data. Existing group IDs are skipped; no existing records are changed.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row {
                    RadioButton(
                        selected = mode == RestoreMode.REPLACE_ALL,
                        onClick = { mode = RestoreMode.REPLACE_ALL }
                    )
                    Column {
                        Text("Replace all local groups", style = MaterialTheme.typography.titleSmall)
                        Text("Deletes all currently saved groups and replaces them with this backup.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
                if (mode == RestoreMode.REPLACE_ALL) {
                    Row {
                        Checkbox(checked = acknowledge, onCheckedChange = { acknowledge = it })
                        Text("I understand that ALL current groups and their expenses will be replaced.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("Backups are not encrypted. Keep the backup file private.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                enabled = mode == RestoreMode.MERGE_MISSING || acknowledge,
                onClick = { onRestore(mode) }
            ) { Text(if (mode == RestoreMode.REPLACE_ALL) "Replace all data" else "Import groups") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
