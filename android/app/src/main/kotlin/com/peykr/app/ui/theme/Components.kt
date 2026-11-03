package com.peykr.app.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val AppHorizontalPadding = 24.dp

@Composable
fun SectionHeader(title: String, supportingText: String? = null, modifier: Modifier = Modifier, color: Color = TextPrimary) {
  Column(modifier) {
    Text(title, style = MaterialTheme.typography.headlineSmall, color = color)
    if (supportingText != null) {
      Spacer(Modifier.height(6.dp))
      Text(supportingText, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
  }
}
