package com.peykr.app.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.peykr.app.Main
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
import com.peykr.app.domain.contacts.PairingStatus
import com.peykr.app.domain.contacts.TrustedContactRepository
import com.peykr.app.localization.AppLanguage
import com.peykr.app.ui.theme.AppHorizontalPadding
import com.peykr.app.ui.theme.Navy950
import com.peykr.app.ui.theme.PremiumIcon
import com.peykr.app.ui.theme.PremiumIconKind
import com.peykr.app.ui.theme.PremiumInfoRow
import com.peykr.app.ui.theme.PremiumSurface
import com.peykr.app.ui.theme.PrimaryTurquoise
import com.peykr.app.ui.theme.SectionHeader
import com.peykr.app.ui.theme.TextMuted
import com.peykr.app.ui.theme.TextPrimary
import com.peykr.app.ui.theme.TextSecondary
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
  val peopleSummary = when (val uiState = state) {
    is MainScreenUiState.Success -> stringResource(R.string.trusted_people_count, uiState.data.size)
    is MainScreenUiState.DatabaseError -> stringResource(R.string.database_error_status)
    is MainScreenUiState.IdentityError -> stringResource(R.string.identity_error_status)
    is MainScreenUiState.Loading -> stringResource(R.string.database_loading)
    is MainScreenUiState.Error -> stringResource(R.string.database_error_status)
  }
  val systemStatusText = when (state) {
    is MainScreenUiState.Success -> stringResource(R.string.system_status_ready)
    is MainScreenUiState.Loading -> stringResource(R.string.system_status_initializing)
    is MainScreenUiState.DatabaseError,
    is MainScreenUiState.IdentityError,
    is MainScreenUiState.Error -> stringResource(R.string.system_status_error)
  }
  val systemStatusColor = when (state) {
    is MainScreenUiState.Success -> PrimaryTurquoise
    is MainScreenUiState.Loading -> TextSecondary
    is MainScreenUiState.DatabaseError,
    is MainScreenUiState.IdentityError,
    is MainScreenUiState.Error -> Color(0xFFFF5252)
  }

  Scaffold(
    modifier,
    containerColor = Navy950,
    contentWindowInsets = WindowInsets.safeDrawing,
    bottomBar = { PremiumBottomNavigation(Main, onNavigate) }
  ) { padding ->
    Page(Modifier.padding(padding)) {
      HomeBrand()
      FocusEmblem()
      Text(stringResource(R.string.home_headline), color = TextPrimary, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
      Text(stringResource(R.string.home_description), color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
      PremiumSurface(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          PremiumIcon(PremiumIconKind.Backup, stringResource(R.string.system_status))
          Spacer(Modifier.width(14.dp))
          Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.system_status), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Text(systemStatusText, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
          }
          Box(Modifier.size(9.dp).clip(CircleShape).background(systemStatusColor))
        }
      }
      if (state is MainScreenUiState.IdentityError) {
        val identityError = state as MainScreenUiState.IdentityError
        PremiumSurface(Modifier.fillMaxWidth()) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            PremiumIcon(PremiumIconKind.Shield, stringResource(R.string.identity_error_title))
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
      PremiumInfoRow(PremiumIconKind.People, stringResource(R.string.trusted_people), peopleSummary, stringResource(R.string.trusted_people), onPeopleClick)
      PremiumInfoRow(PremiumIconKind.Location, stringResource(R.string.location_sharing), stringResource(R.string.location_off), stringResource(R.string.location_sharing), onSettingsClick)
    }
  }
}

@Composable private fun Page(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) { Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = AppHorizontalPadding).padding(top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
@Composable private fun HomeBrand() { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { PremiumIcon(PremiumIconKind.Shield, "PEYKR"); Spacer(Modifier.width(12.dp)); Column { Text("PEYKR", color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(stringResource(R.string.home_kicker), color = TextSecondary, style = MaterialTheme.typography.bodySmall) }; Spacer(Modifier.weight(1f)); Text(stringResource(R.string.prototype_label), color = PrimaryTurquoise, style = MaterialTheme.typography.labelMedium) } }
@Composable private fun FocusEmblem() { Box(Modifier.fillMaxWidth().height(124.dp), contentAlignment = Alignment.Center) { Canvas(Modifier.size(112.dp).semantics { contentDescription = "PEYKR güvenlik sembolü" }) { val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f); drawCircle(Color(0x1834D9FF), 48.dp.toPx()); drawCircle(PrimaryTurquoise, 32.dp.toPx(), style = Stroke(2.dp.toPx())); drawArc(Brush.sweepGradient(listOf(PrimaryTurquoise, Color.Transparent)), 210f, 240f, false, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)); drawCircle(PrimaryTurquoise, 3.dp.toPx(), center); drawLine(PrimaryTurquoise, center.copy(y = center.y + 3.dp.toPx()), center.copy(y = center.y + 13.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round); drawLine(PrimaryTurquoise, center.copy(y = center.y + 3.dp.toPx()), center.copy(y = center.y - 7.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round) } } }

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
  Scaffold(modifier, containerColor = Navy950, contentWindowInsets = WindowInsets.safeDrawing, bottomBar = { PremiumBottomNavigation(TrustedPeople, onNavigate) }) { padding -> Page(Modifier.padding(padding)) {
    BackAction(onBack); SectionHeader(stringResource(R.string.trusted_people), stringResource(R.string.trusted_people_description))
    when (val uiState = state) {
      is MainScreenUiState.Loading -> {
        Text(stringResource(R.string.database_loading), color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
      }
      is MainScreenUiState.IdentityError -> {
        PremiumSurface(Modifier.fillMaxWidth()) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            PremiumIcon(PremiumIconKind.Shield, stringResource(R.string.identity_error_title))
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
            PremiumIcon(PremiumIconKind.Shield, stringResource(R.string.database_error_title))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
              Text(stringResource(R.string.database_error_title), color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
              Spacer(Modifier.height(4.dp))
              Text(stringResource(R.string.database_error_description), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
          }
          Spacer(Modifier.height(12.dp))
          TextButton(onClick = { actualViewModel.retry() }, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.database_error_retry), color = PrimaryTurquoise, style = MaterialTheme.typography.labelLarge)
          }
        }
      }
      is MainScreenUiState.Error -> {
        PremiumSurface(Modifier.fillMaxWidth()) {
          Text(stringResource(R.string.database_error_title), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
          Spacer(Modifier.height(4.dp))
          Text(stringResource(R.string.database_error_description), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
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
          Text(stringResource(R.string.trusted_people_empty_title), color = TextPrimary, style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.CenterHorizontally))
          Text(stringResource(R.string.trusted_people_empty_description), color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
          PremiumSurface(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              PremiumIcon(PremiumIconKind.Info, stringResource(R.string.trusted_people_privacy_note))
              Spacer(Modifier.width(12.dp))
              Text(stringResource(R.string.trusted_people_privacy_note), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
          }
        } else {
          people.forEach { person ->
            PremiumInfoRow(PremiumIconKind.People, person.displayName, pairingLabel(person.pairingStatus), person.displayName)
          }
        }
      }
    }
  } }
}

@Composable private fun pairingLabel(status: PairingStatus): String = stringResource(when (status) { PairingStatus.NOT_PAIRED -> R.string.pairing_not_paired; PairingStatus.PENDING -> R.string.pairing_pending; PairingStatus.PAIRED -> R.string.pairing_paired; PairingStatus.REVOKED -> R.string.pairing_revoked })

@Composable private fun TrustIllustration() { Canvas(Modifier.fillMaxWidth().height(124.dp).semantics { contentDescription = "Güvenilir kişiler sembolü" }) { val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f); drawOval(PrimaryTurquoise, androidx.compose.ui.geometry.Offset(center.x - 68.dp.toPx(), center.y - 16.dp.toPx()), androidx.compose.ui.geometry.Size(136.dp.toPx(), 58.dp.toPx()), style = Stroke(1.5.dp.toPx())); drawCircle(PrimaryTurquoise, 13.dp.toPx(), center.copy(x = center.x - 20.dp.toPx(), y = center.y - 18.dp.toPx())); drawCircle(PrimaryTurquoise, 11.dp.toPx(), center.copy(x = center.x + 20.dp.toPx(), y = center.y - 14.dp.toPx())); drawArc(PrimaryTurquoise, 200f, 140f, false, androidx.compose.ui.geometry.Offset(center.x - 42.dp.toPx(), center.y - 3.dp.toPx()), androidx.compose.ui.geometry.Size(44.dp.toPx(), 40.dp.toPx()), style = Stroke(2.dp.toPx())); drawArc(PrimaryTurquoise, 200f, 140f, false, androidx.compose.ui.geometry.Offset(center.x - 2.dp.toPx(), center.y + 2.dp.toPx()), androidx.compose.ui.geometry.Size(40.dp.toPx(), 36.dp.toPx()), style = Stroke(2.dp.toPx())); drawCircle(Navy950, 21.dp.toPx(), center.copy(x = center.x + 56.dp.toPx(), y = center.y + 16.dp.toPx())); drawCircle(PrimaryTurquoise, 18.dp.toPx(), center.copy(x = center.x + 56.dp.toPx(), y = center.y + 16.dp.toPx()), style = Stroke(2.dp.toPx())) } }

@Composable
fun SettingsScreen(
  onNavigate: (NavKey) -> Unit,
  onBack: () -> Unit,
  selectedLanguage: AppLanguage,
  onLanguageSelected: (AppLanguage) -> Unit,
  modifier: Modifier = Modifier,
  deviceIdentity: DeviceIdentity? = null,
  securityGate: SecurityGate? = null,
) {
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

  var authTestResultText by remember { mutableStateOf<String?>(null) }
  val coroutineScope = rememberCoroutineScope()
  val promptTitle = stringResource(R.string.biometric_prompt_title)
  val promptDescription = stringResource(R.string.biometric_prompt_description)
  val negativeText = stringResource(R.string.biometric_prompt_cancel)
  val authSuccessText = stringResource(R.string.biometric_auth_success)
  val authFailedText = stringResource(R.string.biometric_auth_failed)
  val authCancelledText = stringResource(R.string.biometric_auth_cancelled)

  Scaffold(modifier, containerColor = Navy950, contentWindowInsets = WindowInsets.safeDrawing, bottomBar = { PremiumBottomNavigation(Settings, onNavigate) }) { padding -> Page(Modifier.padding(padding)) {
    SectionHeader(stringResource(R.string.settings_title), stringResource(R.string.settings_description))
    SettingsGroup(stringResource(R.string.appearance_section)) { PremiumInfoRow(PremiumIconKind.Theme, stringResource(R.string.theme_setting), stringResource(R.string.theme_detail), stringResource(R.string.theme_setting)) }
    SettingsGroup(stringResource(R.string.language_section)) { PremiumInfoRow(PremiumIconKind.Language, stringResource(R.string.language_setting), languageSummary(selectedLanguage), stringResource(R.string.language_setting), trailing = { LanguageChoice(selectedLanguage, onLanguageSelected) }) }
    SettingsGroup(stringResource(R.string.privacy_section)) {
      PremiumInfoRow(PremiumIconKind.Location, stringResource(R.string.location_sharing), stringResource(R.string.location_detail), stringResource(R.string.location_sharing))
      PremiumInfoRow(PremiumIconKind.Backup, stringResource(R.string.backup_setting), stringResource(R.string.backup_detail), stringResource(R.string.backup_setting))
      PremiumInfoRow(PremiumIconKind.Device, stringResource(R.string.identity_setting), identitySummary, stringResource(R.string.identity_setting))
    }
    SettingsGroup(stringResource(R.string.security_section)) {
      PremiumInfoRow(
        PremiumIconKind.Device,
        stringResource(R.string.biometric_setting_title),
        authTestResultText ?: biometricStatus,
        stringResource(R.string.biometric_setting_title),
        onClick = {
          if (securityGate != null && securityGate.canAuthenticate() == BiometricAvailability.AVAILABLE) {
            coroutineScope.launch {
              val result = securityGate.withAuthorization(
                scope = SecurityGateScope.VIEW_SECURITY_DETAILS,
                config = AuthPromptConfig(
                  title = promptTitle,
                  description = promptDescription,
                  negativeButtonText = negativeText,
                  allowDeviceCredential = true,
                )
              ) {
                authSuccessText
              }
              authTestResultText = result.fold(
                onSuccess = { it },
                onFailure = { error ->
                  if (error is SecurityGateException.AuthenticationDenied && error.result is AuthenticationResult.UserCancellation) {
                    authCancelledText
                  } else {
                    authFailedText
                  }
                }
              )
            }
          }
        }
      )
      PremiumInfoRow(PremiumIconKind.Device, stringResource(R.string.device_verification), stringResource(R.string.device_verification_detail), stringResource(R.string.device_verification))
    }
    SettingsGroup(stringResource(R.string.about_section)) { PremiumInfoRow(PremiumIconKind.Info, stringResource(R.string.prototype_version), stringResource(R.string.prototype_version_detail), stringResource(R.string.prototype_version)) }
  } }
}

@Composable private fun SettingsGroup(title: String, content: @Composable () -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, color = TextSecondary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp)); content() } }
@Composable private fun languageSummary(language: AppLanguage): String = stringResource(if (language == AppLanguage.TURKISH) R.string.language_turkish else R.string.language_english)
@Composable
private fun LanguageChoice(selected: AppLanguage, onSelected: (AppLanguage) -> Unit) {
  TextButton(onClick = { onSelected(if (selected == AppLanguage.TURKISH) AppLanguage.ENGLISH else AppLanguage.TURKISH) }) {
    Text(stringResource(if (selected == AppLanguage.TURKISH) R.string.language_turkish else R.string.language_english), color = PrimaryTurquoise, style = MaterialTheme.typography.labelMedium)
  }
}
@Composable private fun BackAction(onBack: () -> Unit) { TextButton(onClick = onBack) { Text(stringResource(R.string.back_action), color = PrimaryTurquoise) } }
@Composable private fun PremiumBottomNavigation(current: NavKey, onNavigate: (NavKey) -> Unit) { Row(Modifier.fillMaxWidth().background(Color(0xF20B1220)).navigationBarsPadding().height(76.dp).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { NavigationItem(Main, PremiumIconKind.Home, stringResource(R.string.navigation_home), current == Main, onNavigate); NavigationItem(TrustedPeople, PremiumIconKind.People, stringResource(R.string.navigation_people), current == TrustedPeople, onNavigate); NavigationItem(Settings, PremiumIconKind.Settings, stringResource(R.string.navigation_settings), current == Settings, onNavigate) } }
@Composable private fun NavigationItem(key: NavKey, icon: PremiumIconKind, label: String, selected: Boolean, onNavigate: (NavKey) -> Unit) { Column(horizontalAlignment = Alignment.CenterHorizontally) { TextButton(onClick = { onNavigate(key) }, modifier = Modifier.semantics { contentDescription = label }) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) { PremiumIcon(icon, label, if (selected) PrimaryTurquoise else TextMuted); Text(label, color = if (selected) PrimaryTurquoise else TextSecondary, style = MaterialTheme.typography.labelSmall) } } } }
