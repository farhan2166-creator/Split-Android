package com.splitapp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/** Quiet, high-contrast ocean/sage palette. All controls follow Android's system appearance. */
private val Light = lightColorScheme(
    primary = Color(0xFF146C63),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7EEE7),
    onPrimaryContainer = Color(0xFF103F38),
    secondary = Color(0xFF946037),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF8E8D5),
    onSecondaryContainer = Color(0xFF603B1E),
    tertiary = Color(0xFF595F92),
    onTertiary = Color.White,
    background = Color(0xFFF4F7F5),
    onBackground = Color(0xFF1B302D),
    surface = Color.White,
    onSurface = Color(0xFF1B302D),
    surfaceVariant = Color(0xFFE7EEEA),
    onSurfaceVariant = Color(0xFF526861),
    outline = Color(0xFFC4D4CD),
    outlineVariant = Color(0xFFDFE8E3),
    error = Color(0xFFB44340),
    onError = Color.White
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8BD9C8),
    onPrimary = Color(0xFF0A4038),
    primaryContainer = Color(0xFF21594F),
    onPrimaryContainer = Color(0xFFD7F7EB),
    secondary = Color(0xFFF2BC8B),
    onSecondary = Color(0xFF563518),
    secondaryContainer = Color(0xFF5F432D),
    onSecondaryContainer = Color(0xFFFFE9D4),
    tertiary = Color(0xFFC2C5FA),
    onTertiary = Color(0xFF353A6B),
    background = Color(0xFF111D20),
    onBackground = Color(0xFFE2ECE8),
    surface = Color(0xFF1B2A2D),
    onSurface = Color(0xFFE2ECE8),
    surfaceVariant = Color(0xFF26393A),
    onSurfaceVariant = Color(0xFFB5CCC4),
    outline = Color(0xFF536F68),
    outlineVariant = Color(0xFF344B46),
    error = Color(0xFFFFB4AD),
    onError = Color(0xFF680D0B)
)

private val Rounded = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val ReadableType = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontSize = 31.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp),
        headlineSmall = base.headlineSmall.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        titleMedium = base.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
        labelLarge = base.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontSize = 12.sp, lineHeight = 17.sp)
    )
}

@Composable
fun SplitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = ReadableType,
        shapes = Rounded,
        content = content
    )
}
