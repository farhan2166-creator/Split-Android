package com.splitapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.splitapp.model.Member
import com.splitapp.model.Money

internal val DeepGreen = Color(0xFF00675B)
internal val AccentOrange = Color(0xFFD97706)
internal val Muted = Color(0xFF658078)
internal val Pale = Color(0xFFE4F3EE)

@Composable
fun AppHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).statusBarsPadding().padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Text("‹", fontSize = 36.sp, color = DeepGreen, modifier = Modifier.clickable { onBack() }.padding(end = 16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Muted)
        }
        action?.invoke()
    }
}

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) { Column(Modifier.padding(18.dp), content = content) }
}

@Composable
fun SectionTitle(title: String, right: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (right != null) Text(right, style = MaterialTheme.typography.labelMedium, color = Muted)
    }
}

@Composable
fun MoneyLabel(amount: Long, currency: String, color: Color = DeepGreen, large: Boolean = false) {
    Text("$currency ${Money.format(amount)}", color = color, fontWeight = FontWeight.Bold,
        style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium)
}

@Composable
fun PersonPicker(members: List<Member>, selected: String?, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        members.forEach { member ->
            FilterChip(
                selected = member.id == selected,
                onClick = { onSelect(member.id) },
                label = { Text(member.name) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Pale),
                border = BorderStroke(1.dp, if (member.id == selected) Color(0xFF08745D) else Color(0xFFDAE3DF))
            )
        }
    }
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 44.dp, horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("◌", fontSize = 42.sp, color = Muted)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(message, color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun LoadingPage(back: () -> Unit) {
    Column {
        AppHeader("Split", onBack = back)
        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

fun csvCell(value: String): String = "\"" + value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\""

@Composable
fun DashboardAction(title: String, subtitle: String, symbol: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick, modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(symbol, fontSize = 28.sp, color = AccentOrange)
            Spacer(Modifier.height(7.dp))
            Text(title, fontWeight = FontWeight.Bold, color = DeepGreen, textAlign = TextAlign.Center)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Muted, textAlign = TextAlign.Center)
        }
    }
}
