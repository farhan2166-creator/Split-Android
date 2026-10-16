package com.splitapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitapp.model.Money
import com.splitapp.model.TourSettlement
import com.splitapp.model.TravelerSettlement
import com.splitapp.model.figuresFor

/**
 * Four genuinely different tour figures:
 * My Expenses = my allocated shares, not bills I happened to pay.
 * Cash Received = cash advances + repayments actually received.
 * Outstanding = signed net balance, not the original expense amount.
 */
@Composable
fun TourOverview(
    statement: TourSettlement,
    me: TravelerSettlement?,
    onChooseMe: () -> Unit
) {
    val currency = statement.group.currency
    val figures = statement.figuresFor(me?.member?.id)
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TOUR AT A GLANCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
                Text(
                    if (me == null) "Choose your participant to see personal figures"
                    else "Viewing my figures · ${me.member.name}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Muted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            TextButton(onClick = onChooseMe) {
                Text(if (me == null) "Set Me" else "Change Me")
            }
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TourMetric("Total Expenses",
                "$currency ${Money.format(figures.totalExpensesMinor)}",
                "All travelers' bills", Modifier.weight(1f))
            TourMetric("My Expenses",
                figures.myExpensesMinor?.let { "$currency ${Money.format(it)}" } ?: "—",
                "My share of bills", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TourMetric("Cash Received",
                figures.cashReceivedMinor?.let { "$currency ${Money.format(it)}" } ?: "—",
                "Advances + repayments", Modifier.weight(1f))
            TourMetric("Outstanding",
                figures.outstandingMinor?.let { "$currency ${Money.format(kotlin.math.abs(it))}" } ?: "—",
                when {
                    me == null -> "Select Me first"
                    me.balanceMinor > 0L -> "To collect"
                    me.balanceMinor < 0L -> "To pay"
                    else -> "Fully settled"
                },
                Modifier.weight(1f),
                alert = me != null && me.balanceMinor < 0L)
        }
        if (me != null) {
            Spacer(Modifier.height(9.dp))
            Text(
                "Bills paid by me: $currency ${Money.format(me.paidForExpensesMinor)}  ·  Cash sent: $currency ${Money.format(Math.addExact(me.cashAdvancedMinor, me.repaymentsSentMinor))}",
                style = MaterialTheme.typography.labelSmall,
                color = Muted
            )
        }
    }
}

@Composable
private fun TourMetric(
    label: String,
    value: String,
    note: String,
    modifier: Modifier = Modifier,
    alert: Boolean = false
) {
    Surface(
        modifier = modifier.heightIn(min = 80.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}
