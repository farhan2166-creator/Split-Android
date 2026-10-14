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
    var error by remember { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = { AppHeader("Record a repayment", detail.group.name, onBack) },
        bottomBar = {
            Surface(modifier = Modifier.navigationBarsPadding(), shadowElevation = 5.dp) {
                Button(onClick = {
                    val amount = Money.parse(amountText)
                    when {
                        from == to -> error = "Choose two different participants"
                        amount == null || amount <= 0 -> error = "Enter a positive amount"
                        else -> {
                            error = null
                            vm.addTransfer(Transfer(UUID.randomUUID().toString(), detail.group.id, from, to, amount, note, System.currentTimeMillis()), onBack)
                        }
                    }
                }, modifier = Modifier.fillMaxWidth().padding(18.dp)) { Text("Record repayment") }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            SurfaceCard {
                Text("WHO PAID?", style = MaterialTheme.typography.labelMedium, color = Muted)
                Spacer(Modifier.height(7.dp))
                PersonPicker(detail.members, from) { from = it }
                Spacer(Modifier.height(18.dp))
                Text("WHO RECEIVED?", style = MaterialTheme.typography.labelMedium, color = Muted)
                Spacer(Modifier.height(7.dp))
                PersonPicker(detail.members, to) { to = it }
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(amountText, { amountText = it }, label = { Text("Amount · ${detail.group.currency}") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(note, { note = it }, label = { Text("Reference or note (optional)") }, modifier = Modifier.fillMaxWidth())
            }
            Text("Only record a repayment when the money has actually changed hands. This reduces the payer's outstanding debt; it does not add to the group's spending total.", style = MaterialTheme.typography.bodyMedium, color = Muted)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
