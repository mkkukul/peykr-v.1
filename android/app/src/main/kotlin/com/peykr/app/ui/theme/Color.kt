package com.peykr.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Design system color tokens for Dark theme
val DarkBackground = Color(0xFF070B14)
val DarkSurface = Color(0xFF111C2D)
val DarkSurfaceElevated = Color(0xFF172438)
val DarkTextPrimary = Color(0xFFF1F5F9)
val DarkTextSecondary = Color(0xFFAAB6C8)
val DarkTextMuted = Color(0xFF77849B)
val DarkBorder = Color(0xFF263449)
val DarkPrimaryAccent = Color(0xFF00D5D0)
val DarkOrangeAccent = Color(0xFFFF8A19)
val DarkBrandNavy = Color(0xFF0A1730)
val DarkOnPrimary = Color(0xFF070B14)

// Design system color tokens for Light theme
val LightBackground = Color(0xFFF5F7FB)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceElevated = Color(0xFFFFFFFF)
val LightTextPrimary = Color(0xFF142238)
val LightTextSecondary = Color(0xFF64748B)
val LightTextMuted = Color(0xFF94A3B8)
val LightBorder = Color(0xFFDCE4EE)
val LightPrimaryAccent = Color(0xFF007F83)
val LightOrangeAccent = Color(0xFFE87516)
val LightBrandNavy = Color(0xFF073B78)
val LightOnPrimary = Color(0xFFFFFFFF)

// Semantic colors
val SuccessGreen = Color(0xFF43E6B1)
val ErrorRed = Color(0xFFFF5252)
val WarningOrange = Color(0xFFFFB74D)

data class PeykrColors(
  val background: Color,
  val surface: Color,
  val surfaceElevated: Color,
  val border: Color,
  val textPrimary: Color,
  val textSecondary: Color,
  val textMuted: Color,
  val primaryAccent: Color,
  val orangeAccent: Color,
  val brandNavy: Color,
  val onPrimary: Color,
  val isDark: Boolean,
)

val DarkPeykrColors = PeykrColors(
  background = DarkBackground,
  surface = DarkSurface,
  surfaceElevated = DarkSurfaceElevated,
  border = DarkBorder,
  textPrimary = DarkTextPrimary,
  textSecondary = DarkTextSecondary,
  textMuted = DarkTextMuted,
  primaryAccent = DarkPrimaryAccent,
  orangeAccent = DarkOrangeAccent,
  brandNavy = DarkBrandNavy,
  onPrimary = DarkOnPrimary,
  isDark = true,
)

val LightPeykrColors = PeykrColors(
  background = LightBackground,
  surface = LightSurface,
  surfaceElevated = LightSurfaceElevated,
  border = LightBorder,
  textPrimary = LightTextPrimary,
  textSecondary = LightTextSecondary,
  textMuted = LightTextMuted,
  primaryAccent = LightPrimaryAccent,
  orangeAccent = LightOrangeAccent,
  brandNavy = LightBrandNavy,
  onPrimary = LightOnPrimary,
  isDark = false,
)

val LocalPeykrColors = staticCompositionLocalOf { DarkPeykrColors }

object PeykrTheme {
  val colors: PeykrColors
    @Composable
    @ReadOnlyComposable
    get() = LocalPeykrColors.current
}

// Dynamic properties resolving to active theme tokens
val Navy950: Color
  @Composable
  @ReadOnlyComposable
  get() = PeykrTheme.colors.background

val Navy900: Color
  @Composable
  @ReadOnlyComposable
  get() = PeykrTheme.colors.surface

val DarkPanel: Color
  @Composable
  @ReadOnlyComposable
  get() = PeykrTheme.colors.surface

val DividerDark: Color
  @Composable
  @ReadOnlyComposable
  get() = PeykrTheme.colors.border

val TextPrimary: Color
  @Composable
  @ReadOnlyComposable
  get() = PeykrTheme.colors.textPrimary

val TextSecondary: Color
  @Composable
  @ReadOnlyComposable
  get() = PeykrTheme.colors.textSecondary

val TextMuted: Color
  @Composable
  @ReadOnlyComposable
  get() = PeykrTheme.colors.textMuted

val PrimaryTurquoise: Color
  @Composable
  @ReadOnlyComposable
  get() = PeykrTheme.colors.primaryAccent

val Cyan: Color
  @Composable
  @ReadOnlyComposable
  get() = PeykrTheme.colors.primaryAccent
