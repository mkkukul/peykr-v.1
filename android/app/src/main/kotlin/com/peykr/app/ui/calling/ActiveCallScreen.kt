package com.peykr.app.ui.calling

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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peykr.app.domain.calling.CallState
import com.peykr.app.domain.calling.CallingEngine
import com.peykr.app.ui.theme.ErrorRed
import com.peykr.app.ui.theme.PeykrTheme
import com.peykr.app.ui.theme.SuccessGreen
import kotlinx.coroutines.launch

@Composable
fun ActiveCallScreen(
  callingEngine: CallingEngine,
  onBack: () -> Unit,
) {
  val session by callingEngine.activeSession.collectAsStateWithLifecycle()
  val coroutineScope = rememberCoroutineScope()

  Scaffold(
    containerColor = PeykrTheme.colors.background,
    contentWindowInsets = WindowInsets.safeDrawing,
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
          Text("‹ Geri", color = PeykrTheme.colors.primaryAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
      }

      // Middle: Avatar & Info
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
          modifier = Modifier
            .size(110.dp)
            .clip(CircleShape)
            .background(PeykrTheme.colors.primarySubtle)
            .border(2.dp, PeykrTheme.colors.primaryAccent.copy(alpha = 0.3f), CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = session?.contactDisplayName?.take(1)?.uppercase() ?: "P",
            color = PeykrTheme.colors.primaryAccent,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold
          )
        }

        Spacer(Modifier.height(18.dp))

        Text(
          text = session?.contactDisplayName ?: "Arama",
          color = PeykrTheme.colors.textPrimary,
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
        Text(
          text = stateText,
          color = if (session?.state == CallState.CONNECTED) SuccessGreen else PeykrTheme.colors.primaryAccent,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold
        )
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
              colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
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
              colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
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
                containerColor = if (session?.isMuted == true) Color(0xFFFEF3C7) else PeykrTheme.colors.surfaceElevated
              ),
              shape = CircleShape,
              modifier = Modifier.size(54.dp)
            ) {
              Text("🎤", color = if (session?.isMuted == true) Color(0xFFD97706) else PeykrTheme.colors.textPrimary)
            }

            Button(
              onClick = { callingEngine.toggleSpeaker() },
              colors = ButtonDefaults.buttonColors(
                containerColor = if (session?.isSpeakerOn == true) PeykrTheme.colors.primarySubtle else PeykrTheme.colors.surfaceElevated
              ),
              shape = CircleShape,
              modifier = Modifier.size(54.dp)
            ) {
              Text("🔊", color = if (session?.isSpeakerOn == true) PeykrTheme.colors.primaryAccent else PeykrTheme.colors.textPrimary)
            }

            Button(
              onClick = { callingEngine.toggleVideo() },
              colors = ButtonDefaults.buttonColors(
                containerColor = if (session?.isVideoEnabled == true) PeykrTheme.colors.primarySubtle else PeykrTheme.colors.surfaceElevated
              ),
              shape = CircleShape,
              modifier = Modifier.size(54.dp)
            ) {
              Text("📹", color = if (session?.isVideoEnabled == true) PeykrTheme.colors.primaryAccent else PeykrTheme.colors.textPrimary)
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
            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
            shape = CircleShape,
            modifier = Modifier.size(64.dp)
          ) {
            Text("✕", color = Color.White, style = MaterialTheme.typography.titleLarge)
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
