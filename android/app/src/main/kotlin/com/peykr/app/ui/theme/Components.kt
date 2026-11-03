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
import androidx.compose.material3.MaterialTheme
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
val GlassShape = RoundedCornerShape(20.dp)

@Composable
fun GlassCard(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  content: @Composable () -> Unit,
) {
  val interactionModifier = if (onClick == null) {
    Modifier
  } else {
    Modifier
      .clickable(onClick = onClick)
      .semantics { role = Role.Button }
  }
  Box(
    modifier = modifier
      .then(interactionModifier)
      .clip(GlassShape)
      .background(Color(0xB310182A))
      .border(BorderStroke(1.dp, GlassBorder), GlassShape)
      .padding(16.dp),
  ) {
    content()
  }
}

@Composable
fun PrimaryActionButton(
  text: String,
  onClick: () -> Unit,
  enabled: Boolean = true,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(16.dp)
  Box(
    modifier = modifier
      .height(52.dp)
      .clip(shape)
      .background(
        if (enabled) {
          Brush.horizontalGradient(listOf(PrimaryTurquoise, Cyan))
        } else {
          Brush.horizontalGradient(listOf(Color(0xFF27354B), Color(0xFF1B273B)))
        },
      )
      .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
      .semantics {
        if (!enabled) contentDescription = "$text, kullanılamıyor"
      },
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      color = if (enabled) Navy950 else TextMuted,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.Bold,
    )
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
    horizontalArrangement = Arrangement.spacedBy(7.dp),
  ) {
    Box(
      modifier = Modifier
        .size(7.dp)
        .background(if (active) SuccessGreen else TextMuted, CircleShape),
    )
    Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
  }
}

@Composable
fun SectionHeader(
  title: String,
  supportingText: String? = null,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier) {
    Text(title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
    if (supportingText != null) {
      Spacer(Modifier.height(6.dp))
      Text(supportingText, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
  }
}

@Composable
fun IconContainer(
  symbol: String,
  description: String,
  tint: Color = PrimaryTurquoise,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .size(40.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(tint.copy(alpha = 0.12f))
      .semantics { contentDescription = description },
    contentAlignment = Alignment.Center,
  ) {
    Text(symbol, color = tint, style = MaterialTheme.typography.titleMedium)
  }
}

@Composable
fun SettingsRow(
  symbol: String,
  title: String,
  supportingText: String,
  tint: Color,
  enabled: Boolean = false,
  onClick: (() -> Unit)? = null,
) {
  val effectiveClick = if (enabled) onClick else null
  GlassCard(modifier = Modifier.fillMaxWidth(), onClick = effectiveClick) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconContainer(symbol, title, tint.copy(alpha = if (enabled) 1f else 0.7f))
      Spacer(Modifier.width(13.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text(supportingText, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
      }
      Text("›", color = TextMuted, style = MaterialTheme.typography.headlineSmall)
    }
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
        .size(112.dp)
        .clip(CircleShape)
        .background(
          Brush.radialGradient(
            listOf(Color(0x3334D9FF), Color(0x101A526C), Color.Transparent),
          ),
        ),
      contentAlignment = Alignment.Center,
    ) {
      Text("⌁", color = PrimaryTurquoise, style = MaterialTheme.typography.displaySmall)
    }
    Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
    Text(
      supportingText,
      style = MaterialTheme.typography.bodyMedium,
      color = TextSecondary,
      modifier = Modifier.padding(horizontal = 24.dp),
    )
  }
}
