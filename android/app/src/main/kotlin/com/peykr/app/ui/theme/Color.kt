package com.peykr.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Design system color tokens for Dark theme
val DarkBackground = Color(0xFF0B1120)
val DarkSurface = Color(0xFF131D31)
val DarkSurfaceElevated = Color(0xFF1B273E)
val DarkTextPrimary = Color(0xFFF8FAFC)
val DarkTextSecondary = Color(0xFF94A3B8)
val DarkTextMuted = Color(0xFF64748B)
val DarkBorder = Color(0xFF26354D)
val DarkPrimaryAccent = Color(0xFF3B82F6)
val DarkOrangeAccent = Color(0xFFFF8A19)
val DarkBrandNavy = Color(0xFF0A1730)
val DarkOnPrimary = Color(0xFFFFFFFF)

// Design system color tokens for Light theme (Clean Modern Direction)
val LightBackground = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceElevated = Color(0xFFF1F5F9)
val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF64748B)
val LightTextMuted = Color(0xFF94A3B8)
val LightBorder = Color(0xFFE2E8F0)
val LightPrimaryAccent = Color(0xFF1E60D5) // Clear modern blue
val LightOrangeAccent = Color(0xFFE87516)
val LightBrandNavy = Color(0xFF0F172A)
val LightOnPrimary = Color(0xFFFFFFFF)

// Semantic and accent palette
val SuccessGreen = Color(0xFF16A34A)
val SuccessGreenLight = Color(0xFFDCFCE7)
val ErrorRed = Color(0xFFDC2626)
val ErrorRedLight = Color(0xFFFEE2E2)
val WarningOrange = Color(0xFFD97706)
val WarningOrangeLight = Color(0xFFFEF3C7)

// Rich pastel action tokens for commercial UI fidelity
val PastelBlueBg = Color(0xFFEBF3FF)
val PastelBlueFg = Color(0xFF1E60D5)
val PastelIndigoBg = Color(0xFFEEF0FF)
val PastelIndigoFg = Color(0xFF4F46E5)
val PastelCyanBg = Color(0xFFE0F2FE)
val PastelCyanFg = Color(0xFF0284C7)
val PastelTealBg = Color(0xFFE6F8F3)
val PastelTealFg = Color(0xFF0D9488)
val PastelPurpleBg = Color(0xFFF3E8FF)
val PastelPurpleFg = Color(0xFF7C3AED)
val PastelRoseBg = Color(0xFFFFF1F2)
val PastelRoseFg = Color(0xFFE11D48)

// Ambient background and card tokens
val AmbientBgTop = Color(0xFFEFF6FF)
val AmbientBgBottom = Color(0xFFF8FAFC)
val CardBorderColor = Color(0xFFE9EEF5)
val CardShadowColor = Color(0x120F172A)

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
  val success: Color = SuccessGreen,
  val warning: Color = WarningOrange,
  val emergency: Color = ErrorRed,
  val surfaceVariant: Color = if (isDark) DarkSurfaceElevated else LightSurfaceElevated,
  val primarySubtle: Color = if (isDark) Color(0xFF1E293B) else Color(0xFFEFF5FF),
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

val LocalPeykrColors = staticCompositionLocalOf { LightPeykrColors }

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
