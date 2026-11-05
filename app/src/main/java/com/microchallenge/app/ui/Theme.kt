package com.microchallenge.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * Arcade look for a teen/adult audience: always-dark, high-contrast neon
 * accents on near-black. No dynamic color, so the brand stays consistent.
 */
private val Arcade = darkColorScheme(
    primary = Color(0xFF00E5FF),        // neon cyan
    onPrimary = Color(0xFF001F24),
    secondary = Color(0xFFB388FF),      // electric violet
    onSecondary = Color(0xFF1A0040),
    tertiary = Color(0xFFFFD740),       // gold, for bests and rank-ups
    onTertiary = Color(0xFF261A00),
    background = Color(0xFF0B0D12),
    onBackground = Color(0xFFE8EAF0),
    surface = Color(0xFF14171F),
    onSurface = Color(0xFFE8EAF0),
    surfaceVariant = Color(0xFF1E2230),
    onSurfaceVariant = Color(0xFFB6BCCB),
    error = Color(0xFFFF5370),
)

private val base = Typography()
private val ArcadeType = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Black),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
)

@Composable
fun MicroMindTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Arcade, typography = ArcadeType, content = content)
}
