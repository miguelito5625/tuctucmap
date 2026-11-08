package com.example.tuctucmap.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TucTucDarkColorScheme = darkColorScheme(
    primary = TucTucYellow,
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFFD97706),
    onPrimaryContainer = Color.White,
    secondary = TucTucAmber,
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = SlateSurfaceVariant,
    onSecondaryContainer = TextPrimary,
    tertiary = TucTucSkyBlue,
    onTertiary = Color(0xFF0F172A),
    background = SlateBackground,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = SlateBorder,
    outlineVariant = SlateSurfaceVariant,
    error = TucTucRed,
    onError = Color.White
)

@Composable
fun TuctucMapTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TucTucDarkColorScheme,
        typography = Typography,
        content = content
    )
}
