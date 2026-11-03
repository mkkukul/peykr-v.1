package com.peykr.app.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val ColorOutline = androidx.compose.ui.graphics.Color(0xFF5F6C84)

private val DarkColorScheme = darkColorScheme(
  primary = PrimaryTurquoise,
  onPrimary = Navy950,
  primaryContainer = primaryContainerDark,
  onPrimaryContainer = TextPrimary,
  secondary = Purple,
  onSecondary = TextPrimary,
  tertiary = Cyan,
  background = Navy950,
  onBackground = TextPrimary,
  surface = Navy900,
  onSurface = TextPrimary,
  surfaceVariant = Navy800,
  onSurfaceVariant = TextSecondary,
  outline = ColorOutline,
)

private val LightColorScheme = lightColorScheme(
  primary = primaryLight,
  primaryContainer = primaryContainerLight,
  secondary = secondaryLight,
  tertiary = tertiaryLight,
)

@Composable
fun PEYKRTheme(
  darkTheme: Boolean = true,
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

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
