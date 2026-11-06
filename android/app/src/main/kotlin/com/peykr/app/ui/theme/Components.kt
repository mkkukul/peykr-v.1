package com.peykr.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.peykr.app.Main
import com.peykr.app.MessagesList
import com.peykr.app.Settings
import com.peykr.app.TrustedPeople

val AppHorizontalPadding = 20.dp
val GlassShape = RoundedCornerShape(20.dp)

enum class PremiumIconKind {
  Shield, People, Location, Settings, Home, Info, Theme, Language, Backup, Device, Arrow,
  Message, File, SOS, Bell, Lock, Search, Phone, Video, Send, Attachment, CheckAll, MicroChevron, Plus
}

/**
 * Screen container providing soft ambient blue gradients for high visual fidelity.
 */
@Composable
fun PeykrScreenContainer(
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit
) {
  val colors = PeykrTheme.colors
  val gradient = if (colors.isDark) {
    listOf(
      Color(0xFF0F172A),
      Color(0xFF090E17),
      Color(0xFF05080E)
    )
  } else {
    listOf(
      AmbientBgTop,
      Color(0xFFF6FAFE),
      AmbientBgBottom
    )
  }
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Brush.verticalGradient(gradient)),
    content = content
  )
}

/**
 * Modern layered surface card with subtle elevation and refined borders.
 */
@Composable
fun PeykrCard(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  containerColor: Color = PeykrTheme.colors.surface,
  borderColor: Color = PeykrTheme.colors.border,
  shape: RoundedCornerShape = RoundedCornerShape(20.dp),
  elevation: Dp = 2.dp,
  contentPadding: PaddingValues = PaddingValues(16.dp),
  content: @Composable ColumnScope.() -> Unit
) {
  val action = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick).semantics { role = Role.Button }
  Column(
    modifier
      .shadow(elevation = elevation, shape = shape, spotColor = CardShadowColor, ambientColor = CardShadowColor)
      .clip(shape)
      .background(containerColor)
      .border(1.dp, borderColor, shape)
      .then(action)
      .padding(contentPadding),
    content = content
  )
}

/**
 * Grouped container for Settings and organized option groups (iOS/Modern Android style).
 */
@Composable
fun PeykrGroupedCard(
  modifier: Modifier = Modifier,
  shape: RoundedCornerShape = RoundedCornerShape(20.dp),
  containerColor: Color = PeykrTheme.colors.surface,
  borderColor: Color = PeykrTheme.colors.border,
  elevation: Dp = 2.dp,
  content: @Composable ColumnScope.() -> Unit
) {
  Column(
    modifier
      .fillMaxWidth()
      .shadow(elevation = elevation, shape = shape, spotColor = CardShadowColor, ambientColor = CardShadowColor)
      .clip(shape)
      .background(containerColor)
      .border(1.dp, borderColor, shape),
    content = content
  )
}

/**
 * Row item inside a PeykrGroupedCard with tailored pastel icon, text hierarchy, and micro-divider.
 */
@Composable
fun PeykrGroupedRow(
  icon: PremiumIconKind,
  iconBg: Color,
  iconFg: Color,
  title: String,
  detail: String? = null,
  onClick: (() -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
  showDivider: Boolean = true
) {
  Column(Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(42.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(iconBg),
        contentAlignment = Alignment.Center
      ) {
        PremiumIcon(icon, title, tint = iconFg, modifier = Modifier.size(22.dp))
      }
      Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.SemiBold,
          color = PeykrTheme.colors.textPrimary
        )
        if (detail != null) {
          Spacer(Modifier.height(2.dp))
          Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = PeykrTheme.colors.textSecondary
          )
        }
      }
      if (trailing != null) {
        trailing()
      } else if (onClick != null) {
        PremiumIcon(PremiumIconKind.MicroChevron, "", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
      }
    }
    if (showDivider) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 72.dp)
          .height(1.dp)
          .background(PeykrTheme.colors.border)
      )
    }
  }
}

@Composable
fun PremiumSurface(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit
) {
  PeykrCard(
    modifier = modifier,
    onClick = onClick,
    containerColor = PeykrTheme.colors.surface,
    borderColor = PeykrTheme.colors.border,
    shape = GlassShape,
    content = content
  )
}

@Composable
fun PremiumIcon(
  kind: PremiumIconKind,
  description: String,
  tint: Color = Color.Unspecified,
  modifier: Modifier = Modifier
) {
  val resolvedTint = if (tint != Color.Unspecified) tint else PeykrTheme.colors.primaryAccent
  Canvas(modifier.size(24.dp).semantics { contentDescription = description }) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (kind) {
      PremiumIconKind.Shield -> {
        val path = Path().apply {
          moveTo(center.x, 3.dp.toPx())
          lineTo(size.width - 4.dp.toPx(), 7.dp.toPx())
          lineTo(size.width - 5.dp.toPx(), size.height * .62f)
          quadraticTo(center.x, size.height - 3.dp.toPx(), 5.dp.toPx(), size.height * .62f)
          lineTo(4.dp.toPx(), 7.dp.toPx())
          close()
        }
        drawPath(path, color = resolvedTint, style = stroke)
        // Keyhole or inner check
        drawLine(resolvedTint, Offset(center.x, 9.dp.toPx()), Offset(center.x, 14.dp.toPx()), 1.8.dp.toPx(), cap = StrokeCap.Round)
      }
      PremiumIconKind.Device -> {
        drawRoundRect(color = resolvedTint, topLeft = Offset(6.dp.toPx(), 3.dp.toPx()), size = Size(size.width - 12.dp.toPx(), size.height - 6.dp.toPx()), cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()), style = stroke)
        drawCircle(resolvedTint, 1.5.dp.toPx(), center.copy(y = size.height - 7.dp.toPx()))
      }
      PremiumIconKind.People -> {
        drawCircle(resolvedTint, 4.dp.toPx(), center.copy(y = 8.dp.toPx(), x = center.x - 4.dp.toPx()), style = stroke)
        drawCircle(resolvedTint, 3.5.dp.toPx(), center.copy(y = 9.dp.toPx(), x = center.x + 6.dp.toPx()), style = stroke)
        drawLine(resolvedTint, center.copy(x = center.x - 10.dp.toPx(), y = 19.dp.toPx()), center.copy(x = center.x + 1.dp.toPx(), y = 19.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(resolvedTint, center.copy(x = center.x + 2.dp.toPx(), y = 20.dp.toPx()), center.copy(x = center.x + 11.dp.toPx(), y = 20.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
      }
      PremiumIconKind.Location -> {
        drawCircle(resolvedTint, 6.5.dp.toPx(), center.copy(y = 9.dp.toPx()), style = stroke)
        drawCircle(resolvedTint, 2.2.dp.toPx(), center.copy(y = 9.dp.toPx()))
        drawLine(resolvedTint, center.copy(y = 15.5.dp.toPx()), center.copy(y = size.height - 3.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
      }
      PremiumIconKind.Home -> {
        drawLine(resolvedTint, center.copy(x = 4.dp.toPx(), y = 12.dp.toPx()), center.copy(x = center.x, y = 4.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(resolvedTint, center.copy(x = center.x, y = 4.dp.toPx()), center.copy(x = size.width - 4.dp.toPx(), y = 12.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
        drawRoundRect(color = resolvedTint, topLeft = Offset(6.dp.toPx(), 11.dp.toPx()), size = Size(size.width - 12.dp.toPx(), 10.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()), style = stroke)
      }
      PremiumIconKind.Settings -> {
        drawCircle(resolvedTint, 6.5.dp.toPx(), center, style = stroke)
        drawCircle(resolvedTint, 2.2.dp.toPx(), center)
        for (i in 0..5) {
          val angle = i * Math.PI / 3
          val start = center + Offset(kotlin.math.cos(angle).toFloat() * 7.5.dp.toPx(), kotlin.math.sin(angle).toFloat() * 7.5.dp.toPx())
          val end = center + Offset(kotlin.math.cos(angle).toFloat() * 10.5.dp.toPx(), kotlin.math.sin(angle).toFloat() * 10.5.dp.toPx())
          drawLine(resolvedTint, start, end, 2.dp.toPx(), cap = StrokeCap.Round)
        }
      }
      PremiumIconKind.Info -> {
        drawCircle(resolvedTint, 9.dp.toPx(), center, style = stroke)
        drawCircle(resolvedTint, 1.2.dp.toPx(), center.copy(y = 9.dp.toPx()))
        drawLine(resolvedTint, center.copy(y = 12.5.dp.toPx()), center.copy(y = 16.5.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
      }
      PremiumIconKind.Theme -> {
        drawArc(resolvedTint, 45f, 270f, false, style = stroke)
        drawCircle(resolvedTint, 2.5.dp.toPx(), center.copy(x = center.x + 3.dp.toPx(), y = center.y - 5.dp.toPx()))
      }
      PremiumIconKind.Language -> {
        drawCircle(resolvedTint, 9.dp.toPx(), center, style = stroke)
        drawOval(color = resolvedTint, topLeft = Offset(center.x - 4.5.dp.toPx(), center.y - 9.dp.toPx()), size = Size(9.dp.toPx(), 18.dp.toPx()), style = stroke)
        drawLine(resolvedTint, center.copy(x = 4.dp.toPx()), center.copy(x = size.width - 4.dp.toPx()), 1.8.dp.toPx())
      }
      PremiumIconKind.Backup -> {
        drawRoundRect(color = resolvedTint, topLeft = Offset(5.dp.toPx(), 7.dp.toPx()), size = Size(size.width - 10.dp.toPx(), 14.dp.toPx()), cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()), style = stroke)
        drawLine(resolvedTint, center.copy(y = 7.dp.toPx()), center.copy(y = 21.dp.toPx()), 2.dp.toPx())
      }
      PremiumIconKind.Arrow -> {
        drawLine(resolvedTint, center.copy(x = 6.dp.toPx()), center.copy(x = size.width - 6.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(resolvedTint, center.copy(x = size.width - 10.dp.toPx(), y = 7.dp.toPx()), center.copy(x = size.width - 6.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(resolvedTint, center.copy(x = size.width - 10.dp.toPx(), y = size.height - 7.dp.toPx()), center.copy(x = size.width - 6.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
      }
      PremiumIconKind.Message -> {
        val bubble = Path().apply {
          moveTo(5.dp.toPx(), 6.dp.toPx())
          lineTo(size.width - 5.dp.toPx(), 6.dp.toPx())
          quadraticTo(size.width - 3.dp.toPx(), 6.dp.toPx(), size.width - 3.dp.toPx(), 8.dp.toPx())
          lineTo(size.width - 3.dp.toPx(), 16.dp.toPx())
          quadraticTo(size.width - 3.dp.toPx(), 18.dp.toPx(), size.width - 5.dp.toPx(), 18.dp.toPx())
          lineTo(10.dp.toPx(), 18.dp.toPx())
          lineTo(6.dp.toPx(), 21.dp.toPx())
          lineTo(7.dp.toPx(), 18.dp.toPx())
          lineTo(5.dp.toPx(), 18.dp.toPx())
          quadraticTo(3.dp.toPx(), 18.dp.toPx(), 3.dp.toPx(), 16.dp.toPx())
          lineTo(3.dp.toPx(), 8.dp.toPx())
          quadraticTo(3.dp.toPx(), 6.dp.toPx(), 5.dp.toPx(), 6.dp.toPx())
          close()
        }
        drawPath(bubble, color = resolvedTint, style = stroke)
        drawLine(resolvedTint, Offset(7.dp.toPx(), 10.5.dp.toPx()), Offset(size.width - 7.dp.toPx(), 10.5.dp.toPx()), 1.8.dp.toPx(), cap = StrokeCap.Round)
        drawLine(resolvedTint, Offset(7.dp.toPx(), 14.dp.toPx()), Offset(size.width - 11.dp.toPx(), 14.dp.toPx()), 1.8.dp.toPx(), cap = StrokeCap.Round)
      }
      PremiumIconKind.File -> {
        val page = Path().apply {
          moveTo(6.dp.toPx(), 3.dp.toPx())
          lineTo(size.width - 10.dp.toPx(), 3.dp.toPx())
          lineTo(size.width - 5.dp.toPx(), 8.dp.toPx())
          lineTo(size.width - 5.dp.toPx(), size.height - 3.dp.toPx())
          lineTo(6.dp.toPx(), size.height - 3.dp.toPx())
          close()
        }
        drawPath(page, color = resolvedTint, style = stroke)
        drawLine(resolvedTint, Offset(9.dp.toPx(), 11.dp.toPx()), Offset(size.width - 9.dp.toPx(), 11.dp.toPx()), 1.8.dp.toPx(), cap = StrokeCap.Round)
        drawLine(resolvedTint, Offset(9.dp.toPx(), 14.5.dp.toPx()), Offset(size.width - 9.dp.toPx(), 14.5.dp.toPx()), 1.8.dp.toPx(), cap = StrokeCap.Round)
      }
      PremiumIconKind.SOS -> {
        val tri = Path().apply {
          moveTo(center.x, 3.dp.toPx())
          lineTo(size.width - 3.dp.toPx(), size.height - 4.dp.toPx())
          lineTo(3.dp.toPx(), size.height - 4.dp.toPx())
          close()
        }
        drawPath(tri, color = resolvedTint, style = stroke)
        drawLine(resolvedTint, Offset(center.x, 8.5.dp.toPx()), Offset(center.x, 13.5.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(resolvedTint, 1.2.dp.toPx(), Offset(center.x, 16.5.dp.toPx()))
      }
      PremiumIconKind.Bell -> {
        drawCircle(resolvedTint, 1.5.dp.toPx(), Offset(center.x, 3.dp.toPx()))
        val bellPath = Path().apply {
          moveTo(7.dp.toPx(), 17.dp.toPx())
          lineTo(size.width - 7.dp.toPx(), 17.dp.toPx())
          lineTo(size.width - 8.dp.toPx(), 11.dp.toPx())
          quadraticTo(size.width - 8.dp.toPx(), 5.dp.toPx(), center.x, 5.dp.toPx())
          quadraticTo(8.dp.toPx(), 5.dp.toPx(), 8.dp.toPx(), 11.dp.toPx())
          close()
        }
        drawPath(bellPath, color = resolvedTint, style = stroke)
        drawLine(resolvedTint, Offset(center.x - 2.5.dp.toPx(), 19.5.dp.toPx()), Offset(center.x + 2.5.dp.toPx(), 19.5.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
      }
      PremiumIconKind.Lock -> {
        drawRoundRect(color = resolvedTint, topLeft = Offset(5.dp.toPx(), 10.dp.toPx()), size = Size(size.width - 10.dp.toPx(), 11.dp.toPx()), cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()), style = stroke)
        drawArc(resolvedTint, 180f, 180f, false, topLeft = Offset(8.dp.toPx(), 4.dp.toPx()), size = Size(size.width - 16.dp.toPx(), 11.dp.toPx()), style = stroke)
        drawCircle(resolvedTint, 1.5.dp.toPx(), Offset(center.x, 14.5.dp.toPx()))
      }
      PremiumIconKind.Search -> {
        drawCircle(resolvedTint, 6.dp.toPx(), Offset(10.dp.toPx(), 10.dp.toPx()), style = stroke)
        drawLine(resolvedTint, Offset(14.5.dp.toPx(), 14.5.dp.toPx()), Offset(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
      }
      PremiumIconKind.Phone -> {
        val phonePath = Path().apply {
          moveTo(7.dp.toPx(), 4.dp.toPx())
          lineTo(10.dp.toPx(), 4.dp.toPx())
          lineTo(11.5.dp.toPx(), 8.5.dp.toPx())
          lineTo(9.5.dp.toPx(), 10.dp.toPx())
          quadraticTo(11.dp.toPx(), 13.dp.toPx(), 14.dp.toPx(), 14.5.dp.toPx())
          lineTo(15.5.dp.toPx(), 12.5.dp.toPx())
          lineTo(20.dp.toPx(), 14.dp.toPx())
          lineTo(20.dp.toPx(), 17.dp.toPx())
          quadraticTo(7.dp.toPx(), 20.dp.toPx(), 4.dp.toPx(), 7.dp.toPx())
          close()
        }
        drawPath(phonePath, color = resolvedTint, style = stroke)
      }
      PremiumIconKind.Video -> {
        drawRoundRect(color = resolvedTint, topLeft = Offset(4.dp.toPx(), 6.dp.toPx()), size = Size(11.dp.toPx(), 12.dp.toPx()), cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()), style = stroke)
        val cam = Path().apply {
          moveTo(15.dp.toPx(), 9.dp.toPx())
          lineTo(20.dp.toPx(), 6.dp.toPx())
          lineTo(20.dp.toPx(), 18.dp.toPx())
          lineTo(15.dp.toPx(), 15.dp.toPx())
          close()
        }
        drawPath(cam, color = resolvedTint, style = stroke)
      }
      PremiumIconKind.Send -> {
        val sendPath = Path().apply {
          moveTo(4.dp.toPx(), 4.dp.toPx())
          lineTo(20.dp.toPx(), 12.dp.toPx())
          lineTo(4.dp.toPx(), 20.dp.toPx())
          lineTo(8.dp.toPx(), 12.dp.toPx())
          close()
        }
        drawPath(sendPath, color = resolvedTint, style = stroke)
        drawLine(resolvedTint, Offset(8.dp.toPx(), 12.dp.toPx()), Offset(14.dp.toPx(), 12.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
      }
      PremiumIconKind.Attachment -> {
        val clip = Path().apply {
          moveTo(8.dp.toPx(), 12.dp.toPx())
          lineTo(14.dp.toPx(), 6.dp.toPx())
          quadraticTo(17.dp.toPx(), 3.dp.toPx(), 19.dp.toPx(), 6.dp.toPx())
          quadraticTo(21.dp.toPx(), 9.dp.toPx(), 18.dp.toPx(), 12.dp.toPx())
          lineTo(11.dp.toPx(), 19.dp.toPx())
          quadraticTo(7.dp.toPx(), 23.dp.toPx(), 4.dp.toPx(), 19.dp.toPx())
          quadraticTo(1.dp.toPx(), 15.dp.toPx(), 5.dp.toPx(), 11.dp.toPx())
          lineTo(12.dp.toPx(), 5.dp.toPx())
        }
        drawPath(clip, color = resolvedTint, style = stroke)
      }
      PremiumIconKind.CheckAll -> {
        // Double tick
        drawLine(resolvedTint, Offset(3.dp.toPx(), 12.dp.toPx()), Offset(7.dp.toPx(), 16.dp.toPx()), 1.8.dp.toPx(), cap = StrokeCap.Round)
        drawLine(resolvedTint, Offset(7.dp.toPx(), 16.dp.toPx()), Offset(14.dp.toPx(), 8.dp.toPx()), 1.8.dp.toPx(), cap = StrokeCap.Round)
        drawLine(resolvedTint, Offset(9.dp.toPx(), 12.dp.toPx()), Offset(13.dp.toPx(), 16.dp.toPx()), 1.8.dp.toPx(), cap = StrokeCap.Round)
        drawLine(resolvedTint, Offset(13.dp.toPx(), 16.dp.toPx()), Offset(20.dp.toPx(), 8.dp.toPx()), 1.8.dp.toPx(), cap = StrokeCap.Round)
      }
      PremiumIconKind.MicroChevron -> {
        drawLine(resolvedTint, Offset(6.dp.toPx(), 4.dp.toPx()), Offset(11.dp.toPx(), 12.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(resolvedTint, Offset(11.dp.toPx(), 12.dp.toPx()), Offset(6.dp.toPx(), 20.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
      }
      PremiumIconKind.Plus -> {
        drawLine(resolvedTint, Offset(center.x, 5.dp.toPx()), Offset(center.x, size.height - 5.dp.toPx()), 2.2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(resolvedTint, Offset(5.dp.toPx(), center.y), Offset(size.width - 5.dp.toPx(), center.y), 2.2.dp.toPx(), cap = StrokeCap.Round)
      }
    }
  }
}

@Composable
fun SectionHeader(title: String, supportingText: String? = null, modifier: Modifier = Modifier) {
  Column(modifier) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = PeykrTheme.colors.textPrimary,
      fontSize = 17.sp,
      letterSpacing = (-0.2).sp
    )
    if (supportingText != null) {
      Spacer(Modifier.height(3.dp))
      Text(
        text = supportingText,
        style = MaterialTheme.typography.bodySmall,
        color = PeykrTheme.colors.textSecondary,
        fontSize = 13.sp
      )
    }
  }
}

@Composable
fun PremiumInfoRow(
  icon: PremiumIconKind,
  title: String,
  detail: String,
  iconDescription: String,
  onClick: (() -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = { PremiumIcon(PremiumIconKind.MicroChevron, "", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp)) }
) {
  PeykrCard(
    modifier = Modifier.fillMaxWidth(),
    onClick = onClick,
    containerColor = Color.White,
    borderColor = CardBorderColor,
    shape = RoundedCornerShape(16.dp),
    contentPadding = PaddingValues(14.dp)
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Box(
        Modifier
          .size(42.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(PeykrTheme.colors.primarySubtle),
        contentAlignment = Alignment.Center
      ) {
        PremiumIcon(icon, iconDescription, tint = PeykrTheme.colors.primaryAccent)
      }
      Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = PeykrTheme.colors.textPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(detail, color = PeykrTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
      }
      trailing?.invoke()
    }
  }
}

@Composable
fun PeykrBadge(
  text: String,
  color: Color = PeykrTheme.colors.primaryAccent,
  backgroundColor: Color = PeykrTheme.colors.primarySubtle,
  modifier: Modifier = Modifier
) {
  Box(
    modifier
      .clip(RoundedCornerShape(8.dp))
      .background(backgroundColor)
      .padding(horizontal = 9.dp, vertical = 4.dp)
  ) {
    Text(
      text = text,
      color = color,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      fontSize = 11.sp
    )
  }
}

@Composable
fun PeykrSearchField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String = "Ara...",
  modifier: Modifier = Modifier
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    placeholder = { Text(placeholder, color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodyMedium, fontSize = 14.sp) },
    leadingIcon = {
      PremiumIcon(PremiumIconKind.Search, "Ara", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
    },
    singleLine = true,
    shape = RoundedCornerShape(16.dp),
    colors = OutlinedTextFieldDefaults.colors(
      focusedContainerColor = PeykrTheme.colors.surface,
      unfocusedContainerColor = PeykrTheme.colors.surface,
      focusedBorderColor = PeykrTheme.colors.primaryAccent,
      unfocusedBorderColor = PeykrTheme.colors.border,
      cursorColor = PeykrTheme.colors.primaryAccent,
      focusedTextColor = PeykrTheme.colors.textPrimary,
      unfocusedTextColor = PeykrTheme.colors.textPrimary
    ),
    modifier = modifier
      .fillMaxWidth()
      .shadow(1.dp, RoundedCornerShape(16.dp), spotColor = CardShadowColor)
  )
}

/**
 * Clean 4-destination bottom navigation bar adhering strictly to the Peykr modern design system.
 */
@Composable
fun PeykrBottomNavigationBar(
  current: NavKey,
  onNavigate: (NavKey) -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier
      .fillMaxWidth()
      .shadow(elevation = 10.dp, spotColor = Color(0x180F172A), ambientColor = Color(0x100F172A))
      .background(PeykrTheme.colors.surface)
      .border(0.5.dp, PeykrTheme.colors.border)
      .navigationBarsPadding()
      .height(72.dp)
      .padding(horizontal = 12.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.SpaceAround,
    verticalAlignment = Alignment.CenterVertically
  ) {
    BottomNavItem(
      label = "Ana Sayfa",
      icon = PremiumIconKind.Home,
      selected = current == Main,
      onClick = { onNavigate(Main) }
    )
    BottomNavItem(
      label = "Mesajlar",
      icon = PremiumIconKind.Message,
      selected = current == MessagesList,
      onClick = { onNavigate(MessagesList) }
    )
    BottomNavItem(
      label = "Kişiler",
      icon = PremiumIconKind.People,
      selected = current == TrustedPeople,
      onClick = { onNavigate(TrustedPeople) }
    )
    BottomNavItem(
      label = "Ayarlar",
      icon = PremiumIconKind.Settings,
      selected = current == Settings,
      onClick = { onNavigate(Settings) }
    )
  }
}

@Composable
private fun BottomNavItem(
  label: String,
  icon: PremiumIconKind,
  selected: Boolean,
  onClick: () -> Unit
) {
  val activeColor = PeykrTheme.colors.primaryAccent
  val inactiveColor = PeykrTheme.colors.textMuted

  Column(
    modifier = Modifier
      .clip(RoundedCornerShape(14.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 4.dp)
      .semantics { contentDescription = label },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(3.dp)
  ) {
    Box(
      modifier = Modifier
        .clip(RoundedCornerShape(12.dp))
        .background(
          if (selected) {
            if (PeykrTheme.colors.isDark) {
              Brush.horizontalGradient(listOf(Color(0xFF1E293B), Color(0xFF283548)))
            } else {
              Brush.horizontalGradient(listOf(Color(0xFFE8F1FF), Color(0xFFF0F6FF)))
            }
          } else {
            Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
          }
        )
        .border(
          width = if (selected) 1.dp else 0.dp,
          color = if (selected) (if (PeykrTheme.colors.isDark) Color(0xFF334155) else Color(0xFFDCEBFF)) else Color.Transparent,
          shape = RoundedCornerShape(12.dp)
        )
        .padding(horizontal = 14.dp, vertical = 5.dp),
      contentAlignment = Alignment.Center
    ) {
      PremiumIcon(
        kind = icon,
        description = label,
        tint = if (selected) activeColor else inactiveColor,
        modifier = Modifier.size(20.dp)
      )
    }
    Text(
      text = label,
      color = if (selected) activeColor else inactiveColor,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      fontSize = 11.sp
    )
  }
}
