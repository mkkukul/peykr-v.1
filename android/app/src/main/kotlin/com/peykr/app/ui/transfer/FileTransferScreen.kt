package com.peykr.app.ui.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peykr.app.domain.transfer.FileTransferEngine
import com.peykr.app.domain.transfer.TransferDirection
import com.peykr.app.domain.transfer.TransferSession
import com.peykr.app.domain.transfer.TransferStatus
import com.peykr.app.ui.theme.Navy950
import com.peykr.app.ui.theme.PrimaryTurquoise
import com.peykr.app.ui.theme.TextPrimary
import com.peykr.app.ui.theme.TextSecondary

@Composable
fun FileTransferScreen(
  transferEngine: FileTransferEngine,
  onBack: () -> Unit,
) {
  val sessionsMap by transferEngine.activeSessions.collectAsStateWithLifecycle()
  val sessions = sessionsMap.values.toList()

  Scaffold(
    containerColor = Navy950,
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(16.dp)
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        TextButton(onClick = onBack) {
          Text("Geri", color = PrimaryTurquoise)
        }
        Spacer(Modifier.weight(1f))
        Text(
          "Yerel Dosya Aktarımı",
          color = TextPrimary,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(48.dp))
      }

      Spacer(Modifier.height(16.dp))

      // Discovery Status Card
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0xFF0F1E36))
          .padding(16.dp)
      ) {
        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(PrimaryTurquoise)
            )
            Spacer(Modifier.width(8.dp))
            Text("Yerel Ağ Keşfi Aktif (mDNS / Wi-Fi)", color = PrimaryTurquoise, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
          }
          Spacer(Modifier.height(6.dp))
          Text(
            "Aynı Wi-Fi ağındaki veya doğrudan eşleşen cihazlara internet kotası harcamadan dosya gönderin.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
          )
        }
      }

      Spacer(Modifier.height(20.dp))
      Text("Aktif Transferler", color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      Spacer(Modifier.height(10.dp))

      if (sessions.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          contentAlignment = Alignment.Center
        ) {
          Text("Şu anda devam eden bir dosya aktarımı yok.", color = TextSecondary)
        }
      } else {
        LazyColumn(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(sessions) { session ->
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
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(Color(0xFF0F1E36))
      .padding(14.dp)
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        val directionTag = if (session.direction == TransferDirection.SEND) "GÖNDERİLİYOR" else "ALINIYOR"
        Text(
          directionTag,
          color = PrimaryTurquoise,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        Text(session.status.name, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
      }

      Spacer(Modifier.height(6.dp))
      Text(
        session.files.joinToString(", ") { it.fileName },
        color = TextPrimary,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold
      )
      Spacer(Modifier.height(4.dp))
      Text(
        "Cihaz: ${session.remoteDisplayName} (${formatBytes(session.totalBytes)})",
        color = TextSecondary,
        style = MaterialTheme.typography.bodySmall
      )

      if (session.status == TransferStatus.TRANSFERRING) {
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
          progress = { session.progressPercent / 100f },
          modifier = Modifier.fillMaxWidth(),
          color = PrimaryTurquoise,
          trackColor = Color(0xFF1E3A5F),
        )
        Spacer(Modifier.height(4.dp))
        Text(
          "${"%.1f".format(session.progressPercent)}%",
          color = PrimaryTurquoise,
          style = MaterialTheme.typography.labelSmall
        )
      }

      if (session.status == TransferStatus.WAITING_FOR_RECEIVER_APPROVAL && session.direction == TransferDirection.RECEIVE) {
        Spacer(Modifier.height(10.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            onClick = onAccept,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTurquoise)
          ) {
            Text("Kabul Et", color = Color.White)
          }
          OutlinedButton(
            onClick = onReject,
            modifier = Modifier.weight(1f),
          ) {
            Text("Reddet", color = Color(0xFFFF8888))
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
