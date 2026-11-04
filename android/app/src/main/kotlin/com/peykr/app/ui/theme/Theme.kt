package com.peykr.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.peykr.app.R

private val DarkColorScheme = darkColorScheme(
  primary = DarkPrimaryAccent,
  onPrimary = DarkOnPrimary,
  primaryContainer = Color(0xFF064B51),
  onPrimaryContainer = DarkTextPrimary,
  secondary = DarkTextSecondary,
  onSecondary = DarkBackground,
  tertiary = DarkPrimaryAccent,
  background = DarkBackground,
  onBackground = DarkTextPrimary,
  surface = DarkSurface,
  onSurface = DarkTextPrimary,
  surfaceVariant = DarkSurfaceElevated,
  onSurfaceVariant = DarkTextSecondary,
  outline = DarkBorder,
)

private val LightColorScheme = lightColorScheme(
  primary = LightPrimaryAccent,
  onPrimary = LightOnPrimary,
  primaryContainer = Color(0xFFE0F7F6),
  onPrimaryContainer = LightTextPrimary,
  secondary = LightTextSecondary,
  onSecondary = Color.White,
  tertiary = LightBrandNavy,
  background = LightBackground,
  onBackground = LightTextPrimary,
  surface = LightSurface,
  onSurface = LightTextPrimary,
  surfaceVariant = Color(0xFFEDF2F7),
  onSurfaceVariant = LightTextSecondary,
  outline = LightBorder,
)

@Composable
fun PEYKRTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }
  val peykrColors = if (darkTheme) DarkPeykrColors else LightPeykrColors

  CompositionLocalProvider(LocalPeykrColors provides peykrColors) {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}

@Composable
fun peykrLogo(): Painter = painterResource(
  if (PeykrTheme.colors.isDark) R.drawable.peykr_logo_dark else R.drawable.peykr_logo_light
)

@Composable
fun peykrEmblem(): Painter = painterResource(
  if (PeykrTheme.colors.isDark) R.drawable.peykr_emblem_dark else R.drawable.peykr_emblem_light
)
