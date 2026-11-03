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
val GlassShape = RoundedCornerShape(16.dp)

@Composable
fun GlassCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
  val interaction = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick).semantics { role = Role.Button }
  Box(modifier.then(interaction).clip(GlassShape).background(DarkPanel).border(BorderStroke(1.dp, DividerDark), GlassShape).padding(16.dp)) { content() }
}

@Composable
fun PrimaryActionButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
  Box(modifier.height(50.dp).clip(RoundedCornerShape(14.dp)).background(if (enabled) Brush.horizontalGradient(listOf(PrimaryTurquoise, Cyan)) else Brush.horizontalGradient(listOf(DarkPanel, DarkPanel))).then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier).semantics { if (!enabled) contentDescription = "$text, kullanılamıyor" }, contentAlignment = Alignment.Center) {
    Text(text, color = if (enabled) Navy950 else TextMuted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
  }
}

@Composable
fun StatusIndicator(label: String, active: Boolean, modifier: Modifier = Modifier) { Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) { Box(Modifier.size(7.dp).background(if (active) SuccessGreen else TextMuted, CircleShape)); Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary) } }

@Composable
fun SectionHeader(title: String, supportingText: String? = null, modifier: Modifier = Modifier, color: Color = TextPrimary) { Column(modifier) { Text(title, style = MaterialTheme.typography.headlineSmall, color = color); if (supportingText != null) { Spacer(Modifier.height(6.dp)); Text(supportingText, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) } } }

@Composable
fun IconContainer(symbol: String, description: String, tint: Color = PrimaryTurquoise, modifier: Modifier = Modifier) { Box(modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(tint.copy(alpha = .10f)).semantics { contentDescription = description }, contentAlignment = Alignment.Center) { Text(symbol, color = tint, style = MaterialTheme.typography.titleMedium) } }

@Composable
fun SettingsRow(symbol: String, title: String, supportingText: String, tint: Color, enabled: Boolean = false, onClick: (() -> Unit)? = null) {
  val action = if (enabled) onClick else null
  GlassCard(Modifier.fillMaxWidth(), action) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconContainer(symbol, title, tint.copy(alpha = if (enabled) 1f else .72f)); Spacer(Modifier.width(13.dp)); Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary); Text(supportingText, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) } } }
}

@Composable
fun EmptyState(title: String, supportingText: String, modifier: Modifier = Modifier) { Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) { Box(Modifier.size(112.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0x3334D9FF), Color.Transparent))), contentAlignment = Alignment.Center) { Text("•", color = PrimaryTurquoise, style = MaterialTheme.typography.displaySmall) }; Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary); Text(supportingText, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.padding(horizontal = 24.dp)) } }
