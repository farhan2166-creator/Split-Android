package com.splitapp.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.splitapp.data.SplitViewModel
import com.splitapp.model.*
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.UUID

/** Create and correct actual money movements without changing what counts as an expense. */
@Composable
fun PaymentEditor(
    detail: GroupDetail,
    initialFrom: String?,
    initialTo: String?,
    suggestedMinor: Long?,
    vm: SplitViewModel,
    onBack: () -> Unit,
    transferId: String? = null
) {
    val existing = detail.transfers.firstOrNull { it.id == transferId }
    val context = LocalContext.current
    val localMe = remember(context, detail.group.id) {
        context.getSharedPreferences(
            "split_group_identity", android.content.Context.MODE_PRIVATE
        ).getString("member_${detail.group.id}", null)
    }
    val first = detail.members.firstOrNull { it.id == localMe }?.id
        ?: detail.members.firstOrNull()?.id.orEmpty()
    var from by remember(transferId) {
        mutableStateOf(existing?.fromMember ?: initialFrom ?: first)
    }
    var to by remember(transferId) {
        mutableStateOf(existing?.toMember ?: initialTo
            ?: detail.members.firstOrNull { it.id != from }?.id.orEmpty())
    }
    var amountText by remember(transferId) {
        mutableStateOf(existing?.let { Money.input(it.amountMinor) }
            ?: suggestedMinor?.let { Money.input(it) }.orEmpty())
    }
    var note by remember(transferId) { mutableStateOf(existing?.note.orEmpty()) }
    var kind by remember(transferId) { mutableStateOf(existing?.kind ?: TransferKind.REPAYMENT) }
    var transferDate by remember(transferId) {
        mutableLongStateOf(existing?.timestamp ?: System.currentTimeMillis())
    }
    var error by remember(transferId) { mutableStateOf<String?>(null) }
    var saving by remember(transferId) { mutableStateOf(false) }
    val draftId = remember(transferId) { existing?.id ?: UUID.randomUUID().toString() }

    Scaffold(
        topBar = {
            AppHeader(
                if (existing != null) "Edit cash movement"
                else if (kind == TransferKind.ADVANCE) "Cash advance" else "Record repayment",
                detail.group.name, onBack
            )
        },
        bottomBar = {
            Surface(modifier = Modifier.navigationBarsPadding(), shadowElevation = 5.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(5.dp))
                    }
                    Button(
                        enabled = !saving && detail.members.size > 1 &&
                            (transferId == null || existing != null),
                        onClick = {
                            val amount = Money.parse(amountText)
                            when {
                                from !in detail.members.map { it.id } ||
                                    to !in detail.members.map { it.id } ->
                                    error = "Choose travelers in this group"
                                from == to -> error = "Choose two different travelers"
                                amount == null || amount <= 0L -> error = "Enter a positive amount"
                                else -> {
                                    saving = true
                                    error = null
                                    vm.saveTransfer(
                                        Transfer(draftId, detail.group.id, from, to, amount,
                                            note.trim(), transferDate, kind),
                                        onBack,
                                        onError = { saving = false }
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        if (saving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (existing != null) "Save changes"
                            else if (kind == TransferKind.ADVANCE) "Record cash advance"
                            else "Record repayment")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            CompactPanel {
                Text("CASH MOVEMENT", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    FilterChip(
                        selected = kind == TransferKind.ADVANCE,
                        onClick = { kind = TransferKind.ADVANCE; error = null },
                        label = { Text("Cash advance") }
                    )
                    FilterChip(
                        selected = kind == TransferKind.REPAYMENT,
                        onClick = { kind = TransferKind.REPAYMENT; error = null },
                        label = { Text("Repayment") }
                    )
                }
                Text(
                    if (kind == TransferKind.ADVANCE)
                        "Money given to another traveler to spend during the trip."
                    else "Money already handed over to settle part or all of a balance.",
                    style = MaterialTheme.typography.bodySmall, color = Muted
                )
            }
            CompactPanel {
                Text("Paid by", color = Muted, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(5.dp))
                PersonPicker(detail.members, from) {
                    from = it
                    if (to == from) to = detail.members.firstOrNull { person ->
                        person.id != from
                    }?.id.orEmpty()
                    error = null
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    OutlinedButton(onClick = {
                        val previous = from
                        from = to
                        to = previous
                        error = null
                    }, enabled = detail.members.size > 1 && to.isNotEmpty()) {
                        Text("⇅  Swap direction")
                    }
                }
                Text("Received by", color = Muted, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(5.dp))
                PersonPicker(detail.members, to) {
                    to = it
                    error = null
                }
                if (from == to) {
                    Text("The sender and recipient must be different people.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it; error = null },
                    label = { Text("Amount · ${detail.group.currency}") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    prefix = { Text("${detail.group.currency} ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(Modifier.height(7.dp))
                OutlinedButton(
                    onClick = {
                        val calendar = Calendar.getInstance().apply { timeInMillis = transferDate }
                        DatePickerDialog(context, { _, year, month, day ->
                            transferDate = Calendar.getInstance().apply {
                                timeInMillis = transferDate
                                set(Calendar.YEAR, year)
                                set(Calendar.MONTH, month)
                                set(Calendar.DAY_OF_MONTH, day)
                            }.timeInMillis
                        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Date: " + DateFormat.getDateInstance(DateFormat.MEDIUM)
                        .format(Date(transferDate)))
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Reference or note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1, maxLines = 3
                )
            }
            Text(
                if (existing != null)
                    "Correcting this record updates balances without creating another transaction."
                else "Only record money that has actually changed hands. Advances and repayments " +
                    "affect balances but do not count as tour expenses.",
                style = MaterialTheme.typography.bodySmall, color = Muted
            )
        }
    }
}
