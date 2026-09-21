package com.wiremote.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WireMoteDarkColors = darkColorScheme(
    primary = Color(0xFF9CCBFF),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF174A73),
    onPrimaryContainer = Color(0xFFD1E5FF),
    secondary = Color(0xFFBBC7D7),
    onSecondary = Color(0xFF253140),
    secondaryContainer = Color(0xFF3B4858),
    onSecondaryContainer = Color(0xFFD7E3F5),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE1E2E6),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE1E2E6),
    surfaceVariant = Color(0xFF41474F),
    onSurfaceVariant = Color(0xFFC1C7D0),
    outline = Color(0xFF8B919A),
)

@Composable
fun WireMoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WireMoteDarkColors,
        content = content,
    )
}
