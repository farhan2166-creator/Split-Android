package com.splitapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.splitapp.data.SplitViewModel
import com.splitapp.model.*
import java.util.UUID

@Composable
fun PaymentEditor(
    detail: GroupDetail,
    initialFrom: String?,
    initialTo: String?,
    suggestedMinor: Long?,
    vm: SplitViewModel,
    onBack: () -> Unit
) {
    var from by remember { mutableStateOf(initialFrom ?: detail.members.first().id) }
    var to by remember { mutableStateOf(initialTo ?: detail.members.firstOrNull { it.id != from }?.id ?: from) }
    var amountText by remember { mutableStateOf(suggestedMinor?.let { Money.input(it) }.orEmpty()) }
    var note by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(TransferKind.REPAYMENT) }
    var error by remember { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = { AppHeader(if (kind == TransferKind.ADVANCE) "Cash advance" else "Record repayment", detail.group.name, onBack) },
        bottomBar = {
            Surface(modifier = Modifier.navigationBarsPadding(), shadowElevation = 5.dp) {
                Button(onClick = {
                    val amount = Money.parse(amountText)
                    when {
                        from == to -> error = "Choose two different participants"
                        amount == null || amount <= 0 -> error = "Enter a positive amount"
                        else -> {
                            error = null
                            vm.addTransfer(Transfer(UUID.randomUUID().toString(), detail.group.id, from, to, amount, note, System.currentTimeMillis(), kind), onBack)
                        }
                    }
                }, modifier = Modifier.fillMaxWidth().padding(18.dp)) { Text(if (kind == TransferKind.ADVANCE) "Record cash advance" else "Record repayment") }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            CompactPanel {
                Text("CASH MOVEMENT", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == TransferKind.ADVANCE,
                        onClick = { kind = TransferKind.ADVANCE }, label = { Text("Advance") })
                    FilterChip(selected = kind == TransferKind.REPAYMENT,
                        onClick = { kind = TransferKind.REPAYMENT }, label = { Text("Repayment") })
                }
                Text(if (kind == TransferKind.ADVANCE)
                    "Advance: cash given before or during the trip to another traveler."
                    else "Repayment: a partial or final settlement between travelers.",
                    style = MaterialTheme.typography.bodySmall, color = Muted)
                Spacer(Modifier.height(8.dp))
                Text("01  ·  WHO PAID?", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(7.dp))
                PersonPicker(detail.members, from) { from = it }
                Spacer(Modifier.height(8.dp))
                Text("02  ·  WHO RECEIVED?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(7.dp))
                PersonPicker(detail.members, to) { to = it }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(amountText, { amountText = it }, label = { Text("Amount · ${detail.group.currency}") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(note, { note = it }, label = { Text("Reference or note (optional)") }, modifier = Modifier.fillMaxWidth())
            }
            Text("Record only money actually handed over. Advances and repayments adjust balances but do not count as tour expenses.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
