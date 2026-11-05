package com.peykr.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.peykr.app.AboutDetail
import com.peykr.app.BiometricSettingsDetail
import com.peykr.app.DataBackupDetail
import com.peykr.app.DeviceIdentityDetail
import com.peykr.app.FileTransfer
import com.peykr.app.LocationSharingDetail
import com.peykr.app.Main
import com.peykr.app.MessagesList
import com.peykr.app.PairingFlow
import com.peykr.app.Settings
import com.peykr.app.TrustedPeople
import com.peykr.app.core.auth.BiometricAvailability
import com.peykr.app.core.auth.SecurityGate
import com.peykr.app.core.identity.DeviceIdentity
import com.peykr.app.core.identity.DeviceIdentityInfo
import com.peykr.app.core.identity.DeviceIdentityState
import com.peykr.app.data.AppThemeMode
import com.peykr.app.domain.contacts.PairingStatus
import com.peykr.app.domain.contacts.TrustedContact
import com.peykr.app.domain.contacts.TrustedContactRepository
import com.peykr.app.localization.AppLanguage
import com.peykr.app.ui.theme.AppHorizontalPadding
import com.peykr.app.ui.theme.CardBorderColor
import com.peykr.app.ui.theme.CardShadowColor
import com.peykr.app.ui.theme.ErrorRed
import com.peykr.app.ui.theme.ErrorRedLight
import com.peykr.app.ui.theme.PastelBlueBg
import com.peykr.app.ui.theme.PastelBlueFg
import com.peykr.app.ui.theme.PastelCyanBg
import com.peykr.app.ui.theme.PastelCyanFg
import com.peykr.app.ui.theme.PastelIndigoBg
import com.peykr.app.ui.theme.PastelIndigoFg
import com.peykr.app.ui.theme.PastelPurpleBg
import com.peykr.app.ui.theme.PastelPurpleFg
import com.peykr.app.ui.theme.PastelTealBg
import com.peykr.app.ui.theme.PastelTealFg
import com.peykr.app.ui.theme.PeykrBadge
import com.peykr.app.ui.theme.PeykrBottomNavigationBar
import com.peykr.app.ui.theme.PeykrCard
import com.peykr.app.ui.theme.PeykrGroupedCard
import com.peykr.app.ui.theme.PeykrGroupedRow
import com.peykr.app.ui.theme.PeykrScreenContainer
import com.peykr.app.ui.theme.PeykrSearchField
import com.peykr.app.ui.theme.PeykrTheme
import com.peykr.app.ui.theme.PremiumIcon
import com.peykr.app.ui.theme.PremiumIconKind
import com.peykr.app.ui.theme.SuccessGreen
import com.peykr.app.ui.theme.SuccessGreenLight

@Composable
fun MainScreen(
  onNavigate: (NavKey) -> Unit,
  onPeopleClick: () -> Unit,
  onSettingsClick: () -> Unit,
  contactRepository: TrustedContactRepository,
  modifier: Modifier = Modifier,
  deviceIdentity: DeviceIdentity? = null,
  securityGate: SecurityGate? = null,
  viewModel: MainScreenViewModel? = null,
) {
  val actualViewModel = viewModel ?: viewModel { MainScreenViewModel(contactRepository, deviceIdentity) }
  val state by actualViewModel.uiState.collectAsStateWithLifecycle()

  val biometricAvailability = securityGate?.canAuthenticate() ?: BiometricAvailability.UNSUPPORTED
  val idState = deviceIdentity?.identityState?.collectAsStateWithLifecycle()?.value

  val isDbOk = state is MainScreenUiState.Success
  val isIdentityOk = idState is DeviceIdentityState.Ready
  val isOverallSecure = isDbOk && isIdentityOk

  var showSosDialog by remember { mutableStateOf(false) }
  var showSosConfirmedNotification by remember { mutableStateOf(false) }
  var showNotificationAlert by remember { mutableStateOf(false) }

  Scaffold(
    modifier = modifier,
    containerColor = Color.Transparent,
    contentWindowInsets = WindowInsets.safeDrawing,
    bottomBar = { PeykrBottomNavigationBar(current = Main, onNavigate = onNavigate) }
  ) { padding ->
    PeykrScreenContainer {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = AppHorizontalPadding, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
      ) {
        // Top Status Pill
        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFEBF3FF))
            .border(1.dp, Color(0xFFD6E6FE), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(SuccessGreen)
          )
          Text(
            text = "Uçtan Uca Şifreli Alan · Çevrimdışı Güvenli",
            color = Color(0xFF1E60D5),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 11.5.sp
          )
        }

        // Top Header: Welcome headline & Notification action (NO LOGO ON HOME SCREEN)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "Merhaba 👋",
              color = PeykrTheme.colors.textPrimary,
              style = MaterialTheme.typography.headlineMedium,
              fontWeight = FontWeight.Bold,
              fontSize = 30.sp,
              letterSpacing = (-0.6).sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
              text = "Güvenli, özel ve doğrudan iletişim.",
              color = Color(0xFF64748B),
              style = MaterialTheme.typography.bodyMedium,
              fontSize = 14.sp
            )
          }

          // Elevated Notification Bell Button with badge dot
          Box(
            modifier = Modifier
              .size(46.dp)
              .shadow(elevation = 3.dp, shape = CircleShape, spotColor = CardShadowColor)
              .clip(CircleShape)
              .background(Color.White)
              .border(1.dp, Color(0xFFE2E8F0), CircleShape)
              .clickable { showNotificationAlert = true },
            contentAlignment = Alignment.Center
          ) {
            PremiumIcon(
              kind = PremiumIconKind.Bell,
              description = "Bildirimler",
              tint = Color(0xFF0F172A),
              modifier = Modifier.size(20.dp)
            )
            // Notification dot
            Box(
              modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E60D5))
                .border(1.5.dp, Color.White, CircleShape)
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 10.dp)
            )
          }
        }

        // Section Title: Hızlı İşlemler
        Text(
          text = "Hızlı İşlemler",
          color = PeykrTheme.colors.textPrimary,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          fontSize = 17.sp,
          letterSpacing = (-0.2).sp
        )

        // 2x2 Grid with high visual fidelity cards
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          HomeShortcutCard(
            title = "Mesajlar",
            subtitle = "Şifreli sohbetler",
            icon = PremiumIconKind.Message,
            iconBg = PastelBlueBg,
            iconFg = PastelBlueFg,
            modifier = Modifier.weight(1f),
            onClick = { onNavigate(MessagesList) }
          )
          HomeShortcutCard(
            title = "Kişiler",
            subtitle = "Güvenilen cihazlar",
            icon = PremiumIconKind.People,
            iconBg = PastelIndigoBg,
            iconFg = PastelIndigoFg,
            modifier = Modifier.weight(1f),
            onClick = { onNavigate(TrustedPeople) }
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          HomeShortcutCard(
            title = "Konum",
            subtitle = "Güvenli paylaşım",
            icon = PremiumIconKind.Location,
            iconBg = PastelCyanBg,
            iconFg = PastelCyanFg,
            modifier = Modifier.weight(1f),
            onClick = { onNavigate(LocationSharingDetail) }
          )
          HomeShortcutCard(
            title = "Dosyalar",
            subtitle = "Yerel aktarım",
            icon = PremiumIconKind.File,
            iconBg = PastelTealBg,
            iconFg = PastelTealFg,
            modifier = Modifier.weight(1f),
            onClick = { onNavigate(FileTransfer) }
          )
        }

        // Refined Security Status Component
        PeykrCard(
          modifier = Modifier.fillMaxWidth(),
          containerColor = Color.White,
          borderColor = CardBorderColor,
          shape = RoundedCornerShape(20.dp),
          elevation = 3.dp,
          contentPadding = PaddingValues(16.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isOverallSecure) Color(0xFFECFDF5) else Color(0xFFFEF3C7)),
              contentAlignment = Alignment.Center
            ) {
              PremiumIcon(
                kind = PremiumIconKind.Shield,
                description = "Güvenlik",
                tint = if (isOverallSecure) SuccessGreen else Color(0xFFD97706),
                modifier = Modifier.size(24.dp)
              )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                  text = if (isOverallSecure) "Güvenlik Aktif" else "Güvenlik Kontrolü",
                  color = Color(0xFF0F172A),
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp
                )
                PeykrBadge(
                  text = if (isOverallSecure) "KORUMALI" else "UYARI",
                  color = if (isOverallSecure) SuccessGreen else Color(0xFFD97706),
                  backgroundColor = if (isOverallSecure) SuccessGreenLight else Color(0xFFFEF3C7)
                )
              }
              Spacer(Modifier.height(3.dp))
              Text(
                text = "Android Keystore · SQLCipher AES-256 yerel depolama",
                color = Color(0xFF64748B),
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp
              )
            }

            // Concentric status dot
            Box(
              modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background((if (isOverallSecure) SuccessGreen else Color(0xFFD97706)).copy(alpha = 0.2f)),
              contentAlignment = Alignment.Center
            ) {
              Box(
                modifier = Modifier
                  .size(9.dp)
                  .clip(CircleShape)
                  .background(if (isOverallSecure) SuccessGreen else Color(0xFFD97706))
              )
            }
          }
        }

        // Premium Emergency SOS Component
        PeykrCard(
          onClick = { showSosDialog = true },
          containerColor = Color(0xFFFFF5F5),
          borderColor = Color(0xFFFECDD3),
          shape = RoundedCornerShape(20.dp),
          elevation = 3.dp,
          contentPadding = PaddingValues(16.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFEF4444)),
              contentAlignment = Alignment.Center
            ) {
              PremiumIcon(
                kind = PremiumIconKind.SOS,
                description = "SOS",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
              )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                  text = "Acil Durum (SOS)",
                  color = Color(0xFF991B1B),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  fontSize = 16.sp
                )
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFDC2626))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                  Text(
                    text = "YARDIM",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                  )
                }
              }
              Spacer(Modifier.height(3.dp))
              Text(
                text = "Kayıtlı kişilere canlı konum ve acil durum uyarısı gönderin.",
                color = Color(0xFFB91C1C),
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp
              )
            }

            PremiumIcon(
              kind = PremiumIconKind.MicroChevron,
              description = "",
              tint = Color(0xFFEF4444),
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }
    }
  }

  // SOS Confirmation Dialog
  if (showSosDialog) {
    AlertDialog(
      onDismissRequest = { showSosDialog = false },
      containerColor = Color.White,
      shape = RoundedCornerShape(22.dp),
      title = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(ErrorRedLight),
            contentAlignment = Alignment.Center
          ) {
            PremiumIcon(PremiumIconKind.SOS, "", tint = ErrorRed, modifier = Modifier.size(18.dp))
          }
          Text(
            text = "Acil Durum Bildirimi",
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFF991B1B),
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp
          )
        }
      },
      text = {
        Text(
          text = "Kayıtlı güvenilir kişilerinize konum bilginiz ve acil durum uyarısı şifrelenerek iletilecektir. Bu işlemi onaylıyor musunuz?",
          style = MaterialTheme.typography.bodyMedium,
          color = Color(0xFF334155),
          fontSize = 14.5.sp,
          lineHeight = 21.sp
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showSosDialog = false
            showSosConfirmedNotification = true
          },
          colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
          shape = RoundedCornerShape(12.dp)
        ) {
          Text("Evet, Gönder", color = Color.White, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showSosDialog = false }) {
          Text("İptal", color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
        }
      }
    )
  }

  if (showSosConfirmedNotification) {
    AlertDialog(
      onDismissRequest = { showSosConfirmedNotification = false },
      containerColor = Color.White,
      shape = RoundedCornerShape(22.dp),
      title = { Text("SOS İletildi", color = SuccessGreen, fontWeight = FontWeight.Bold) },
      text = { Text("Acil durum sinyali ve güvenli konumunuz şifrelenerek güvenilir kişilere gönderildi.", color = Color(0xFF334155)) },
      confirmButton = {
        Button(
          onClick = { showSosConfirmedNotification = false },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E60D5)),
          shape = RoundedCornerShape(12.dp)
        ) {
          Text("Tamam", color = Color.White)
        }
      }
    )
  }

  if (showNotificationAlert) {
    AlertDialog(
      onDismissRequest = { showNotificationAlert = false },
      containerColor = Color.White,
      shape = RoundedCornerShape(22.dp),
      title = { Text("Bildirimler", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold) },
      text = { Text("Şu anda okunmamış yeni bir sistem uyarısı veya güvenlik bildirimi bulunmamaktadır.", color = Color(0xFF64748B)) },
      confirmButton = {
        Button(
          onClick = { showNotificationAlert = false },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E60D5)),
          shape = RoundedCornerShape(12.dp)
        ) {
          Text("Tamam", color = Color.White)
        }
      }
    )
  }
}

@Composable
private fun HomeShortcutCard(
  title: String,
  subtitle: String,
  icon: PremiumIconKind,
  iconBg: Color,
  iconFg: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  PeykrCard(
    modifier = modifier.height(132.dp),
    onClick = onClick,
    containerColor = Color.White,
    borderColor = CardBorderColor,
    shape = RoundedCornerShape(22.dp),
    elevation = 3.dp,
    contentPadding = PaddingValues(16.dp)
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
      ) {
        Box(
          modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(iconBg),
          contentAlignment = Alignment.Center
        ) {
          PremiumIcon(
            kind = icon,
            description = title,
            tint = iconFg,
            modifier = Modifier.size(24.dp)
          )
        }

        PremiumIcon(
          kind = PremiumIconKind.MicroChevron,
          description = "",
          tint = Color(0xFFCBD5E1),
          modifier = Modifier.size(15.dp).padding(top = 2.dp)
        )
      }

      Column {
        Text(
          text = title,
          color = Color(0xFF0F172A),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp,
          letterSpacing = (-0.2).sp
        )
        Spacer(Modifier.height(2.dp))
        Text(
          text = subtitle,
          color = Color(0xFF64748B),
          style = MaterialTheme.typography.bodySmall,
          fontSize = 12.5.sp
        )
      }
    }
  }
}

// -------------------------------------------------------------
// Trusted People (Kişiler) Screen
// -------------------------------------------------------------
@Composable
fun TrustedPeopleScreen(
  onNavigate: (NavKey) -> Unit,
  onBack: () -> Unit,
  contactRepository: TrustedContactRepository,
  modifier: Modifier = Modifier,
  deviceIdentity: DeviceIdentity? = null,
  viewModel: MainScreenViewModel? = null,
) {
  val actualViewModel = viewModel ?: viewModel { MainScreenViewModel(contactRepository, deviceIdentity) }
  val state by actualViewModel.uiState.collectAsStateWithLifecycle()
  var contactToDelete by remember { mutableStateOf<TrustedContact?>(null) }
  var searchQuery by remember { mutableStateOf("") }

  Scaffold(
    modifier = modifier,
    containerColor = Color.Transparent,
    contentWindowInsets = WindowInsets.safeDrawing,
    bottomBar = { PeykrBottomNavigationBar(current = TrustedPeople, onNavigate = onNavigate) }
  ) { padding ->
    PeykrScreenContainer {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .padding(horizontal = AppHorizontalPadding, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // Top row with title and Add Device button
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "Kişiler",
              color = PeykrTheme.colors.textPrimary,
              style = MaterialTheme.typography.headlineMedium,
              fontWeight = FontWeight.Bold,
              fontSize = 28.sp,
              letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(3.dp))
            Text(
              text = "Uçtan uca şifreli güvenilir cihazlar.",
              color = PeykrTheme.colors.textSecondary,
              style = MaterialTheme.typography.bodyMedium,
              fontSize = 14.sp
            )
          }

          // Modern primary pill button for + Eşleştir
          Box(
            modifier = Modifier
              .shadow(4.dp, RoundedCornerShape(20.dp), spotColor = Color(0x331E60D5))
              .clip(RoundedCornerShape(20.dp))
              .background(
                Brush.horizontalGradient(
                  listOf(Color(0xFF1E60D5), Color(0xFF1751B8))
                )
              )
              .clickable { onNavigate(PairingFlow) }
              .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              PremiumIcon(
                kind = PremiumIconKind.Plus,
                description = "Eşleştir",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = "Eşleştir",
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
          placeholder = "Kişilerde veya kimliklerde ara..."
        )

        when (val uiState = state) {
          is MainScreenUiState.Loading -> {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
              Text("Yükleniyor...", color = Color(0xFF64748B), style = MaterialTheme.typography.bodyMedium)
            }
          }
          is MainScreenUiState.Error, is MainScreenUiState.DatabaseError, is MainScreenUiState.IdentityError -> {
            PeykrCard(modifier = Modifier.fillMaxWidth()) {
              Text("Bağlantı Durumu", color = Color(0xFF0F172A), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              Spacer(Modifier.height(4.dp))
              Text("Güvenlik veritabanı kontrol ediliyor.", color = Color(0xFF64748B), style = MaterialTheme.typography.bodyMedium)
              Spacer(Modifier.height(8.dp))
              TextButton(onClick = { actualViewModel.retry() }) {
                Text("Yeniden Dene", color = Color(0xFF1E60D5))
              }
            }
          }
          is MainScreenUiState.Success -> {
            val allPeople = uiState.data
            val people = if (searchQuery.isBlank()) allPeople else allPeople.filter { it.displayName.contains(searchQuery, ignoreCase = true) }

            if (people.isEmpty()) {
              // High-fidelity empty state
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
                  Box(
                    modifier = Modifier
                      .size(90.dp)
                      .clip(CircleShape)
                      .background(Color(0xFFEEF0FF)),
                    contentAlignment = Alignment.Center
                  ) {
                    Box(
                      modifier = Modifier
                        .size(62.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFE0E7FF)),
                      contentAlignment = Alignment.Center
                    ) {
                      PremiumIcon(
                        kind = PremiumIconKind.People,
                        description = "Kişiler",
                        tint = Color(0xFF4F46E5),
                        modifier = Modifier.size(30.dp)
                      )
                    }
                  }

                  Text(
                    text = "Henüz güvenilir kişi yok",
                    color = Color(0xFF0F172A),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                  )

                  Text(
                    text = "Uçtan uca şifreli doğrudan iletişim için yakındaki bir cihazla güvenli eşleşme başlatın.",
                    color = Color(0xFF64748B),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                  )

                  Spacer(Modifier.height(4.dp))

                  Button(
                    onClick = { onNavigate(PairingFlow) },
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
                        text = "Cihaz Eşleştir",
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
                items(people, key = { it.id }) { contact ->
                  ContactCard(
                    contact = contact,
                    onDelete = { contactToDelete = contact },
                    onMessage = { onNavigate(MessagesList) }
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  // Deletion Dialog
  contactToDelete?.let { contact ->
    AlertDialog(
      onDismissRequest = { contactToDelete = null },
      containerColor = Color.White,
      shape = RoundedCornerShape(22.dp),
      title = { Text("Cihazı Kaldır", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold) },
      text = { Text("${contact.displayName} cihazı güvenilir kişiler listenizden silinsin mi? Şifreli sohbet geçmişi korunacaktır.", color = Color(0xFF64748B)) },
      confirmButton = {
        Button(
          onClick = {
            actualViewModel.deleteContact(contact.id)
            contactToDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
          shape = RoundedCornerShape(12.dp)
        ) {
          Text("Sil", color = Color.White, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { contactToDelete = null }) {
          Text("İptal", color = Color(0xFF64748B))
        }
      }
    )
  }
}

@Composable
private fun ContactCard(
  contact: TrustedContact,
  onDelete: () -> Unit,
  onMessage: () -> Unit
) {
  PeykrCard(
    modifier = Modifier.fillMaxWidth(),
    containerColor = Color.White,
    borderColor = CardBorderColor,
    shape = RoundedCornerShape(18.dp),
    elevation = 2.dp,
    contentPadding = PaddingValues(14.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // 48dp Colorful Avatar
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(CircleShape)
          .background(
            Brush.linearGradient(
              listOf(Color(0xFF6366F1), Color(0xFF4338CA))
            )
          ),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = contact.displayName.take(1).uppercase(),
          color = Color.White,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          fontSize = 17.sp
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
            text = contact.displayName,
            color = Color(0xFF0F172A),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
          )
          ContactStatusBadge(contact.pairingStatus)
        }

        Spacer(Modifier.height(4.dp))

        val fingerprint = contact.deviceFingerprint ?: contact.id
        Text(
          text = formatFingerprintShort(fingerprint),
          color = Color(0xFF94A3B8),
          style = MaterialTheme.typography.bodySmall,
          fontFamily = FontFamily.Monospace,
          fontSize = 11.5.sp
        )
      }

      Spacer(Modifier.width(10.dp))

      // Direct message action
      Box(
        modifier = Modifier
          .size(38.dp)
          .clip(CircleShape)
          .background(Color(0xFFEBF3FF))
          .clickable(onClick = onMessage),
        contentAlignment = Alignment.Center
      ) {
        PremiumIcon(
          kind = PremiumIconKind.Message,
          description = "Mesaj",
          tint = Color(0xFF1E60D5),
          modifier = Modifier.size(18.dp)
        )
      }

      Spacer(Modifier.width(6.dp))

      // Delete action
      TextButton(onClick = onDelete) {
        Text("Sil", color = ErrorRed, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun ContactStatusBadge(status: PairingStatus) {
  val (label, bg, fg) = when (status) {
    PairingStatus.PAIRED -> Triple("Eşleşti", SuccessGreenLight, SuccessGreen)
    PairingStatus.PENDING -> Triple("Beklemede", Color(0xFFFEF3C7), Color(0xFFD97706))
    PairingStatus.REVOKED -> Triple("İptal Edildi", ErrorRedLight, ErrorRed)
    PairingStatus.BLOCKED -> Triple("Engellendi", Color(0xFFF1F5F9), Color(0xFF64748B))
    PairingStatus.NOT_PAIRED -> Triple("Eşleşmedi", Color(0xFFF1F5F9), Color(0xFF64748B))
  }
  PeykrBadge(text = label, color = fg, backgroundColor = bg)
}

private fun formatFingerprintShort(raw: String): String {
  if (raw.length <= 16) return raw
  return "${raw.take(8)}...${raw.takeLast(8)}"
}

// -------------------------------------------------------------
// Settings (Ayarlar) Screen
// -------------------------------------------------------------
@Composable
fun SettingsScreen(
  onNavigate: (NavKey) -> Unit,
  onBack: () -> Unit,
  selectedLanguage: AppLanguage,
  onLanguageSelected: (AppLanguage) -> Unit,
  selectedThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
  onThemeModeSelected: (AppThemeMode) -> Unit = {},
  modifier: Modifier = Modifier,
  deviceIdentity: DeviceIdentity? = null,
  securityGate: SecurityGate? = null,
) {
  var showThemeDialog by remember { mutableStateOf(false) }

  val identityState = deviceIdentity?.identityState?.collectAsStateWithLifecycle()?.value
    ?: DeviceIdentityState.Ready(DeviceIdentityInfo("", ""))
  val identitySummary = when (identityState) {
    is DeviceIdentityState.Ready -> {
      if (identityState.identity.id.isNotBlank()) {
        val truncated = if (identityState.identity.id.length > 8) identityState.identity.id.take(8) else identityState.identity.id
        "Kimlik: $truncated…"
      } else {
        "Hazır"
      }
    }
    is DeviceIdentityState.Initializing -> "Hazırlanıyor…"
    else -> "Güvenlik uyarısı"
  }

  val biometricStatus = when (securityGate?.canAuthenticate()) {
    BiometricAvailability.AVAILABLE -> "Kullanılabilir"
    BiometricAvailability.NONE_ENROLLED -> "Kayıtlı biyometri yok"
    BiometricAvailability.NO_HARDWARE -> "Sensör bulunamadı"
    BiometricAvailability.HARDWARE_UNAVAILABLE -> "Sensör meşgul"
    BiometricAvailability.SECURITY_UPDATE_REQUIRED -> "Güncelleme gerekli"
    BiometricAvailability.UNSUPPORTED, null -> "Desteklenmiyor"
  }

  val themeSummary = when (selectedThemeMode) {
    AppThemeMode.SYSTEM -> "Sistem varsayılanı"
    AppThemeMode.LIGHT -> "Açık tema"
    AppThemeMode.DARK -> "Koyu tema"
  }

  Scaffold(
    modifier = modifier,
    containerColor = Color.Transparent,
    contentWindowInsets = WindowInsets.safeDrawing,
    bottomBar = { PeykrBottomNavigationBar(current = Settings, onNavigate = onNavigate) }
  ) { padding ->
    PeykrScreenContainer {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = AppHorizontalPadding, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
      ) {
        Column {
          Text(
            text = "Ayarlar",
            color = PeykrTheme.colors.textPrimary,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            letterSpacing = (-0.5).sp
          )
          Spacer(Modifier.height(3.dp))
          Text(
            text = "Peykr tercihleri ve güvenlik mimarisi.",
            color = PeykrTheme.colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 14.sp
          )
        }

        // Güvenlik ve Kimlik Grubu (Refined iOS/Modern Android Grouped Card)
        SettingsSectionHeader("GÜVENLİK VE KİMLİK")
        PeykrGroupedCard {
          PeykrGroupedRow(
            icon = PremiumIconKind.Shield,
            iconBg = PastelBlueBg,
            iconFg = PastelBlueFg,
            title = "Biyometrik doğrulama",
            detail = biometricStatus,
            onClick = { onNavigate(BiometricSettingsDetail) },
            showDivider = true
          )
          PeykrGroupedRow(
            icon = PremiumIconKind.Lock,
            iconBg = PastelIndigoBg,
            iconFg = PastelIndigoFg,
            title = "Uygulama PIN'i",
            detail = "Sensörsüz cihazlar için yerel koruma",
            onClick = { onNavigate(BiometricSettingsDetail) },
            showDivider = true
          )
          PeykrGroupedRow(
            icon = PremiumIconKind.Device,
            iconBg = PastelTealBg,
            iconFg = PastelTealFg,
            title = "Cihaz kimliği",
            detail = identitySummary,
            onClick = { onNavigate(DeviceIdentityDetail) },
            showDivider = false
          )
        }

        // Gizlilik ve Veri Grubu
        SettingsSectionHeader("GİZLİLİK VE VERİ")
        PeykrGroupedCard {
          PeykrGroupedRow(
            icon = PremiumIconKind.Location,
            iconBg = PastelCyanBg,
            iconFg = PastelCyanFg,
            title = "Konum paylaşımı",
            detail = "Sıfır bulut · Yalnızca doğrudan eşleşen cihazlar",
            onClick = { onNavigate(LocationSharingDetail) },
            showDivider = true
          )
          PeykrGroupedRow(
            icon = PremiumIconKind.Backup,
            iconBg = PastelPurpleBg,
            iconFg = PastelPurpleFg,
            title = "Veri ve depolama güvenliği",
            detail = "SQLCipher AES-256 yerel şifreleme",
            onClick = { onNavigate(DataBackupDetail) },
            showDivider = false
          )
        }

        // Görünüm ve Dil Grubu
        SettingsSectionHeader("GÖRÜNÜM VE DİL")
        PeykrGroupedCard {
          PeykrGroupedRow(
            icon = PremiumIconKind.Theme,
            iconBg = PastelBlueBg,
            iconFg = PastelBlueFg,
            title = "Tema",
            detail = themeSummary,
            onClick = { showThemeDialog = true },
            showDivider = true
          )
          PeykrGroupedRow(
            icon = PremiumIconKind.Language,
            iconBg = PastelTealBg,
            iconFg = PastelTealFg,
            title = "Uygulama dili",
            detail = if (selectedLanguage == AppLanguage.TURKISH) "Türkçe" else "English",
            onClick = {
              onLanguageSelected(if (selectedLanguage == AppLanguage.TURKISH) AppLanguage.ENGLISH else AppLanguage.TURKISH)
            },
            showDivider = false
          )
        }

        // Uygulama Bilgisi Grubu
        SettingsSectionHeader("UYGULAMA")
        PeykrGroupedCard {
          PeykrGroupedRow(
            icon = PremiumIconKind.Info,
            iconBg = PastelIndigoBg,
            iconFg = PastelIndigoFg,
            title = "Peykr hakkında",
            detail = "Gizlilik odaklı, sunucusuz mimari (v1.0.0)",
            onClick = { onNavigate(AboutDetail) },
            showDivider = false
          )
        }
      }
    }
  }

  // Theme Selection Dialog
  if (showThemeDialog) {
    AlertDialog(
      onDismissRequest = { showThemeDialog = false },
      containerColor = Color.White,
      shape = RoundedCornerShape(22.dp),
      title = {
        Text(
          text = "Tema Seçimi",
          color = Color(0xFF0F172A),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          ThemeOptionItem(
            title = "Sistem Varsayılanı",
            selected = selectedThemeMode == AppThemeMode.SYSTEM,
            onSelect = {
              onThemeModeSelected(AppThemeMode.SYSTEM)
              showThemeDialog = false
            }
          )
          ThemeOptionItem(
            title = "Açık Tema",
            selected = selectedThemeMode == AppThemeMode.LIGHT,
            onSelect = {
              onThemeModeSelected(AppThemeMode.LIGHT)
              showThemeDialog = false
            }
          )
          ThemeOptionItem(
            title = "Koyu Tema",
            selected = selectedThemeMode == AppThemeMode.DARK,
            onSelect = {
              onThemeModeSelected(AppThemeMode.DARK)
              showThemeDialog = false
            }
          )
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = { showThemeDialog = false }) {
          Text("Kapat", color = Color(0xFF1E60D5))
        }
      }
    )
  }
}

@Composable
private fun SettingsSectionHeader(title: String) {
  Text(
    text = title,
    style = MaterialTheme.typography.labelMedium,
    color = Color(0xFF64748B),
    fontWeight = FontWeight.Bold,
    fontSize = 11.5.sp,
    letterSpacing = 0.8.sp,
    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
  )
}

@Composable
private fun ThemeOptionItem(
  title: String,
  selected: Boolean,
  onSelect: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onSelect)
      .padding(horizontal = 8.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    RadioButton(
      selected = selected,
      onClick = onSelect,
      colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF1E60D5))
    )
    Spacer(Modifier.width(8.dp))
    Text(
      text = title,
      color = Color(0xFF0F172A),
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
    )
  }
}