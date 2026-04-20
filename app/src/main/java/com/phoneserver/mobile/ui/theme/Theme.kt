package com.phoneserver.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
        primary = Color(0xFF1A7F5A),
        secondary = Color(0xFFB86A2F),
        tertiary = Color(0xFF3F6B9A),
        background = Color(0xFFF4F0E8),
        surface = Color(0xFFFFFBF4),
        surfaceContainer = Color(0xFFF1EAE0),
        surfaceContainerHigh = Color(0xFFE8DED1),
        surfaceContainerLow = Color(0xFFFAF5EE),
        onPrimary = Color(0xFFFFFFFF),
        onSecondary = Color(0xFFFFFFFF),
        onTertiary = Color(0xFFFFFFFF),
        onBackground = Color(0xFF182127),
        onSurface = Color(0xFF182127),
        onSurfaceVariant = Color(0xFF56616C)
)

private val DarkColors = darkColorScheme(
        primary = Color(0xFF8ED1AF),
        secondary = Color(0xFFF0B17A),
        tertiary = Color(0xFF9ABBE7),
        background = Color(0xFF0E151C),
        surface = Color(0xFF121D26),
        surfaceContainer = Color(0xFF15212B),
        surfaceContainerHigh = Color(0xFF1A2935),
        surfaceContainerLow = Color(0xFF101922),
        onPrimary = Color(0xFF0C2B1E),
        onSecondary = Color(0xFF4D2503),
        onTertiary = Color(0xFF0E2742),
        onBackground = Color(0xFFE8EEF4),
        onSurface = Color(0xFFE8EEF4),
        onSurfaceVariant = Color(0xFFAFBCC8)
)

@Composable
fun PhoneServerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
            colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
            content = content
    )
}
