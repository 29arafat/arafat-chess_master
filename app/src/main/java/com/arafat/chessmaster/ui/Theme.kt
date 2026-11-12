package com.arafat.chessmaster.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun ChessTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFFF2C14E),
            onPrimary = Color(0xFF1A1400),
            secondary = Color(0xFF6FA8DC),
            background = Color(0xFF0E1420),
            surface = Color(0xFF172033),
            surfaceVariant = Color(0xFF1F2A40),
            onBackground = Color(0xFFE8ECF4),
            onSurface = Color(0xFFE8ECF4)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF1F3A68),
            onPrimary = Color.White,
            secondary = Color(0xFFB7791F),
            background = Color(0xFFF3F5FA),
            surface = Color.White,
            surfaceVariant = Color(0xFFE6EAF3),
            onBackground = Color(0xFF111827),
            onSurface = Color(0xFF111827)
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
