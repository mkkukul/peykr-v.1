package com.peykr.app.ui.messaging

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peykr.app.domain.contacts.TrustedContact
import com.peykr.app.domain.contacts.TrustedContactRepository
import com.peykr.app.domain.messaging.Conversation
import com.peykr.app.domain.messaging.Message
import com.peykr.app.domain.messaging.MessageDirection
import com.peykr.app.domain.messaging.MessagingEngine
import com.peykr.app.domain.messaging.MessagingRepository
import com.peykr.app.ui.theme.Navy950
import com.peykr.app.ui.theme.PrimaryTurquoise
import com.peykr.app.ui.theme.TextPrimary
import com.peykr.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun MessagesListScreen(
  messagingRepository: MessagingRepository,
  contactRepository: TrustedContactRepository,
  messagingEngine: MessagingEngine,
  onOpenConversation: (String, String) -> Unit,
  onBack: () -> Unit,
) {
  val conversations by messagingRepository.getConversations().collectAsStateWithLifecycle(emptyList())
  val contacts by contactRepository.contacts.collectAsStateWithLifecycle(emptyList())
  val coroutineScope = rememberCoroutineScope()
  var showNewChatDialog by remember { mutableStateOf(false) }

  Scaffold(
    containerColor = Navy950,
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        TextButton(onClick = onBack) {
          Text("Geri", color = PrimaryTurquoise)
        }
        Spacer(Modifier.weight(1f))
        Text(
          "Özel Mesajlar",
          color = TextPrimary,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        Button(
          onClick = { showNewChatDialog = true },
          colors = ButtonDefaults.buttonColors(containerColor = PrimaryTurquoise),
          shape = CircleShape,
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
          Text("+ Yeni", color = Color.White)
        }
      }

      Spacer(Modifier.height(16.dp))

      if (conversations.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Henüz sohbet yok", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
              "Eşleştiğiniz güvenilir kişilerle uçtan uca şifreli mesajlaşma başlatın.",
              color = TextSecondary,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(horizontal = 32.dp)
            )
          }
        }
      } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items(conversations) { conv ->
            ConversationRow(
              conversation = conv,
              onClick = { onOpenConversation(conv.id, conv.title) }
            )
          }
        }
      }
    }
  }

  if (showNewChatDialog) {
    androidx.compose.material3.AlertDialog(
      onDismissRequest = { showNewChatDialog = false },
      title = { Text("Yeni Sohbet Başlat", color = TextPrimary) },
      text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items(contacts) { c ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  coroutineScope.launch {
                    val conv = messagingEngine.createOneToOneConversation(c)
                    showNewChatDialog = false
                    onOpenConversation(conv.id, conv.title)
                  }
                }
                .padding(vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(c.displayName, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
            }
          }
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = { showNewChatDialog = false }) {
          Text("Kapat", color = PrimaryTurquoise)
        }
      },
      containerColor = Color(0xFF0F1E36),
    )
  }
}

@Composable
private fun ConversationRow(conversation: Conversation, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(Color(0xFF0F1E36))
      .clickable(onClick = onClick)
      .padding(14.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(44.dp)
        .clip(CircleShape)
        .background(Color(0xFF1E3A5F)),
      contentAlignment = Alignment.Center
    ) {
      Text(
        conversation.title.take(1).uppercase(),
        color = PrimaryTurquoise,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
      )
    }
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(conversation.title, color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(2.dp))
      Text(
        conversation.lastMessage ?: "Şifreli mesajlaşma hazır",
        color = TextSecondary,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1
      )
    }
  }
}

@Composable
fun ConversationDetailScreen(
  conversationId: String,
  title: String,
  messagingRepository: MessagingRepository,
  messagingEngine: MessagingEngine,
  onStartCall: (String, String) -> Unit,
  onBack: () -> Unit,
) {
  val messages by messagingRepository.getMessages(conversationId).collectAsStateWithLifecycle(emptyList())
  val coroutineScope = rememberCoroutineScope()
  var textInput by remember { mutableStateOf("") }

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
        Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Button(
          onClick = { onStartCall(conversationId, title) },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A5F)),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text("Ara", color = PrimaryTurquoise)
        }
      }

      Spacer(Modifier.height(12.dp))

      // Messages list
      LazyColumn(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(messages) { msg ->
          MessageBubble(msg)
        }
      }

      Spacer(Modifier.height(8.dp))

      // Message input
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        OutlinedTextField(
          value = textInput,
          onValueChange = { textInput = it },
          placeholder = { Text("Şifreli mesaj yazın…", color = TextSecondary) },
          modifier = Modifier.weight(1f),
          maxLines = 4,
          shape = RoundedCornerShape(24.dp)
        )
        Spacer(Modifier.width(8.dp))
        Button(
          onClick = {
            if (textInput.isNotBlank()) {
              val content = textInput
              textInput = ""
              coroutineScope.launch {
                messagingEngine.sendTextMessage(
                  conversationId = conversationId,
                  recipientId = conversationId.removePrefix("conv_"),
                  text = content,
                )
              }
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = PrimaryTurquoise),
          shape = CircleShape,
          modifier = Modifier.size(48.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) {
          Text("➤", color = Color.White)
        }
      }
    }
  }
}

@Composable
private fun MessageBubble(message: Message) {
  val isOutgoing = message.direction == MessageDirection.OUTGOING
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
  ) {
    Box(
      modifier = Modifier
        .clip(RoundedCornerShape(16.dp))
        .background(if (isOutgoing) Color(0xFF1E3A5F) else Color(0xFF0F1E36))
        .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
      Column {
        Text(
          message.textContent ?: "[Dosya eki]",
          color = TextPrimary,
          style = MaterialTheme.typography.bodyMedium
        )
      }
    }
  }
}
