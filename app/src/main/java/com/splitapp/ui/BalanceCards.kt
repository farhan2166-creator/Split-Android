package com.splitapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.splitapp.model.Member
import com.splitapp.model.Money

/** Financial state colours are accompanied by text, not colour alone. */
@Composable
fun PersonalBalanceBanner(
    balanceMinor: Long?,
    currency: String,
    onChooseMe: () -> Unit,
    onViewSuggestions: () -> Unit
) {
    val positive = (balanceMinor ?: 0L) > 0L
    val negative = (balanceMinor ?: 0L) < 0L
    val tone = if (negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val title = when {
        balanceMinor == null -> "Which traveler is you?"
        positive -> "You are owed"
        negative -> "You need to pay"
        else -> "You're all settled"
    }
    val subtitle = when {
        balanceMinor == null -> "Select Me to view your personal balance."
        positive -> "Other travelers still owe you money."
        negative -> "Settle whenever the payment is made."
        else -> "You have no outstanding balance in this group."
    }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.60f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        Text(if (negative) "↗" else if (positive) "↙" else "✓",
                            style = MaterialTheme.typography.titleLarge, color = tone)
                    }
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = Muted, style = MaterialTheme.typography.labelSmall,
                        maxLines = 2)
                }
                if (balanceMinor != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("$currency ${Money.format(kotlin.math.abs(balanceMinor))}",
                            color = tone, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Text(if (positive) "TO COLLECT" else if (negative) "TO PAY" else "CLEAR",
                            color = tone, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = if (balanceMinor == null) onChooseMe else onViewSuggestions,
                modifier = Modifier.fillMaxWidth()) {
                Text(if (balanceMinor == null) "Choose my name" else "View suggested settlements  ›")
            }
        }
    }
}

@Composable
fun TravelerBalanceCard(member: Member, balanceMinor: Long, currency: String, isMe: Boolean) {
    val positive = balanceMinor > 0L
    val negative = balanceMinor < 0L
    val color = when {
        negative -> MaterialTheme.colorScheme.error
        positive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape) {
                Box(Modifier.size(41.dp), contentAlignment = Alignment.Center) {
                    Text(member.name.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(member.name, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (isMe) {
                        Spacer(Modifier.width(7.dp))
                        Surface(color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small) {
                            Text("Me", color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                        }
                    }
                }
                Text(when {
                    positive -> "To receive"
                    negative -> "To pay"
                    else -> "Settled"
                }, style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${if (negative) "−" else if (positive) "+" else ""}$currency ${Money.format(kotlin.math.abs(balanceMinor))}",
                    color = color, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1)
            }
        }
    }
}
