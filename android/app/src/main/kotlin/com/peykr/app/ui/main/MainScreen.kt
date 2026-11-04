package com.peykr.app.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.peykr.app.AboutDetail
import com.peykr.app.BiometricSettingsDetail
import com.peykr.app.DataBackupDetail
import com.peykr.app.DeviceIdentityDetail
import com.peykr.app.LocationSharingDetail
import com.peykr.app.Main
import com.peykr.app.PairingFlow
import com.peykr.app.R
import com.peykr.app.Settings
import com.peykr.app.TrustedPeople
import com.peykr.app.core.auth.AuthPromptConfig
import com.peykr.app.core.auth.AuthenticationResult
import com.peykr.app.core.auth.BiometricAvailability
import com.peykr.app.core.auth.SecurityGate
import com.peykr.app.core.auth.SecurityGateException
import com.peykr.app.core.auth.SecurityGateScope
import com.peykr.app.core.identity.DeviceIdentity
import com.peykr.app.core.identity.DeviceIdentityInfo
import com.peykr.app.core.identity.DeviceIdentityState
import com.peykr.app.data.AppThemeMode
import com.peykr.app.data.contacts.DatabaseState
import com.peykr.app.domain.contacts.PairingStatus
import com.peykr.app.domain.contacts.TrustedContact
import com.peykr.app.domain.contacts.TrustedContactRepository
import com.peykr.app.localization.AppLanguage
import com.peykr.app.ui.theme.AppHorizontalPadding
import com.peykr.app.ui.theme.DividerDark
import com.peykr.app.ui.theme.Navy950
import com.peykr.app.ui.theme.PeykrTheme
import com.peykr.app.ui.theme.PremiumIcon
import com.peykr.app.ui.theme.PremiumIconKind
import com.peykr.app.ui.theme.PremiumInfoRow
import com.peykr.app.ui.theme.PremiumSurface
import com.peykr.app.ui.theme.PrimaryTurquoise
import com.peykr.app.ui.theme.SectionHeader
import com.peykr.app.ui.theme.TextMuted
import com.peykr.app.ui.theme.TextPrimary
import com.peykr.app.ui.theme.TextSecondary
import com.peykr.app.ui.theme.peykrEmblem
import com.peykr.app.ui.theme.peykrLogo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

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
  val isBiometricsOk = biometricAvailability == BiometricAvailability.AVAILABLE

  val isOverallSecure = isDbOk && isIdentityOk

  Scaffold(
    modifier,
    containerColor = Navy950,
    contentWindowInsets = WindowInsets.safeDrawing,
    bottomBar = { PremiumBottomNavigation(Main, onNavigate) }
  ) { padding ->
    Page(Modifier.padding(padding)) {
      HomeBrand()
      FocusEmblem()
      Text(
        text = stringResource(R.string.home_headline),
        color = TextPrimary,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.align(Alignment.CenterHorizontally)
      )
      Text(
        text = stringResource(R.string.home_description),
        color = TextSecondary,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.align(Alignment.CenterHorizontally)
      )

      // Security Health Overview Card
      PremiumSurface(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          PremiumIcon(
            PremiumIconKind.Shield,
            stringResource(R.string.security_health_title),
            tint = if (isOverallSecure) PrimaryTurquoise else Color(0xFFFFB74D)
          )
          Spacer(Modifier.width(14.dp))
          Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.security_health_title), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Text(
              if (isOverallSecure) stringResource(R.string.security_health_secure) else stringResource(R.string.security_health_warning),
              color = TextPrimary,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold
            )
          }
          Box(
            Modifier
              .size(10.dp)
              .clip(CircleShape)
              .background(if (isOverallSecure) PrimaryTurquoise else Color(0xFFFFB74D))
          )
        }

        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(DividerDark))
        Spacer(Modifier.height(10.dp))

        // Security Layer Items
        SecurityLayerRow(
          title = stringResource(R.string.security_component_identity),
          status = when (idState) {
            is DeviceIdentityState.Ready -> stringResource(R.string.identity_status_ready)
            is DeviceIdentityState.Initializing -> stringResource(R.string.identity_status_initializing)
            else -> stringResource(R.string.identity_status_error)
          },
          isOk = isIdentityOk
        )
        Spacer(Modifier.height(6.dp))
        SecurityLayerRow(
          title = stringResource(R.string.security_component_database),
          status = when (state) {
            is MainScreenUiState.Success -> stringResource(R.string.system_status_ready)
            is MainScreenUiState.Loading -> stringResource(R.string.system_status_initializing)
            else -> stringResource(R.string.database_error_status)
          },
          isOk = isDbOk
        )
        Spacer(Modifier.height(6.dp))
        SecurityLayerRow(
          title = stringResource(R.string.security_component_biometrics),
          status = when (biometricAvailability) {
            BiometricAvailability.AVAILABLE -> stringResource(R.string.biometric_status_available)
            BiometricAvailability.NONE_ENROLLED -> stringResource(R.string.biometric_status_none_enrolled)
            BiometricAvailability.NO_HARDWARE -> stringResource(R.string.biometric_status_no_hardware)
            BiometricAvailability.HARDWARE_UNAVAILABLE -> stringResource(R.string.biometric_status_hardware_unavailable)
            BiometricAvailability.SECURITY_UPDATE_REQUIRED -> stringResource(R.string.biometric_status_update_required)
            BiometricAvailability.UNSUPPORTED -> stringResource(R.string.biometric_status_unsupported)
          },
          isOk = isBiometricsOk
        )
      }

      // Identity Error Banner if identity failed
      if (state is MainScreenUiState.IdentityError) {
        val identityError = state as MainScreenUiState.IdentityError
        PremiumSurface(Modifier.fillMaxWidth()) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            PremiumIcon(PremiumIconKind.Shield, stringResource(R.string.identity_error_title), tint = Color(0xFFFF5252))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
              Text(stringResource(R.string.identity_error_title), color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
              Spacer(Modifier.height(4.dp))
              val description = when (identityError.state) {
                is DeviceIdentityState.IdentityInvalidated -> stringResource(R.string.identity_error_invalidated_description)
                else -> stringResource(R.string.identity_error_description)
              }
              Text(description, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
          }
          if (identityError.isRecoverable) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { actualViewModel.retry() }, modifier = Modifier.align(Alignment.End)) {
              Text(stringResource(R.string.identity_error_retry), color = PrimaryTurquoise, style = MaterialTheme.typography.labelLarge)
            }
          }
        }
      }

      // Database Error Banner if database failed
      if (state is MainScreenUiState.DatabaseError) {
        val dbError = state as MainScreenUiState.DatabaseError
        PremiumSurface(Modifier.fillMaxWidth()) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            PremiumIcon(PremiumIconKind.Backup, stringResource(R.string.database_error_title), tint = Color(0xFFFF5252))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
              Text(stringResource(R.string.database_error_title), color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
              Spacer(Modifier.height(4.dp))
              Text(
                dbError.diagnosticMessage ?: stringResource(R.string.database_error_description),
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
              )
            }
          }
          Spacer(Modifier.height(12.dp))
          TextButton(onClick = { actualViewModel.retry() }, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.database_error_retry), color = PrimaryTurquoise, style = MaterialTheme.typography.labelLarge)
          }
        }
      }

      // Trusted Devices Summary
      val contactCount = (state as? MainScreenUiState.Success)?.data?.size ?: 0
      val peopleCountText = if (state is MainScreenUiState.Success) {
        stringResource(R.string.trusted_people_count, contactCount)
      } else {
        stringResource(R.string.trusted_devices_empty_short)
      }

      PremiumSurface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onPeopleClick
      ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          PremiumIcon(PremiumIconKind.People, stringResource(R.string.trusted_devices_summary_title))
          Spacer(Modifier.width(14.dp))
          Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.trusted_devices_summary_title), color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(peopleCountText, color = PrimaryTurquoise, style = MaterialTheme.typography.bodySmall)
          }
          PremiumIcon(PremiumIconKind.Arrow, "")
        }
      }

      // Quick Actions Section
      SectionHeader(stringResource(R.string.quick_actions_title))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickActionButton(
          modifier = Modifier.weight(1f),
          title = stringResource(R.string.action_pair_device),
          icon = PremiumIconKind.Device,
          onClick = { onNavigate(PairingFlow) }
        )
        QuickActionButton(
          modifier = Modifier.weight(1f),
          title = stringResource(R.string.action_trusted_people),
          icon = PremiumIconKind.People,
          onClick = onPeopleClick
        )
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickActionButton(
          modifier = Modifier.weight(1f),
          title = stringResource(R.string.action_location_sharing),
          icon = PremiumIconKind.Location,
          onClick = { onNavigate(LocationSharingDetail) }
        )
        QuickActionButton(
          modifier = Modifier.weight(1f),
          title = stringResource(R.string.action_security_settings),
          icon = PremiumIconKind.Settings,
          onClick = onSettingsClick
        )
      }
    }
  }
}

@Composable
private fun SecurityLayerRow(title: String, status: String, isOk: Boolean) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
    Text(title, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        status,
        color = if (isOk) PrimaryTurquoise else Color(0xFFFFB74D),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium
      )
      Spacer(Modifier.width(6.dp))
      Box(
        Modifier
          .size(6.dp)
          .clip(CircleShape)
          .background(if (isOk) PrimaryTurquoise else Color(0xFFFFB74D))
      )
    }
  }
}

@Composable
private fun QuickActionButton(
  modifier: Modifier = Modifier,
  title: String,
  icon: PremiumIconKind,
  onClick: () -> Unit
) {
  PremiumSurface(modifier = modifier, onClick = onClick) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      PremiumIcon(icon, title)
      Text(
        text = title,
        color = TextPrimary,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold
      )
    }
  }
}

@Composable
private fun Page(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  Column(
    modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = AppHorizontalPadding)
      .padding(top = 20.dp, bottom = 20.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    content = content
  )
}

@Composable
private fun HomeBrand() {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Image(
      painter = peykrEmblem(),
      contentDescription = "Peykr",
      modifier = Modifier.size(36.dp)
    )
    Spacer(Modifier.width(12.dp))
    Column {
      Text("Peykr", color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      Text(stringResource(R.string.home_kicker), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.weight(1f))
    Box(
      Modifier
        .clip(RoundedCornerShape(6.dp))
        .background(PrimaryTurquoise.copy(alpha = 0.15f))
        .border(1.dp, PrimaryTurquoise.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
        .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
      Text(stringResource(R.string.prototype_label), color = PrimaryTurquoise, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
private fun FocusEmblem() {
  Box(Modifier.fillMaxWidth().height(125.dp), contentAlignment = Alignment.Center) {
    Image(
      painter = peykrLogo(),
      contentDescription = stringResource(R.string.home_headline),
      modifier = Modifier.height(115.dp)
    )
  }
}

// -------------------------------------------------------------
// Trusted People Screen
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

  Scaffold(
    modifier,
    containerColor = Navy950,
    contentWindowInsets = WindowInsets.safeDrawing,
    bottomBar = { PremiumBottomNavigation(TrustedPeople, onNavigate) }
  ) { padding ->
    Page(Modifier.padding(padding)) {
      BackAction(onBack)
      SectionHeader(stringResource(R.string.trusted_people), stringResource(R.string.trusted_people_description))

      when (val uiState = state) {
        is MainScreenUiState.Loading -> {
          Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.database_loading), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
          }
        }
        is MainScreenUiState.IdentityError -> {
          PremiumSurface(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              PremiumIcon(PremiumIconKind.Shield, stringResource(R.string.identity_error_title), tint = Color(0xFFFF5252))
              Spacer(Modifier.width(12.dp))
              Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.identity_error_title), color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                val description = when (uiState.state) {
                  is DeviceIdentityState.IdentityInvalidated -> stringResource(R.string.identity_error_invalidated_description)
                  else -> stringResource(R.string.identity_error_description)
                }
                Text(description, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
              }
            }
            if (uiState.isRecoverable) {
              Spacer(Modifier.height(12.dp))
              TextButton(onClick = { actualViewModel.retry() }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.identity_error_retry), color = PrimaryTurquoise, style = MaterialTheme.typography.labelLarge)
              }
            }
          }
        }
        is MainScreenUiState.DatabaseError -> {
          PremiumSurface(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              PremiumIcon(PremiumIconKind.Backup, stringResource(R.string.database_error_title), tint = Color(0xFFFF5252))
              Spacer(Modifier.width(12.dp))
              Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.database_error_title), color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                  uiState.diagnosticMessage ?: stringResource(R.string.database_error_description),
                  color = TextSecondary,
                  style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                  "Durum kodu: ${uiState.state.name}",
                  color = TextMuted,
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = FontFamily.Monospace
                )
              }
            }
            Spacer(Modifier.height(12.dp))
            Button(
              onClick = { actualViewModel.retry() },
              colors = ButtonDefaults.buttonColors(containerColor = PrimaryTurquoise),
              modifier = Modifier.align(Alignment.End)
            ) {
              Text(stringResource(R.string.database_error_retry), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
          }
        }
        is MainScreenUiState.Error -> {
          PremiumSurface(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.database_error_title), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(uiState.throwable.message ?: stringResource(R.string.database_error_description), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { actualViewModel.retry() }, modifier = Modifier.align(Alignment.End)) {
              Text(stringResource(R.string.database_error_retry), color = PrimaryTurquoise)
            }
          }
        }
        is MainScreenUiState.Success -> {
          val people = uiState.data
          if (people.isEmpty()) {
            TrustIllustration()
            Text(
              stringResource(R.string.trusted_people_empty_title),
              color = TextPrimary,
              style = MaterialTheme.typography.titleLarge,
              modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Text(
              stringResource(R.string.trusted_people_empty_description),
              color = TextSecondary,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            PremiumSurface(Modifier.fillMaxWidth()) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                PremiumIcon(PremiumIconKind.Info, stringResource(R.string.trusted_people_privacy_note))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.trusted_people_privacy_note), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
              }
            }
            Spacer(Modifier.height(8.dp))
            Button(
              onClick = { onNavigate(PairingFlow) },
              modifier = Modifier.fillMaxWidth().height(50.dp),
              colors = ButtonDefaults.buttonColors(containerColor = PrimaryTurquoise),
            ) {
              Text(
                text = stringResource(R.string.action_pair_device),
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
              )
            }
          } else {
            people.forEach { person ->
              ContactCard(
                contact = person,
                onDelete = { contactToDelete = person }
              )
            }
            Spacer(Modifier.height(8.dp))
            Button(
              onClick = { onNavigate(PairingFlow) },
              modifier = Modifier.fillMaxWidth().height(50.dp),
              colors = ButtonDefaults.buttonColors(containerColor = PrimaryTurquoise),
            ) {
              Text(
                text = stringResource(R.string.pairing_action_add_device),
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
              )
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
      containerColor = Color(0xFF0F1A2C),
      title = { Text(stringResource(R.string.contact_delete_dialog_title), color = TextPrimary) },
      text = {
        Text(
          stringResource(R.string.contact_delete_dialog_desc, contact.displayName),
          color = TextSecondary
        )
      },
      confirmButton = {
        Button(
          onClick = {
            actualViewModel.deleteContact(contact.id)
            contactToDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
        ) {
          Text(stringResource(R.string.contact_delete_confirm), color = Color.White, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { contactToDelete = null }) {
          Text(stringResource(R.string.biometric_prompt_cancel), color = TextSecondary)
        }
      }
    )
  }
}

@Composable
private fun ContactCard(
  contact: TrustedContact,
  onDelete: () -> Unit
) {
  PremiumSurface(Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = contact.displayName,
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          Spacer(Modifier.width(8.dp))
          StatusBadge(contact.pairingStatus)
        }

        val fingerprint = contact.publicIdentity?.takeIf { it.isNotBlank() } ?: contact.id
        Spacer(Modifier.height(4.dp))
        Text(
          text = "${stringResource(R.string.contact_fingerprint_label)}: ${formatFingerprintShort(fingerprint)}",
          color = PrimaryTurquoise,
          style = MaterialTheme.typography.bodySmall,
          fontFamily = FontFamily.Monospace
        )

        val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(contact.createdAtEpochMillis))
        Spacer(Modifier.height(2.dp))
        Text(
          text = stringResource(R.string.contact_paired_date, dateStr),
          color = TextMuted,
          style = MaterialTheme.typography.labelSmall
        )
      }

      TextButton(onClick = onDelete) {
        Text(stringResource(R.string.contact_delete_action), color = Color(0xFFFF5252), style = MaterialTheme.typography.labelMedium)
      }
    }
  }
}

@Composable
private fun StatusBadge(status: PairingStatus) {
  val (label, bg, fg) = when (status) {
    PairingStatus.PAIRED -> Triple(stringResource(R.string.pairing_paired), Color(0x2234D9FF), PrimaryTurquoise)
    PairingStatus.PENDING -> Triple(stringResource(R.string.pairing_pending), Color(0x33FFB74D), Color(0xFFFFB74D))
    PairingStatus.REVOKED -> Triple(stringResource(R.string.pairing_revoked), Color(0x33FF5252), Color(0xFFFF5252))
    PairingStatus.NOT_PAIRED -> Triple(stringResource(R.string.pairing_not_paired), Color(0x229E9E9E), TextSecondary)
  }
  Box(
    Modifier
      .clip(RoundedCornerShape(4.dp))
      .background(bg)
      .padding(horizontal = 6.dp, vertical = 2.dp)
  ) {
    Text(label, color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
  }
}

private fun formatFingerprintShort(raw: String): String {
  if (raw.length <= 16) return raw
  return "${raw.take(8)}...${raw.takeLast(8)}"
}

@Composable
private fun TrustIllustration() {
  val strokeColor = PeykrTheme.colors.primaryAccent
  val bgCircle = PeykrTheme.colors.background
  Canvas(Modifier.fillMaxWidth().height(110.dp).semantics { contentDescription = "Güvenilir kişiler sembolü" }) {
    val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
    drawOval(strokeColor, androidx.compose.ui.geometry.Offset(center.x - 68.dp.toPx(), center.y - 16.dp.toPx()), androidx.compose.ui.geometry.Size(136.dp.toPx(), 58.dp.toPx()), style = Stroke(1.5.dp.toPx()))
    drawCircle(strokeColor, 13.dp.toPx(), center.copy(x = center.x - 20.dp.toPx(), y = center.y - 18.dp.toPx()))
    drawCircle(strokeColor, 11.dp.toPx(), center.copy(x = center.x + 20.dp.toPx(), y = center.y - 14.dp.toPx()))
    drawArc(strokeColor, 200f, 140f, false, androidx.compose.ui.geometry.Offset(center.x - 42.dp.toPx(), center.y - 3.dp.toPx()), androidx.compose.ui.geometry.Size(44.dp.toPx(), 40.dp.toPx()), style = Stroke(2.dp.toPx()))
    drawArc(strokeColor, 200f, 140f, false, androidx.compose.ui.geometry.Offset(center.x - 2.dp.toPx(), center.y + 2.dp.toPx()), androidx.compose.ui.geometry.Size(40.dp.toPx(), 36.dp.toPx()), style = Stroke(2.dp.toPx()))
    drawCircle(bgCircle, 21.dp.toPx(), center.copy(x = center.x + 56.dp.toPx(), y = center.y + 16.dp.toPx()))
    drawCircle(strokeColor, 18.dp.toPx(), center.copy(x = center.x + 56.dp.toPx(), y = center.y + 16.dp.toPx()), style = Stroke(2.dp.toPx()))
  }
}

// -------------------------------------------------------------
// Settings Screen
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
        stringResource(R.string.identity_ready_format, truncated)
      } else {
        stringResource(R.string.identity_status_ready)
      }
    }
    is DeviceIdentityState.Initializing -> stringResource(R.string.identity_initializing)
    is DeviceIdentityState.KeystoreUnavailable -> stringResource(R.string.identity_error_unavailable)
    is DeviceIdentityState.IdentityInvalidated -> stringResource(R.string.identity_error_invalidated)
    is DeviceIdentityState.GenerationFailed -> stringResource(R.string.identity_error_generation_failed)
  }

  val biometricStatus = when (securityGate?.canAuthenticate()) {
    BiometricAvailability.AVAILABLE -> stringResource(R.string.biometric_status_available)
    BiometricAvailability.NONE_ENROLLED -> stringResource(R.string.biometric_status_none_enrolled)
    BiometricAvailability.NO_HARDWARE -> stringResource(R.string.biometric_status_no_hardware)
    BiometricAvailability.HARDWARE_UNAVAILABLE -> stringResource(R.string.biometric_status_hardware_unavailable)
    BiometricAvailability.SECURITY_UPDATE_REQUIRED -> stringResource(R.string.biometric_status_update_required)
    BiometricAvailability.UNSUPPORTED, null -> stringResource(R.string.biometric_status_unsupported)
  }

  val themeSummary = when (selectedThemeMode) {
    AppThemeMode.SYSTEM -> stringResource(R.string.theme_system)
    AppThemeMode.LIGHT -> stringResource(R.string.theme_light)
    AppThemeMode.DARK -> stringResource(R.string.theme_dark)
  }

  Scaffold(
    modifier,
    containerColor = PeykrTheme.colors.background,
    contentWindowInsets = WindowInsets.safeDrawing,
    bottomBar = { PremiumBottomNavigation(Settings, onNavigate) }
  ) { padding ->
    Page(Modifier.padding(padding)) {
      SectionHeader(stringResource(R.string.settings_title), stringResource(R.string.settings_description))

      // Görünüm
      SettingsGroup(stringResource(R.string.appearance_section)) {
        PremiumInfoRow(
          PremiumIconKind.Theme,
          stringResource(R.string.theme_setting),
          themeSummary,
          stringResource(R.string.theme_setting),
          onClick = { showThemeDialog = true },
          trailing = {
            Text(
              themeSummary,
              color = PrimaryTurquoise,
              style = MaterialTheme.typography.labelMedium
            )
          }
        )
      }

      if (showThemeDialog) {
        ThemeSelectionDialog(
          selectedMode = selectedThemeMode,
          onDismiss = { showThemeDialog = false },
          onSelectMode = { mode ->
            onThemeModeSelected(mode)
            showThemeDialog = false
          }
        )
      }

      // Dil
      SettingsGroup(stringResource(R.string.language_section)) {
        PremiumInfoRow(
          PremiumIconKind.Language,
          stringResource(R.string.language_setting),
          languageSummary(selectedLanguage),
          stringResource(R.string.language_setting),
          trailing = { LanguageChoice(selectedLanguage, onLanguageSelected) }
        )
      }

      // Gizlilik
      SettingsGroup(stringResource(R.string.privacy_section)) {
        PremiumInfoRow(
          PremiumIconKind.Location,
          stringResource(R.string.location_sharing),
          stringResource(R.string.location_detail),
          stringResource(R.string.location_sharing),
          onClick = { onNavigate(LocationSharingDetail) }
        )
        PremiumInfoRow(
          PremiumIconKind.Backup,
          stringResource(R.string.backup_setting),
          stringResource(R.string.backup_detail),
          stringResource(R.string.backup_setting),
          onClick = { onNavigate(DataBackupDetail) }
        )
      }

      // Güvenlik
      SettingsGroup(stringResource(R.string.security_section)) {
        PremiumInfoRow(
          PremiumIconKind.Device,
          stringResource(R.string.identity_setting),
          identitySummary,
          stringResource(R.string.identity_setting),
          onClick = { onNavigate(DeviceIdentityDetail) }
        )
        PremiumInfoRow(
          PremiumIconKind.Device,
          stringResource(R.string.biometric_setting_title),
          biometricStatus,
          stringResource(R.string.biometric_setting_title),
          onClick = { onNavigate(BiometricSettingsDetail) }
        )
      }

      // Hakkında
      SettingsGroup(stringResource(R.string.about_section)) {
        PremiumInfoRow(
          PremiumIconKind.Info,
          stringResource(R.string.prototype_version),
          stringResource(R.string.prototype_version_detail),
          stringResource(R.string.prototype_version),
          onClick = { onNavigate(AboutDetail) }
        )
      }
    }
  }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(title, color = TextSecondary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
    content()
  }
}

@Composable
private fun languageSummary(language: AppLanguage): String =
  stringResource(if (language == AppLanguage.TURKISH) R.string.language_turkish else R.string.language_english)

@Composable
private fun LanguageChoice(selected: AppLanguage, onSelected: (AppLanguage) -> Unit) {
  TextButton(onClick = { onSelected(if (selected == AppLanguage.TURKISH) AppLanguage.ENGLISH else AppLanguage.TURKISH) }) {
    Text(
      stringResource(if (selected == AppLanguage.TURKISH) R.string.language_turkish else R.string.language_english),
      color = PrimaryTurquoise,
      style = MaterialTheme.typography.labelMedium
    )
  }
}

@Composable
private fun BackAction(onBack: () -> Unit) {
  TextButton(onClick = onBack) {
    Text(stringResource(R.string.back_action), color = PrimaryTurquoise)
  }
}

@Composable
private fun ThemeSelectionDialog(
  selectedMode: AppThemeMode,
  onDismiss: () -> Unit,
  onSelectMode: (AppThemeMode) -> Unit
) {
  val options = listOf(
    AppThemeMode.SYSTEM to stringResource(R.string.theme_system),
    AppThemeMode.LIGHT to stringResource(R.string.theme_light),
    AppThemeMode.DARK to stringResource(R.string.theme_dark)
  )

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = PeykrTheme.colors.surface,
    title = {
      Text(
        text = stringResource(R.string.theme_select_dialog_title),
        style = MaterialTheme.typography.titleLarge,
        color = PeykrTheme.colors.textPrimary,
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        options.forEach { (mode, label) ->
          val isSelected = mode == selectedMode
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .clickable { onSelectMode(mode) }
              .padding(vertical = 10.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            RadioButton(
              selected = isSelected,
              onClick = { onSelectMode(mode) },
              colors = RadioButtonDefaults.colors(
                selectedColor = PeykrTheme.colors.primaryAccent,
                unselectedColor = PeykrTheme.colors.textSecondary
              )
            )
            Spacer(Modifier.width(10.dp))
            Text(
              text = label,
              color = if (isSelected) PeykrTheme.colors.primaryAccent else PeykrTheme.colors.textPrimary,
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(
          stringResource(R.string.back_action),
          color = PeykrTheme.colors.primaryAccent,
          style = MaterialTheme.typography.labelLarge
        )
      }
    }
  )
}

@Composable
private fun PremiumBottomNavigation(current: NavKey, onNavigate: (NavKey) -> Unit) {
  val navBg = if (PeykrTheme.colors.isDark) Color(0xF20B1220) else Color(0xF8FFFFFF)
  Row(
    Modifier
      .fillMaxWidth()
      .background(navBg)
      .border(0.5.dp, PeykrTheme.colors.border)
      .navigationBarsPadding()
      .height(72.dp)
      .padding(horizontal = 12.dp),
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically
  ) {
    NavigationItem(Main, PremiumIconKind.Home, stringResource(R.string.navigation_home), current == Main, onNavigate)
    NavigationItem(TrustedPeople, PremiumIconKind.People, stringResource(R.string.navigation_people), current == TrustedPeople, onNavigate)
    NavigationItem(Settings, PremiumIconKind.Settings, stringResource(R.string.navigation_settings), current == Settings, onNavigate)
  }
}

@Composable
private fun NavigationItem(key: NavKey, icon: PremiumIconKind, label: String, selected: Boolean, onNavigate: (NavKey) -> Unit) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    TextButton(onClick = { onNavigate(key) }, modifier = Modifier.semantics { contentDescription = label }) {
      Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        PremiumIcon(icon, label, if (selected) PrimaryTurquoise else TextMuted)
        Text(label, color = if (selected) PrimaryTurquoise else TextSecondary, style = MaterialTheme.typography.labelSmall)
      }
    }
  }
}
