package com.peykr.app.ui.calling

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peykr.app.domain.calling.CallState
import com.peykr.app.domain.calling.CallingEngine
import com.peykr.app.ui.theme.Navy950
import com.peykr.app.ui.theme.PrimaryTurquoise
import com.peykr.app.ui.theme.TextPrimary
import com.peykr.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun ActiveCallScreen(
  callingEngine: CallingEngine,
  onBack: () -> Unit,
) {
  val session by callingEngine.activeSession.collectAsStateWithLifecycle()
  val coroutineScope = rememberCoroutineScope()

  Scaffold(
    containerColor = Navy950,
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      // Top bar
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
      ) {
        TextButton(onClick = onBack) {
          Text("Geri", color = PrimaryTurquoise)
        }
      }

      // Middle: Avatar & Info
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
          modifier = Modifier
            .size(110.dp)
            .clip(CircleShape)
            .background(Color(0xFF1E3A5F)),
          contentAlignment = Alignment.Center
        ) {
          Text(
            session?.contactDisplayName?.take(1)?.uppercase() ?: "P",
            color = PrimaryTurquoise,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold
          )
        }
        Spacer(Modifier.height(16.dp))
        Text(
          session?.contactDisplayName ?: "Arama",
          color = TextPrimary,
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        val stateText = when (session?.state) {
          CallState.OUTGOING_DIALING -> "Aranıyor…"
          CallState.INCOMING_RINGING -> "Gelen Çağrı…"
          CallState.CONNECTING -> "Bağlanıyor…"
          CallState.CONNECTED -> formatDuration(session?.durationSeconds ?: 0L)
          CallState.RECONNECTING -> "Yeniden bağlanıyor…"
          CallState.ENDED, CallState.REJECTED -> "Görüşme sonlandı"
          else -> "Hazır"
        }
        Text(stateText, color = PrimaryTurquoise, style = MaterialTheme.typography.titleMedium)
      }

      // Bottom: Action buttons
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        if (session?.state == CallState.INCOMING_RINGING) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
          ) {
            Button(
              onClick = {
                session?.let { s ->
                  coroutineScope.launch { callingEngine.acceptIncomingCall(s.callId) }
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
              shape = CircleShape,
              modifier = Modifier.size(64.dp)
            ) {
              Text("✓", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }

            Button(
              onClick = {
                session?.let { s ->
                  coroutineScope.launch { callingEngine.rejectIncomingCall(s.callId) }
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
              shape = CircleShape,
              modifier = Modifier.size(64.dp)
            ) {
              Text("✕", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
          }
        } else {
          // In-call toggles
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
          ) {
            Button(
              onClick = { callingEngine.toggleMute() },
              colors = ButtonDefaults.buttonColors(
                containerColor = if (session?.isMuted == true) Color(0xFFFFB74D) else Color(0xFF0F1E36)
              ),
              shape = CircleShape,
              modifier = Modifier.size(54.dp)
            ) {
              Text("🎤", color = Color.White)
            }

            Button(
              onClick = { callingEngine.toggleSpeaker() },
              colors = ButtonDefaults.buttonColors(
                containerColor = if (session?.isSpeakerOn == true) PrimaryTurquoise else Color(0xFF0F1E36)
              ),
              shape = CircleShape,
              modifier = Modifier.size(54.dp)
            ) {
              Text("🔊", color = Color.White)
            }

            Button(
              onClick = { callingEngine.toggleVideo() },
              colors = ButtonDefaults.buttonColors(
                containerColor = if (session?.isVideoEnabled == true) PrimaryTurquoise else Color(0xFF0F1E36)
              ),
              shape = CircleShape,
              modifier = Modifier.size(54.dp)
            ) {
              Text("📹", color = Color.White)
            }
          }

          Spacer(Modifier.height(24.dp))

          // End call button
          Button(
            onClick = {
              session?.let { s ->
                coroutineScope.launch { callingEngine.endCall(s.callId) }
              } ?: onBack()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
            shape = CircleShape,
            modifier = Modifier.size(64.dp)
          ) {
            Text("☎", color = Color.White, style = MaterialTheme.typography.titleLarge)
          }
        }
      }
    }
  }
}

private fun formatDuration(seconds: Long): String {
  val mins = seconds / 60
  val secs = seconds % 60
  return "%02d:%02d".format(mins, secs)
}
