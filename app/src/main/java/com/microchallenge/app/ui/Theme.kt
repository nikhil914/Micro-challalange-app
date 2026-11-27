package com.microchallenge.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.microchallenge.app.ui.design.AppFonts
import com.microchallenge.app.ui.design.LocalReduceMotion
import com.microchallenge.app.ui.design.Palette

/**
 * Arcade look for a teen/adult audience: always-dark deep indigo with skill
 * gradients and warm reward colors. No dynamic color, so the brand stays consistent.
 * Tokens live in [com.microchallenge.app.ui.design].
 */
private val Arcade = darkColorScheme(
    primary = Palette.Primary,
    onPrimary = Color.White,
    primaryContainer = Palette.Surface3,
    onPrimaryContainer = Palette.Text,
    secondary = Palette.Accent,
    onSecondary = Color(0xFF00262D),
    tertiary = Palette.Xp,
    onTertiary = Color(0xFF2A1C00),
    background = Palette.Night,
    onBackground = Palette.Text,
    surface = Palette.Surface1,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Surface2,
    onSurfaceVariant = Palette.TextDim,
    surfaceContainer = Palette.Surface1,
    surfaceContainerHigh = Palette.Surface2,
    surfaceContainerHighest = Palette.Surface3,
    outline = Palette.Outline,
    outlineVariant = Palette.Outline,
    error = Palette.Wrong,
    onError = Color(0xFF2D0000),
)

private fun display(size: Int, weight: FontWeight, line: Int = (size * 1.15f).toInt(), spacing: Float = 0f) =
    TextStyle(fontFamily = AppFonts.display, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
        letterSpacing = spacing.sp)

private fun body(size: Int, weight: FontWeight, line: Int = (size * 1.45f).toInt(), spacing: Float = 0f) =
    TextStyle(fontFamily = AppFonts.body, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
        letterSpacing = spacing.sp)

private val ArcadeType = Typography(
    displayLarge = display(64, FontWeight.Black),
    displayMedium = display(48, FontWeight.Black),
    displaySmall = display(36, FontWeight.ExtraBold),
    headlineLarge = display(32, FontWeight.ExtraBold),
    headlineMedium = display(28, FontWeight.ExtraBold),
    headlineSmall = display(24, FontWeight.Bold),
    titleLarge = display(22, FontWeight.Bold),
    titleMedium = display(18, FontWeight.SemiBold),
    titleSmall = display(15, FontWeight.SemiBold),
    bodyLarge = body(16, FontWeight.Normal),
    bodyMedium = body(14, FontWeight.Normal),
    bodySmall = body(12, FontWeight.Normal),
    labelLarge = body(14, FontWeight.SemiBold, spacing = 0.2f),
    labelMedium = body(12, FontWeight.SemiBold, spacing = 0.4f),
    labelSmall = body(11, FontWeight.Medium, spacing = 0.6f),
)

@Composable
fun MicroMindTheme(reduceMotion: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Arcade, typography = ArcadeType) {
        CompositionLocalProvider(LocalReduceMotion provides reduceMotion, content = content)
    }
}
