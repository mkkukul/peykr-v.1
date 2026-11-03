package com.peykr.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val AppHorizontalPadding = 24.dp
val GlassShape = RoundedCornerShape(14.dp)

enum class PremiumIconKind { Shield, People, Location, Settings, Home, Info, Theme, Language, Backup, Device, Arrow }

@Composable
fun PremiumSurface(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
  val action = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick).semantics { role = Role.Button }
  Column(modifier.then(action).clip(GlassShape).background(DarkPanel).border(1.dp, DividerDark, GlassShape).padding(horizontal = 14.dp, vertical = 12.dp), content = content)
}

@Composable
fun PremiumIcon(kind: PremiumIconKind, description: String, tint: Color = PrimaryTurquoise, modifier: Modifier = Modifier) {
  Canvas(modifier.size(32.dp).semantics { contentDescription = description }) {
    val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
    val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
    when (kind) {
      PremiumIconKind.Shield, PremiumIconKind.Device -> {
        val path = androidx.compose.ui.graphics.Path().apply { moveTo(center.x, 5.dp.toPx()); lineTo(size.width - 8.dp.toPx(), 11.dp.toPx()); lineTo(size.width - 10.dp.toPx(), size.height * .62f); quadraticTo(center.x, size.height - 5.dp.toPx(), 10.dp.toPx(), size.height * .62f); lineTo(8.dp.toPx(), 11.dp.toPx()); close() }
        drawPath(path, color = tint, style = stroke)
        if (kind == PremiumIconKind.Device) drawCircle(tint, 2.dp.toPx(), center)
      }
      PremiumIconKind.People -> { drawCircle(tint, 5.dp.toPx(), center.copy(y = 11.dp.toPx(), x = center.x - 7.dp.toPx()), style = stroke); drawCircle(tint, 4.dp.toPx(), center.copy(y = 12.dp.toPx(), x = center.x + 8.dp.toPx()), style = stroke); drawLine(tint, center.copy(x = center.x - 16.dp.toPx(), y = 27.dp.toPx()), center.copy(x = center.x - 1.dp.toPx(), y = 27.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round); drawLine(tint, center.copy(x = center.x + 1.dp.toPx(), y = 27.dp.toPx()), center.copy(x = center.x + 16.dp.toPx(), y = 27.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round) }
      PremiumIconKind.Location -> { drawCircle(tint, 10.dp.toPx(), center.copy(y = 14.dp.toPx()), style = stroke); drawCircle(tint, 2.dp.toPx(), center.copy(y = 14.dp.toPx())); drawLine(tint, center.copy(y = 24.dp.toPx()), center.copy(y = size.height - 2.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round) }
      PremiumIconKind.Home -> { drawLine(tint, center.copy(x = 7.dp.toPx(), y = 18.dp.toPx()), center.copy(x = center.x, y = 7.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round); drawLine(tint, center.copy(x = center.x, y = 7.dp.toPx()), center.copy(x = size.width - 7.dp.toPx(), y = 18.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round); drawRoundRect(color = tint, topLeft = Offset(10.dp.toPx(), 17.dp.toPx()), size = Size(size.width - 20.dp.toPx(), 14.dp.toPx()), cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()), style = stroke) }
      PremiumIconKind.Settings -> { drawCircle(tint, 10.dp.toPx(), center, style = stroke); drawCircle(tint, 3.dp.toPx(), center); for (i in 0..7) { val angle = i * Math.PI / 4; val start = center + androidx.compose.ui.geometry.Offset(kotlin.math.cos(angle).toFloat() * 11.dp.toPx(), kotlin.math.sin(angle).toFloat() * 11.dp.toPx()); val end = center + androidx.compose.ui.geometry.Offset(kotlin.math.cos(angle).toFloat() * 15.dp.toPx(), kotlin.math.sin(angle).toFloat() * 15.dp.toPx()); drawLine(tint, start, end, 2.dp.toPx(), cap = StrokeCap.Round) } }
      PremiumIconKind.Info -> { drawCircle(tint, 13.dp.toPx(), center, style = stroke); drawCircle(tint, 1.5.dp.toPx(), center.copy(y = 12.dp.toPx())); drawLine(tint, center.copy(y = 17.dp.toPx()), center.copy(y = 22.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round) }
      PremiumIconKind.Theme -> { drawArc(tint, 45f, 270f, false, style = stroke); drawCircle(tint, 3.dp.toPx(), center.copy(x = center.x + 5.dp.toPx(), y = center.y - 8.dp.toPx())) }
      PremiumIconKind.Language -> { drawCircle(tint, 13.dp.toPx(), center, style = stroke); drawOval(color = tint, topLeft = Offset(center.x - 7.dp.toPx(), center.y - 13.dp.toPx()), size = Size(14.dp.toPx(), 26.dp.toPx()), style = stroke); drawLine(tint, center.copy(x = 5.dp.toPx()), center.copy(x = size.width - 5.dp.toPx()), 2.dp.toPx()) }
      PremiumIconKind.Backup -> { drawRoundRect(color = tint, topLeft = Offset(7.dp.toPx(), 9.dp.toPx()), size = Size(size.width - 14.dp.toPx(), 18.dp.toPx()), cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()), style = stroke); drawLine(tint, center.copy(y = 9.dp.toPx()), center.copy(y = 29.dp.toPx()), 2.dp.toPx()) }
      PremiumIconKind.Arrow -> { drawLine(tint, center.copy(x = 8.dp.toPx()), center.copy(x = size.width - 8.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round); drawLine(tint, center.copy(x = size.width - 13.dp.toPx(), y = 10.dp.toPx()), center.copy(x = size.width - 8.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round); drawLine(tint, center.copy(x = size.width - 13.dp.toPx(), y = size.height - 10.dp.toPx()), center.copy(x = size.width - 8.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round) }
    }
  }
}

@Composable
fun SectionHeader(title: String, supportingText: String? = null, modifier: Modifier = Modifier) { Column(modifier) { Text(title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary); if (supportingText != null) { Spacer(Modifier.height(6.dp)); Text(supportingText, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) } } }

@Composable
fun PremiumInfoRow(icon: PremiumIconKind, title: String, detail: String, iconDescription: String, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = { PremiumIcon(PremiumIconKind.Arrow, "") }) {
  PremiumSurface(Modifier.fillMaxWidth(), onClick) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { PremiumIcon(icon, iconDescription); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text(detail, color = TextSecondary, style = MaterialTheme.typography.bodySmall) }; trailing?.invoke() } }
}
