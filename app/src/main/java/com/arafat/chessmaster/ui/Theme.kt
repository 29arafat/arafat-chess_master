package com.arafat.chessmaster.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class Palette(
    val name: String,
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val primary: Color,
    val onPrimary: Color,
    val text: Color,
    val sub: Color,
    val gold: Color,
    val lightSq: Color,
    val darkSq: Color,
    val good: Color = Color(0xFF22C55E),
    val warn: Color = Color(0xFFF59E0B),
    val bad: Color = Color(0xFFEF4444),
    val info: Color = Color(0xFF38BDF8)
)

val PALETTES = listOf(
    Palette(
        "🌌 নিয়ন ব্লু", true,
        bg = Color(0xFF0A1020), surface = Color(0xFF121B33), surface2 = Color(0xFF1B2748),
        primary = Color(0xFF3DA9FC), onPrimary = Color(0xFF001428),
        text = Color(0xFFE8F0FF), sub = Color(0xFF93A4C8), gold = Color(0xFFF5C542),
        lightSq = Color(0xFFCBD9F2), darkSq = Color(0xFF4170B8)
    ),
    Palette(
        "👑 রয়্যাল গোল্ড", true,
        bg = Color(0xFF0F1218), surface = Color(0xFF181C26), surface2 = Color(0xFF232937),
        primary = Color(0xFFF2C14E), onPrimary = Color(0xFF1A1400),
        text = Color(0xFFF1F1EC), sub = Color(0xFFA3A8B8), gold = Color(0xFFF2C14E),
        lightSq = Color(0xFFF0D9B5), darkSq = Color(0xFFB58863)
    ),
    Palette(
        "☀ ক্লিন লাইট", false,
        bg = Color(0xFFF2F5FB), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFE6ECF7),
        primary = Color(0xFF1F5FD1), onPrimary = Color(0xFFFFFFFF),
        text = Color(0xFF0F172A), sub = Color(0xFF5B6B8C), gold = Color(0xFFB7791F),
        lightSq = Color(0xFFEEEED2), darkSq = Color(0xFF769656)
    )
)

val LocalPalette = staticCompositionLocalOf { PALETTES[0] }

@Composable
fun ChessTheme(index: Int, content: @Composable () -> Unit) {
    val p = PALETTES[index.coerceIn(0, PALETTES.lastIndex)]
    val scheme = if (p.dark) {
        darkColorScheme(
            primary = p.primary, onPrimary = p.onPrimary, secondary = p.gold,
            background = p.bg, surface = p.surface, surfaceVariant = p.surface2,
            onBackground = p.text, onSurface = p.text
        )
    } else {
        lightColorScheme(
            primary = p.primary, onPrimary = p.onPrimary, secondary = p.gold,
            background = p.bg, surface = p.surface, surfaceVariant = p.surface2,
            onBackground = p.text, onSurface = p.text
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
