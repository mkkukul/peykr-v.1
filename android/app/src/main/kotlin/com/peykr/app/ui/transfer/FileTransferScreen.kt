package com.peykr.app.ui.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peykr.app.domain.localsend.LocalHttpClient
import com.peykr.app.domain.localsend.LocalNetworkDiscovery
import com.peykr.app.domain.transfer.DiscoveredDevice
import com.peykr.app.domain.transfer.FileTransferEngine
import com.peykr.app.domain.transfer.TransferDirection
import com.peykr.app.domain.transfer.TransferSession
import com.peykr.app.domain.transfer.TransferStatus
import com.peykr.app.ui.theme.AppHorizontalPadding
import com.peykr.app.ui.theme.CardBorderColor
import com.peykr.app.ui.theme.CardShadowColor
import com.peykr.app.ui.theme.ErrorRed
import com.peykr.app.ui.theme.PastelTealBg
import com.peykr.app.ui.theme.PastelTealFg
import com.peykr.app.ui.theme.PeykrBadge
import com.peykr.app.ui.theme.PeykrCard
import com.peykr.app.ui.theme.PeykrScreenContainer
import com.peykr.app.ui.theme.PeykrTheme
import com.peykr.app.ui.theme.PremiumIcon
import com.peykr.app.ui.theme.PremiumIconKind
import com.peykr.app.ui.theme.SuccessGreen
import com.peykr.app.ui.theme.SuccessGreenLight
import kotlinx.coroutines.launch

@Composable
fun FileTransferScreen(
  transferEngine: FileTransferEngine,
  discovery: LocalNetworkDiscovery? = null,
  httpClient: LocalHttpClient? = null,
  onBack: () -> Unit,
) {
  val sessionsMap by transferEngine.activeSessions.collectAsStateWithLifecycle()
  val sessions = sessionsMap.values.toList()
  val discoveredDevices = discovery?.discoveredDevices?.collectAsStateWithLifecycle()?.value ?: emptyList()
  val coroutineScope = rememberCoroutineScope()

  Scaffold(
    containerColor = Color.Transparent,
    contentWindowInsets = WindowInsets.safeDrawing,
  ) { padding ->
    PeykrScreenContainer {
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
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
          ) {
            PremiumIcon(
              kind = PremiumIconKind.Arrow,
              description = "Geri",
              tint = Color(0xFF1E60D5),
              modifier = Modifier.size(20.dp)
            )
          }
          Spacer(Modifier.width(8.dp))
          Text(
            "Yerel Dosya Aktarımı",
            color = Color(0xFF0F172A),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
          )
        }

        // Discovery Status Card
        PeykrCard(
          modifier = Modifier.fillMaxWidth(),
          containerColor = Color.White,
          borderColor = CardBorderColor,
          elevation = 2.dp,
          shape = RoundedCornerShape(18.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(PastelTealBg),
              contentAlignment = Alignment.Center
            ) {
              PremiumIcon(PremiumIconKind.File, "Dosya", tint = PastelTealFg, modifier = Modifier.size(22.dp))
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                  "Yerel Ağ Keşfi Aktif",
                  color = Color(0xFF0F172A),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp
                )
                PeykrBadge(
                  text = "${discoveredDevices.size} CİHAZ",
                  color = SuccessGreen,
                  backgroundColor = SuccessGreenLight
                )
              }
              Spacer(Modifier.height(2.dp))
              Text(
                "Aynı Wi-Fi ağındaki Peykr cihazlarına doğrudan dosya aktarın.",
                color = Color(0xFF64748B),
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp
              )
            }

            Box(
              modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(SuccessGreen)
            )
          }
        }

        // Discovered Devices Section (if any found)
        if (discoveredDevices.isNotEmpty()) {
          Text(
            "Ağdaki Cihazlar",
            color = Color(0xFF0F172A),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
          )

          LazyColumn(
            modifier = Modifier.height(130.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(discoveredDevices, key = { it.deviceId }) { device ->
              DiscoveredDeviceCard(
                device = device,
                onSend = {
                  // Trigger sample file send over LAN
                }
              )
            }
          }
        }

        Text(
          "Aktif Transferler",
          color = Color(0xFF0F172A),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp
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
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(72.dp)
                  .clip(CircleShape)
                  .background(Color(0xFFEFF5FF)),
                contentAlignment = Alignment.Center
              ) {
                PremiumIcon(
                  kind = PremiumIconKind.File,
                  description = "Transfer",
                  tint = Color(0xFF1E60D5),
                  modifier = Modifier.size(32.dp)
                )
              }
              Text(
                "Şu anda devam eden bir dosya aktarımı yok.",
                color = Color(0xFF64748B),
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.sp
              )
              Text(
                "Yakındaki bir Peykr cihazından dosya gönderildiğinde burada onay penceresi görünecektir.",
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
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
}

@Composable
private fun DiscoveredDeviceCard(
  device: DiscoveredDevice,
  onSend: () -> Unit
) {
  PeykrCard(
    modifier = Modifier.fillMaxWidth(),
    containerColor = Color.White,
    borderColor = CardBorderColor,
    shape = RoundedCornerShape(14.dp),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(38.dp)
          .clip(CircleShape)
          .background(Color(0xFFEBF3FF)),
        contentAlignment = Alignment.Center
      ) {
        PremiumIcon(PremiumIconKind.Phone, "Cihaz", tint = Color(0xFF1E60D5), modifier = Modifier.size(18.dp))
      }
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text(
          text = device.displayName,
          color = Color(0xFF0F172A),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "${device.deviceModel} · ${device.ipAddress}:${device.port}",
          color = Color(0xFF94A3B8),
          style = MaterialTheme.typography.bodySmall,
          fontSize = 11.5.sp
        )
      }
      Button(
        onClick = onSend,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E60D5)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)
      ) {
        Text("Gönder", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
    containerColor = Color.White,
    borderColor = CardBorderColor,
    elevation = 2.dp,
    shape = RoundedCornerShape(16.dp),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        val isSend = session.direction == TransferDirection.SEND
        val directionTag = if (isSend) "GÖNDERİLİYOR" else "ALINIYOR"
        PeykrBadge(
          text = directionTag,
          color = if (isSend) Color(0xFF1E60D5) else SuccessGreen,
          backgroundColor = if (isSend) Color(0xFFEBF3FF) else SuccessGreenLight
        )
        Spacer(Modifier.weight(1f))
        Text(
          session.status.name,
          color = Color(0xFF64748B),
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold
        )
      }

      Text(
        session.files.joinToString(", ") { it.fileName },
        color = Color(0xFF0F172A),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold
      )

      Text(
        "Cihaz: ${session.remoteDisplayName} (${formatBytes(session.totalBytes)})",
        color = Color(0xFF64748B),
        style = MaterialTheme.typography.bodySmall
      )

      if (session.status == TransferStatus.TRANSFERRING) {
        LinearProgressIndicator(
          progress = { session.progressPercent / 100f },
          modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
          color = Color(0xFF1E60D5),
          trackColor = Color(0xFFEFF6FF),
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            "${"%.1f".format(session.progressPercent)}%",
            color = Color(0xFF1E60D5),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
          )
          Text(
            "${formatBytes(session.bytesTransferred)} / ${formatBytes(session.totalBytes)}",
            color = Color(0xFF94A3B8),
            style = MaterialTheme.typography.labelSmall
          )
        }
      }

      if (session.status == TransferStatus.WAITING_FOR_RECEIVER_APPROVAL && session.direction == TransferDirection.RECEIVE) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            onClick = onAccept,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E60D5)),
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
