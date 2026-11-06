package com.peykr.app.ui.messaging

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.peykr.app.MessagesList
import com.peykr.app.domain.contacts.TrustedContact
import com.peykr.app.domain.contacts.TrustedContactRepository
import com.peykr.app.domain.messaging.Conversation
import com.peykr.app.domain.messaging.Message
import com.peykr.app.domain.messaging.MessageDirection
import com.peykr.app.domain.messaging.MessageStatus
import com.peykr.app.domain.messaging.MessageTransportType
import com.peykr.app.domain.messaging.MessagingEngine
import com.peykr.app.domain.messaging.MessagingRepository
import com.peykr.app.domain.transport.TransportManager
import com.peykr.app.ui.theme.AppHorizontalPadding
import com.peykr.app.ui.theme.CardBorderColor
import com.peykr.app.ui.theme.CardShadowColor
import com.peykr.app.ui.theme.PastelBlueBg
import com.peykr.app.ui.theme.PastelBlueFg
import com.peykr.app.ui.theme.PeykrBadge
import com.peykr.app.ui.theme.PeykrBottomNavigationBar
import com.peykr.app.ui.theme.PeykrCard
import com.peykr.app.ui.theme.PeykrScreenContainer
import com.peykr.app.ui.theme.PeykrSearchField
import com.peykr.app.ui.theme.PeykrTheme
import com.peykr.app.ui.theme.PremiumIcon
import com.peykr.app.ui.theme.PremiumIconKind
import com.peykr.app.ui.theme.SuccessGreen
import com.peykr.app.ui.theme.SuccessGreenLight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun MessagesListScreen(
  messagingRepository: MessagingRepository,
  contactRepository: TrustedContactRepository,
  messagingEngine: MessagingEngine,
  onOpenConversation: (String, String) -> Unit,
  onBack: () -> Unit,
  onNavigate: (NavKey) -> Unit = {},
) {
  val conversations by messagingRepository.getConversations().collectAsStateWithLifecycle(emptyList())
  val contacts by contactRepository.contacts.collectAsStateWithLifecycle(emptyList())
  val coroutineScope = rememberCoroutineScope()
  var showNewChatDialog by remember { mutableStateOf(false) }
  var directRecipientName by remember { mutableStateOf("") }
  var searchQuery by remember { mutableStateOf("") }
  var selectedFilterIndex by remember { mutableStateOf(0) }
  val filters = listOf("Tümü", "Güvenilir Kişiler", "Doğrudan Cihazlar")

  Scaffold(
    containerColor = Color.Transparent,
    contentWindowInsets = WindowInsets.safeDrawing,
    bottomBar = { PeykrBottomNavigationBar(current = MessagesList, onNavigate = onNavigate) }
  ) { padding ->
    PeykrScreenContainer {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .padding(horizontal = AppHorizontalPadding, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // Top Header: Title and New Chat button
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "Mesajlar",
              color = PeykrTheme.colors.textPrimary,
              style = MaterialTheme.typography.headlineMedium,
              fontWeight = FontWeight.Bold,
              fontSize = 28.sp,
              letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(3.dp))
            Text(
              text = "Uçtan uca şifreli doğrudan mesajlaşma.",
              color = PeykrTheme.colors.textSecondary,
              style = MaterialTheme.typography.bodyMedium,
              fontSize = 14.sp
            )
          }

          // Modern primary pill button for + Yeni
          Box(
            modifier = Modifier
              .shadow(4.dp, RoundedCornerShape(20.dp), spotColor = Color(0x331E60D5))
              .clip(RoundedCornerShape(20.dp))
              .background(
                Brush.horizontalGradient(
                  listOf(Color(0xFF1E60D5), Color(0xFF1751B8))
                )
              )
              .clickable { showNewChatDialog = true }
              .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              PremiumIcon(
                kind = PremiumIconKind.Plus,
                description = "Yeni",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = "Yeni",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
              )
            }
          }
        }

        // Search bar
        PeykrSearchField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = "Sohbetlerde veya mesajlarda ara..."
        )

        // Filter chips row
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          filters.forEachIndexed { index, filter ->
            val isSelected = selectedFilterIndex == index
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) PeykrTheme.colors.primarySubtle else PeykrTheme.colors.surface)
                .border(
                  width = 1.dp,
                  color = if (isSelected) PeykrTheme.colors.primaryAccent else PeykrTheme.colors.border,
                  shape = RoundedCornerShape(12.dp)
                )
                .clickable { selectedFilterIndex = index }
                .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
              Text(
                text = filter,
                color = if (isSelected) PeykrTheme.colors.primaryAccent else PeykrTheme.colors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp
              )
            }
          }
        }

        val filteredConversations = if (searchQuery.isBlank()) {
          conversations
        } else {
          conversations.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }

        if (filteredConversations.isEmpty()) {
          // Polished friendly empty state
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            contentAlignment = Alignment.Center
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(14.dp),
              modifier = Modifier.padding(24.dp)
            ) {
              // Concentric circles with message icon
              Box(
                modifier = Modifier
                  .size(90.dp)
                  .clip(CircleShape)
                  .background(Color(0xFFEFF5FF)),
                contentAlignment = Alignment.Center
              ) {
                Box(
                  modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFDCEBFF)),
                  contentAlignment = Alignment.Center
                ) {
                  PremiumIcon(
                    kind = PremiumIconKind.Message,
                    description = "Mesajlar",
                    tint = Color(0xFF1E60D5),
                    modifier = Modifier.size(30.dp)
                  )
                }
              }

              Text(
                text = "Henüz sohbet yok",
                color = PeykrTheme.colors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
              )

              Text(
                text = "Eşleştiğiniz güvenilir kişilerle uçtan uca şifreli doğrudan mesajlaşma başlatın.",
                color = PeykrTheme.colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
              )

              Spacer(Modifier.height(4.dp))

              Button(
                onClick = { showNewChatDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E60D5)),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  PremiumIcon(
                    kind = PremiumIconKind.Plus,
                    description = "",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                  )
                  Text(
                    text = "Yeni Sohbet Başlat",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                  )
                }
              }
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            items(filteredConversations, key = { it.id }) { conv ->
              ConversationRow(conversation = conv, onClick = { onOpenConversation(conv.id, conv.title) })
            }
          }
        }
      }
    }
  }

  // New Chat Dialog
  if (showNewChatDialog) {
    AlertDialog(
      onDismissRequest = { showNewChatDialog = false },
      title = {
        Text(
          text = "Yeni Sohbet Başlat",
          color = PeykrTheme.colors.textPrimary,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
          if (contacts.isNotEmpty()) {
            Text(
              text = "Güvenilir Kişiler",
              color = PeykrTheme.colors.textPrimary,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold
            )
            LazyColumn(modifier = Modifier.heightIn(max = 160.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              items(contacts) { c ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(PeykrTheme.colors.surfaceElevated)
                    .border(1.dp, PeykrTheme.colors.border, RoundedCornerShape(14.dp))
                    .clickable {
                      coroutineScope.launch {
                        val conv = messagingEngine.createOneToOneConversation(c)
                        showNewChatDialog = false
                        onOpenConversation(conv.id, conv.title)
                      }
                    }
                    .padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Box(
                    modifier = Modifier
                      .size(40.dp)
                      .clip(CircleShape)
                      .background(
                        Brush.linearGradient(
                          listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
                        )
                      ),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      text = c.displayName.take(1).uppercase(),
                      color = Color.White,
                      fontWeight = FontWeight.Bold,
                      fontSize = 15.sp
                    )
                  }
                  Spacer(Modifier.width(12.dp))
                  Column(Modifier.weight(1f)) {
                    Text(
                      text = c.displayName,
                      color = PeykrTheme.colors.textPrimary,
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.SemiBold
                    )
                    Text(
                      text = "🔒 Uçtan uca şifreli",
                      color = SuccessGreen,
                      style = MaterialTheme.typography.labelSmall,
                      fontSize = 11.sp
                    )
                  }
                }
              }
            }
            HorizontalDivider(color = PeykrTheme.colors.border)
          }

          Text(
            text = "Doğrudan Cihaz veya Kişi",
            color = PeykrTheme.colors.textPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
          )
          OutlinedTextField(
            value = directRecipientName,
            onValueChange = { directRecipientName = it },
            placeholder = { Text("Kişi adı veya cihaz kimliği...", fontSize = 13.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = PeykrTheme.colors.primaryAccent,
              unfocusedBorderColor = PeykrTheme.colors.border
            )
          )
          Button(
            onClick = {
              if (directRecipientName.isNotBlank()) {
                val name = directRecipientName.trim()
                directRecipientName = ""
                showNewChatDialog = false
                coroutineScope.launch {
                  val convId = "conv_${java.util.UUID.randomUUID().toString().take(8)}"
                  val newConv = Conversation(
                    id = convId,
                    title = name,
                    isGroup = false,
                    lastMessage = "🔒 Uçtan uca şifreli güvenli oturum başlatıldı",
                    updatedAt = System.currentTimeMillis()
                  )
                  messagingRepository.saveConversation(newConv)
                  onOpenConversation(newConv.id, newConv.title)
                }
              }
            },
            enabled = directRecipientName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = PeykrTheme.colors.primaryAccent),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
          ) {
            Text("Sohbet Başlat", color = Color.White, fontWeight = FontWeight.Bold)
          }
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = { showNewChatDialog = false }) {
          Text("Kapat", color = PeykrTheme.colors.primaryAccent)
        }
      },
      containerColor = PeykrTheme.colors.surface,
      shape = RoundedCornerShape(20.dp)
    )
  }
}

@Composable
private fun ConversationRow(conversation: Conversation, onClick: () -> Unit) {
  val formattedTime = remember(conversation.updatedAt) {
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(conversation.updatedAt))
  }

  PeykrCard(
    modifier = Modifier.fillMaxWidth(),
    containerColor = PeykrTheme.colors.surface,
    borderColor = PeykrTheme.colors.border,
    shape = RoundedCornerShape(18.dp),
    elevation = 2.dp,
    contentPadding = PaddingValues(14.dp),
    onClick = onClick
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // 50dp Avatar with status dot
      Box {
        Box(
          modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(
              Brush.linearGradient(
                listOf(Color(0xFF3B82F6), Color(0xFF1E40AF))
              )
            ),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = conversation.title.take(1).uppercase(),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
          )
        }
        // Small active shield/lock badge
        Box(
          modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(SuccessGreen)
            .border(2.dp, PeykrTheme.colors.surface, CircleShape)
            .align(Alignment.BottomEnd)
        )
      }

      Spacer(Modifier.width(14.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = conversation.title,
            color = PeykrTheme.colors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
          )

          Text(
            text = formattedTime,
            color = Color(0xFF94A3B8),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 12.sp
          )
        }

        Spacer(Modifier.height(4.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            PremiumIcon(
              kind = PremiumIconKind.CheckAll,
              description = "İletildi",
              tint = Color(0xFF1E60D5),
              modifier = Modifier.size(15.dp)
            )
            Text(
              text = conversation.lastMessage ?: "Şifreli sohbet hazır",
              color = Color(0xFF64748B),
              style = MaterialTheme.typography.bodyMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              fontSize = 13.5.sp
            )
          }

          if (conversation.unreadCount > 0) {
            Spacer(Modifier.width(8.dp))
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E60D5))
                .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
              Text(
                text = conversation.unreadCount.toString(),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }
    }
  }
}

// -------------------------------------------------------------
// Chat (Sohbet) Screen
// -------------------------------------------------------------
@Composable
fun ConversationDetailScreen(
  conversationId: String,
  title: String,
  messagingRepository: MessagingRepository,
  messagingEngine: MessagingEngine,
  transportManager: TransportManager? = null,
  onStartCall: (String, String) -> Unit,
  onBack: () -> Unit,
) {
  val messages by messagingRepository.getMessages(conversationId).collectAsStateWithLifecycle(emptyList())
  val coroutineScope = rememberCoroutineScope()
  val listState = rememberLazyListState()
  var textInput by remember { mutableStateOf("") }

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  Scaffold(
    containerColor = PeykrTheme.colors.background,
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      // Modern elevated chat header
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .shadow(elevation = 3.dp, spotColor = CardShadowColor)
          .background(PeykrTheme.colors.surface)
          .border(0.5.dp, PeykrTheme.colors.border)
          .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Back Button
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
            tint = PeykrTheme.colors.primaryAccent,
            modifier = Modifier.size(20.dp)
          )
        }

        Spacer(Modifier.width(6.dp))

        // Contact Avatar
        Box {
          Box(
            modifier = Modifier
              .size(42.dp)
              .clip(CircleShape)
              .background(
                Brush.linearGradient(
                  listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
                )
              ),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = title.take(1).uppercase(),
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp
            )
          }
          Box(
            modifier = Modifier
              .size(11.dp)
              .clip(CircleShape)
              .background(SuccessGreen)
              .border(2.dp, PeykrTheme.colors.surface, CircleShape)
              .align(Alignment.BottomEnd)
          )
        }

        Spacer(Modifier.width(10.dp))

        // Title and Status
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = title,
            color = PeykrTheme.colors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PremiumIcon(
              kind = PremiumIconKind.Shield,
              description = "",
              tint = SuccessGreen,
              modifier = Modifier.size(12.dp)
            )
            Text(
              text = "Uçtan uca şifreli",
              color = SuccessGreen,
              style = MaterialTheme.typography.labelSmall,
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Medium
            )
          }
        }

        // Voice Call Action
        Box(
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(PeykrTheme.colors.surfaceElevated)
            .clickable { onStartCall(conversationId.removePrefix("conv_"), title) },
          contentAlignment = Alignment.Center
        ) {
          PremiumIcon(
            kind = PremiumIconKind.Phone,
            description = "Sesli Ara",
            tint = PeykrTheme.colors.primaryAccent,
            modifier = Modifier.size(18.dp)
          )
        }

        Spacer(Modifier.width(8.dp))

        // Video Call Action
        Box(
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(PeykrTheme.colors.surfaceElevated)
            .clickable { onStartCall(conversationId.removePrefix("conv_"), title) },
          contentAlignment = Alignment.Center
        ) {
          PremiumIcon(
            kind = PremiumIconKind.Video,
            description = "Görüntülü Ara",
            tint = PeykrTheme.colors.primaryAccent,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .imePadding()
    ) {
      // Security Notice Pill
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 10.dp, bottom = 4.dp, start = 20.dp, end = 20.dp),
        contentAlignment = Alignment.Center
      ) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (PeykrTheme.colors.isDark) Color(0xFF1E293B) else Color(0xFFEFF6FF))
            .border(1.dp, if (PeykrTheme.colors.isDark) Color(0xFF334155) else Color(0xFFDBEAFE), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
          Text(
            text = "🔒 Mesajlar ve aramalar cihazlar arasında uçtan uca şifrelidir.",
            color = if (PeykrTheme.colors.isDark) Color(0xFF93C5FD) else Color(0xFF1E40AF),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
          )
        }
      }

      // Message stream
      if (messages.isEmpty()) {
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(PeykrTheme.colors.primarySubtle),
              contentAlignment = Alignment.Center
            ) {
              PremiumIcon(
                kind = PremiumIconKind.Lock,
                description = "Güvenli",
                tint = PeykrTheme.colors.primaryAccent,
                modifier = Modifier.size(28.dp)
              )
            }
            Text(
              text = "Güvenli Sohbet Başlatıldı",
              color = PeykrTheme.colors.textPrimary,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              fontSize = 17.sp
            )
            Text(
              text = "Mesajlarınız Double Ratchet protokolü ve AES-256 ile cihazlar arasında doğrudan iletilir.",
              color = PeykrTheme.colors.textSecondary,
              style = MaterialTheme.typography.bodySmall,
              textAlign = TextAlign.Center,
              fontSize = 13.sp,
              modifier = Modifier.padding(horizontal = 20.dp)
            )
          }
        }
      } else {
        LazyColumn(
          state = listState,
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(messages, key = { it.id }) { msg ->
            MessageBubble(msg)
          }
        }
      }

      // Modern Message Composer
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .shadow(elevation = 6.dp, spotColor = CardShadowColor)
          .background(PeykrTheme.colors.surface)
          .border(0.5.dp, PeykrTheme.colors.border)
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Attachment Button
        Box(
          modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(PeykrTheme.colors.surfaceElevated)
            .clickable {
              // Attachment action
            },
          contentAlignment = Alignment.Center
        ) {
          PremiumIcon(
            kind = PremiumIconKind.Attachment,
            description = "Dosya Ekle",
            tint = PeykrTheme.colors.textSecondary,
            modifier = Modifier.size(18.dp)
          )
        }

        Spacer(Modifier.width(8.dp))

        // Message input pill
        OutlinedTextField(
          value = textInput,
          onValueChange = { textInput = it },
          placeholder = { Text("Bir mesaj yazın…", color = PeykrTheme.colors.textMuted, fontSize = 14.sp) },
          modifier = Modifier.weight(1f),
          maxLines = 4,
          shape = RoundedCornerShape(24.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = PeykrTheme.colors.surfaceElevated,
            unfocusedContainerColor = PeykrTheme.colors.surfaceElevated,
            focusedBorderColor = PeykrTheme.colors.primaryAccent,
            unfocusedBorderColor = PeykrTheme.colors.border,
            cursorColor = PeykrTheme.colors.primaryAccent,
            focusedTextColor = PeykrTheme.colors.textPrimary,
            unfocusedTextColor = PeykrTheme.colors.textPrimary
          )
        )

        Spacer(Modifier.width(8.dp))

        // Send Button
        Box(
          modifier = Modifier
            .size(46.dp)
            .shadow(if (textInput.isNotBlank()) 3.dp else 0.dp, CircleShape, spotColor = Color(0x331E60D5))
            .clip(CircleShape)
            .background(
              if (textInput.isNotBlank()) {
                Brush.horizontalGradient(listOf(Color(0xFF1E60D5), Color(0xFF1650B8)))
              } else {
                Brush.horizontalGradient(listOf(PeykrTheme.colors.surfaceElevated, PeykrTheme.colors.surfaceElevated))
              }
            )
            .clickable(enabled = textInput.isNotBlank()) {
              val content = textInput
              textInput = ""
              coroutineScope.launch {
                if (transportManager != null) {
                  transportManager.sendReliableMessage(
                    conversationId = conversationId,
                    recipientDeviceId = conversationId.removePrefix("conv_"),
                    text = content,
                  )
                } else {
                  messagingEngine.sendTextMessage(
                    conversationId = conversationId,
                    recipientId = conversationId.removePrefix("conv_"),
                    text = content,
                  )
                }
              }
            },
          contentAlignment = Alignment.Center
        ) {
          PremiumIcon(
            kind = PremiumIconKind.Send,
            description = "Gönder",
            tint = if (textInput.isNotBlank()) Color.White else PeykrTheme.colors.textMuted,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun MessageBubble(message: Message) {
  val isOutgoing = message.direction == MessageDirection.OUTGOING
  val timeText = remember(message.timestamp) {
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
  }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
  ) {
    Box(
      modifier = Modifier
        .shadow(
          elevation = 2.dp,
          shape = if (isOutgoing) RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
          spotColor = if (isOutgoing) Color(0x281E60D5) else CardShadowColor
        )
        .clip(
          if (isOutgoing) RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
          else RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
        )
        .background(
          if (isOutgoing) {
            Brush.horizontalGradient(listOf(Color(0xFF1E60D5), Color(0xFF1751B8)))
          } else {
            Brush.horizontalGradient(listOf(PeykrTheme.colors.surface, PeykrTheme.colors.surface))
          }
        )
        .then(
          if (!isOutgoing) Modifier.border(1.dp, PeykrTheme.colors.border, RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp))
          else Modifier
        )
        .padding(horizontal = 15.dp, vertical = 10.dp)
    ) {
      Column(
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
      ) {
        Text(
          text = message.textContent ?: "[Dosya eki]",
          color = if (isOutgoing) Color.White else PeykrTheme.colors.textPrimary,
          style = MaterialTheme.typography.bodyMedium,
          fontSize = 15.sp,
          lineHeight = 20.sp
        )
        Spacer(Modifier.height(4.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          // Verified Transport indicator badge
          val (transportLabel, transportBg) = when (message.transportType) {
            MessageTransportType.LOCAL_WIFI -> Pair("Wi-Fi", Color(0xFF0D9488))
            MessageTransportType.BLUETOOTH_MESH -> Pair("BLE Mesh", Color(0xFF4F46E5))
            MessageTransportType.ONLINE_RELAY -> Pair("Şifreli Ağ", Color(0xFF2563EB))
            MessageTransportType.OFFLINE_QUEUED -> Pair("Kuyrukta", Color(0xFFD97706))
            MessageTransportType.DIRECT_P2P -> Pair("P2P", Color(0xFF0284C7))
          }

          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(if (isOutgoing) Color.White.copy(alpha = 0.22f) else transportBg.copy(alpha = 0.12f))
              .padding(horizontal = 4.dp, vertical = 1.dp)
          ) {
            Text(
              text = transportLabel,
              color = if (isOutgoing) Color.White else transportBg,
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold
            )
          }

          Text(
            text = timeText,
            color = if (isOutgoing) Color.White.copy(alpha = 0.8f) else PeykrTheme.colors.textMuted,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.5.sp
          )
          if (isOutgoing) {
            when (message.status) {
              MessageStatus.DELIVERED, MessageStatus.READ -> {
                PremiumIcon(
                  kind = PremiumIconKind.CheckAll,
                  description = "İletildi",
                  tint = Color(0xFFBFDBFE),
                  modifier = Modifier.size(13.dp)
                )
              }
              MessageStatus.SENT -> {
                Text("✓", color = Color(0xFFBFDBFE), fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
              MessageStatus.QUEUED_OFFLINE -> {
                Text("⏳", fontSize = 10.sp)
              }
              else -> {
                Text("•", color = Color(0xFFBFDBFE), fontSize = 11.sp)
              }
            }
          }
        }
      }
    }
  }
}