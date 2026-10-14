package com.splitapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.splitapp.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitapp.model.Member
import com.splitapp.model.Money

/** Colors derive from the system light/dark palette rather than hard-coded white cards. */
internal val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun AppHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Text("←", fontSize = 26.sp, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(6.dp))
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_split_foreground),
                    contentDescription = "Split app icon",
                    modifier = Modifier.size(43.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            action?.invoke()
        }
    }
}

/** Border-led surfaces avoid heavy drop shadows and keep dark mode readable. */
@Composable
fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
fun HeroPanel(
    kicker: String,
    title: String,
    supporting: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val gradient = Brush.linearGradient(
        listOf(Color(0xFF163F48), Color(0xFF1B645D), Color(0xFF327B6E))
    )
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(gradient).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(Color(0xFFD3DEA5), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(kicker.uppercase(), style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFCBEBE3), letterSpacing = 1.2.sp)
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = Color.White)
        if (supporting != null) {
            Text(supporting, color = Color(0xFFD9EBE7),
                style = MaterialTheme.typography.bodyMedium)
        }
        content()
    }
}

@Composable
fun SectionTitle(title: String, right: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        if (right != null) {
            Text(right, style = MaterialTheme.typography.labelMedium, color = Muted)
        }
    }
}

@Composable
fun MoneyLabel(amount: Long, currency: String, color: Color = Color.Unspecified, large: Boolean = false) {
    Text(
        "$currency ${Money.format(amount)}",
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.primary else color,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium
    )
}

@Composable
fun PersonPicker(members: List<Member>, selected: String?, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        members.forEach { member ->
            FilterChip(
                selected = member.id == selected,
                onClick = { onSelect(member.id) },
                label = { Text(member.name) },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                border = BorderStroke(1.dp,
                    if (member.id == selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}

@Composable
fun MetricTile(title: String, value: String, modifier: Modifier = Modifier) {
    SurfaceCard(modifier) {
        Text(title, color = Muted, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(5.dp))
        Text(value, style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    SurfaceCard(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(54.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text("◎", fontSize = 30.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(13.dp))
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(message, color = Muted, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium)
        }
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

fun csvCell(value: String): String =
    "\"" + value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\""

@Composable
fun DashboardAction(
    title: String,
    subtitle: String,
    symbol: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 120.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(symbol, style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(11.dp))
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, style = MaterialTheme.typography.labelSmall,
                color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The most important screen actions stay visible, without tall dashboard tiles. */
@Composable
fun CompactQuickAction(
    title: String,
    symbol: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 7.dp)
    ) {
        Text(symbol, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium)
    }
}

/** Shorter card padding for compact, one-screen forms and dashboard summaries. */
@Composable
fun CompactPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), content = content)
    }
}
