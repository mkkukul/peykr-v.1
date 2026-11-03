package com.peykr.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val AppHorizontalPadding = 24.dp
val GlassShape = RoundedCornerShape(22.dp)

@Composable
fun GlassCard(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  content: @Composable () -> Unit,
) {
  val clickableModifier = if (onClick == null) Modifier else Modifier
    .clickable(onClick = onClick)
    .semantics { role = Role.Button }
  Box(
    modifier = modifier
      .then(clickableModifier)
      .clip(GlassShape)
      .background(Brush.linearGradient(listOf(Color(0x1CFFFFFF), Color(0x0BFFFFFF))))
      .border(BorderStroke(1.dp, GlassBorder), GlassShape)
      .padding(18.dp),
  ) {
    content()
  }
}

@Composable
fun GradientButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier
      .height(56.dp)
      .clip(RoundedCornerShape(18.dp))
      .clickable(onClick = onClick),
    color = Color.Transparent,
    contentColor = Navy950,
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(Brush.horizontalGradient(listOf(PrimaryTurquoise, Cyan, Violet)))
        .padding(horizontal = 20.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(text = text, fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
fun StatusIndicator(
  label: String,
  active: Boolean,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(
      modifier = Modifier
        .size(8.dp)
        .background(if (active) SuccessGreen else TextMuted, CircleShape),
    )
    Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = TextSecondary)
  }
}

@Composable
fun SectionHeader(
  title: String,
  supportingText: String? = null,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier) {
    Text(title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, color = TextPrimary)
    if (supportingText != null) {
      Spacer(Modifier.height(6.dp))
      Text(supportingText, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
  }
}

@Composable
fun IconContainer(
  symbol: String,
  contentDescription: String,
  tint: Color = PrimaryTurquoise,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .size(42.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(tint.copy(alpha = 0.13f))
      .semantics { this.contentDescription = contentDescription },
    contentAlignment = Alignment.Center,
  ) {
    Text(symbol, color = tint, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
  }
}

@Composable
fun EmptyState(
  title: String,
  supportingText: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Box(
      modifier = Modifier
        .size(104.dp)
        .clip(CircleShape)
        .background(Brush.radialGradient(listOf(Purple.copy(alpha = 0.4f), Color.Transparent))),
      contentAlignment = Alignment.Center,
    ) {
      Text("✦", color = PrimaryTurquoise, style = androidx.compose.material3.MaterialTheme.typography.displaySmall)
    }
    Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = TextPrimary)
    Text(
      supportingText,
      style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
      color = TextSecondary,
      modifier = Modifier.padding(horizontal = 18.dp),
    )
  }
}

@Composable
fun SettingsRow(
  symbol: String,
  title: String,
  supportingText: String,
  tint: Color,
  onClick: (() -> Unit)? = null,
) {
  GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconContainer(symbol, title, tint)
      Spacer(Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text(supportingText, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = TextSecondary)
      }
      Text("›", color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
    }
  }
}
