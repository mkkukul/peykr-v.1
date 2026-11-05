package com.peykr.app.ui.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peykr.app.domain.transfer.FileTransferEngine
import com.peykr.app.domain.transfer.TransferDirection
import com.peykr.app.domain.transfer.TransferSession
import com.peykr.app.domain.transfer.TransferStatus
import com.peykr.app.ui.theme.AppHorizontalPadding
import com.peykr.app.ui.theme.ErrorRed
import com.peykr.app.ui.theme.PeykrBadge
import com.peykr.app.ui.theme.PeykrCard
import com.peykr.app.ui.theme.PeykrTheme
import com.peykr.app.ui.theme.PremiumIcon
import com.peykr.app.ui.theme.PremiumIconKind
import com.peykr.app.ui.theme.SuccessGreen
import com.peykr.app.ui.theme.SuccessGreenLight

@Composable
fun FileTransferScreen(
  transferEngine: FileTransferEngine,
  onBack: () -> Unit,
) {
  val sessionsMap by transferEngine.activeSessions.collectAsStateWithLifecycle()
  val sessions = sessionsMap.values.toList()

  Scaffold(
    containerColor = PeykrTheme.colors.background,
    contentWindowInsets = WindowInsets.safeDrawing,
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = AppHorizontalPadding, vertical = 20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        TextButton(onClick = onBack) {
          Text("‹ Geri", color = PeykrTheme.colors.primaryAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.weight(1f))
        Text(
          "Yerel Dosya Aktarımı",
          color = PeykrTheme.colors.textPrimary,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(48.dp))
      }

      // Discovery Status Card
      PeykrCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = PeykrTheme.colors.surface,
        borderColor = PeykrTheme.colors.border,
        shape = RoundedCornerShape(16.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(SuccessGreenLight),
            contentAlignment = Alignment.Center
          ) {
            PremiumIcon(PremiumIconKind.File, "Dosya", tint = SuccessGreen)
          }

          Spacer(Modifier.width(14.dp))

          Column(Modifier.weight(1f)) {
            Text(
              "Yerel Ağ Keşfi Aktif",
              color = PeykrTheme.colors.textPrimary,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
              "Aynı Wi-Fi ağındaki veya eşleşen cihazlara doğrudan dosya gönderin.",
              color = PeykrTheme.colors.textSecondary,
              style = MaterialTheme.typography.bodySmall
            )
          }

          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(SuccessGreen)
          )
        }
      }

      Text(
        "Aktif Transferler",
        color = PeykrTheme.colors.textPrimary,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
      )

      if (sessions.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Box(
              modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(PeykrTheme.colors.primarySubtle),
              contentAlignment = Alignment.Center
            ) {
              PremiumIcon(PremiumIconKind.File, "Transfer", tint = PeykrTheme.colors.primaryAccent)
            }
            Text(
              "Şu anda devam eden bir dosya aktarımı yok.",
              color = PeykrTheme.colors.textSecondary,
              style = MaterialTheme.typography.bodyMedium
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(sessions, key = { it.sessionId }) { session ->
            TransferSessionCard(
              session = session,
              onAccept = { transferEngine.acceptTransfer(session.sessionId) },
              onReject = { transferEngine.rejectTransfer(session.sessionId) },
              onCancel = { transferEngine.cancelTransfer(session.sessionId) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TransferSessionCard(
  session: TransferSession,
  onAccept: () -> Unit,
  onReject: () -> Unit,
  onCancel: () -> Unit,
) {
  PeykrCard(
    modifier = Modifier.fillMaxWidth(),
    containerColor = PeykrTheme.colors.surface,
    borderColor = PeykrTheme.colors.border,
    shape = RoundedCornerShape(16.dp)
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        val directionTag = if (session.direction == TransferDirection.SEND) "GÖNDERİLİYOR" else "ALINIYOR"
        PeykrBadge(text = directionTag, color = PeykrTheme.colors.primaryAccent, backgroundColor = PeykrTheme.colors.primarySubtle)
        Spacer(Modifier.weight(1f))
        Text(session.status.name, color = PeykrTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall)
      }

      Text(
        session.files.joinToString(", ") { it.fileName },
        color = PeykrTheme.colors.textPrimary,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold
      )

      Text(
        "Cihaz: ${session.remoteDisplayName} (${formatBytes(session.totalBytes)})",
        color = PeykrTheme.colors.textSecondary,
        style = MaterialTheme.typography.bodySmall
      )

      if (session.status == TransferStatus.TRANSFERRING) {
        LinearProgressIndicator(
          progress = { session.progressPercent / 100f },
          modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
          color = PeykrTheme.colors.primaryAccent,
          trackColor = PeykrTheme.colors.primarySubtle,
        )
        Text(
          "${"%.1f".format(session.progressPercent)}%",
          color = PeykrTheme.colors.primaryAccent,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold
        )
      }

      if (session.status == TransferStatus.WAITING_FOR_RECEIVER_APPROVAL && session.direction == TransferDirection.RECEIVE) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            onClick = onAccept,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = PeykrTheme.colors.primaryAccent),
            shape = RoundedCornerShape(10.dp)
          ) {
            Text("Kabul Et", color = Color.White, fontWeight = FontWeight.Bold)
          }
          OutlinedButton(
            onClick = onReject,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp)
          ) {
            Text("Reddet", color = ErrorRed)
          }
        }
      }
    }
  }
}

private fun formatBytes(bytes: Long): String {
  if (bytes < 1024) return "$bytes B"
  val kb = bytes / 1024.0
  if (kb < 1024) return "%.1f KB".format(kb)
  val mb = kb / 1024.0
  return "%.1f MB".format(mb)
}
