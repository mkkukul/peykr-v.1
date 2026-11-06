package com.peykr.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peykr.app.R
import com.peykr.app.core.auth.AuthPromptConfig
import com.peykr.app.core.auth.AuthenticationResult
import com.peykr.app.core.auth.BiometricAvailability
import com.peykr.app.core.auth.PinSecurityManager
import com.peykr.app.core.auth.SecurityGate
import com.peykr.app.core.auth.SecurityGateException
import com.peykr.app.core.auth.SecurityGateScope
import com.peykr.app.core.identity.DeviceIdentity
import com.peykr.app.core.identity.DeviceIdentityState
import com.peykr.app.data.contacts.DatabaseState
import com.peykr.app.domain.contacts.TrustedContactRepository
import com.peykr.app.ui.theme.AppHorizontalPadding
import com.peykr.app.ui.theme.PeykrTheme
import com.peykr.app.ui.theme.PeykrScreenContainer
import com.peykr.app.ui.theme.DarkPanel
import com.peykr.app.ui.theme.DividerDark
import com.peykr.app.ui.theme.Navy950
import com.peykr.app.ui.theme.PremiumIcon
import com.peykr.app.ui.theme.PremiumIconKind
import com.peykr.app.ui.theme.PremiumSurface
import com.peykr.app.ui.theme.PrimaryTurquoise
import com.peykr.app.ui.theme.SectionHeader
import com.peykr.app.ui.theme.TextMuted
import com.peykr.app.ui.theme.TextPrimary
import com.peykr.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
private fun DetailPage(
  onBack: () -> Unit,
  title: String,
  subtitle: String? = null,
  content: @Composable ColumnScope.() -> Unit
) {
  Scaffold(
    containerColor = Color.Transparent,
    contentWindowInsets = WindowInsets.safeDrawing,
  ) { padding ->
    PeykrScreenContainer {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(padding)
          .padding(horizontal = AppHorizontalPadding, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        TextButton(onClick = onBack) {
          Text(
            stringResource(R.string.back_action),
            color = PeykrTheme.colors.primaryAccent,
            fontWeight = FontWeight.SemiBold
          )
        }
        SectionHeader(title = title, supportingText = subtitle)
        content()
      }
    }
  }
}

// -------------------------------------------------------------
// 1. Device Identity Detail Screen
// -------------------------------------------------------------
@Composable
fun DeviceIdentityDetailScreen(
  deviceIdentity: DeviceIdentity?,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val clipboardManager = LocalClipboardManager.current
  var copiedNotice by remember { mutableStateOf(false) }
  val idState = deviceIdentity?.identityState?.collectAsStateWithLifecycle()?.value

  val identityId = (idState as? DeviceIdentityState.Ready)?.identity?.id.orEmpty()
  val isReady = idState is DeviceIdentityState.Ready

  DetailPage(
    onBack = onBack,
    title = stringResource(R.string.detail_identity_title),
    subtitle = stringResource(R.string.detail_identity_desc)
  ) {
    // Status Card
    PremiumSurface(Modifier.fillMaxWidth()) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        PremiumIcon(PremiumIconKind.Device, stringResource(R.string.detail_identity_status_title))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
          Text(stringResource(R.string.detail_identity_status_title), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
          Text(
            if (isReady) stringResource(R.string.identity_status_ready) else stringResource(R.string.identity_status_error),
            color = if (isReady) PrimaryTurquoise else Color(0xFFFF5252),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }
        Box(
          Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(if (isReady) PrimaryTurquoise else Color(0xFFFF5252))
        )
      }
    }

    // Public Device ID Card
    PremiumSurface(Modifier.fillMaxWidth()) {
      Text(stringResource(R.string.detail_identity_id_title), color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(8.dp))
      if (identityId.isNotBlank()) {
        SelectionContainer {
          Text(
            text = formatFingerprint(identityId),
            fontFamily = FontFamily.Monospace,
            color = PrimaryTurquoise,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            modifier = Modifier
              .fillMaxWidth()
              .background(PeykrTheme.colors.background, RoundedCornerShape(8.dp))
              .border(1.dp, PeykrTheme.colors.border, RoundedCornerShape(8.dp))
              .padding(12.dp)
          )
        }
        Spacer(Modifier.height(12.dp))
        Button(
          onClick = {
            clipboardManager.setText(AnnotatedString(identityId))
            copiedNotice = true
          },
          colors = ButtonDefaults.buttonColors(containerColor = PrimaryTurquoise),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            if (copiedNotice) stringResource(R.string.detail_identity_copied_toast) else stringResource(R.string.detail_identity_copy_action),
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold
          )
        }
      } else {
        Text(
          stringResource(R.string.identity_initializing),
          color = TextSecondary,
          style = MaterialTheme.typography.bodyMedium
        )
      }
    }

    // Keystore & Hardware Security Details
    PremiumSurface(Modifier.fillMaxWidth()) {
      Row(verticalAlignment = Alignment.Top) {
        PremiumIcon(PremiumIconKind.Shield, stringResource(R.string.detail_identity_keystore_title))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
          Text(
            stringResource(R.string.detail_identity_keystore_title),
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
          )
          Spacer(Modifier.height(6.dp))

          // Verified Key Security Level
          val secLevelText = when ((idState as? DeviceIdentityState.Ready)?.identity?.securityLevel) {
            com.peykr.app.core.identity.KeySecurityLevel.STRONGBOX -> stringResource(R.string.detail_identity_level_strongbox)
            com.peykr.app.core.identity.KeySecurityLevel.TRUSTED_ENVIRONMENT -> stringResource(R.string.detail_identity_level_tee)
            com.peykr.app.core.identity.KeySecurityLevel.SOFTWARE -> stringResource(R.string.detail_identity_level_software)
            else -> stringResource(R.string.detail_identity_level_unknown)
          }

          Text(
            text = "${stringResource(R.string.detail_identity_level_title)}: $secLevelText",
            color = PrimaryTurquoise,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
          )
          Spacer(Modifier.height(6.dp))
          Text(
            stringResource(R.string.detail_identity_keystore_desc),
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 18.sp
          )
        }
      }
    }
  }
}

// -------------------------------------------------------------
// 2. Biometric Settings Detail Screen
// -------------------------------------------------------------
@Composable
fun BiometricSettingsDetailScreen(
  securityGate: SecurityGate?,
  pinSecurityManager: PinSecurityManager? = null,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val coroutineScope = rememberCoroutineScope()
  var authStatusMessage by remember { mutableStateOf<String?>(null) }

  val sensorAvailability = securityGate?.canAuthenticate() ?: BiometricAvailability.UNSUPPORTED
  val availabilityText = when (sensorAvailability) {
    BiometricAvailability.AVAILABLE -> stringResource(R.string.biometric_status_available)
    BiometricAvailability.NONE_ENROLLED -> stringResource(R.string.biometric_status_none_enrolled)
    BiometricAvailability.NO_HARDWARE -> stringResource(R.string.biometric_status_no_hardware)
    BiometricAvailability.HARDWARE_UNAVAILABLE -> stringResource(R.string.biometric_status_hardware_unavailable)
    BiometricAvailability.SECURITY_UPDATE_REQUIRED -> stringResource(R.string.biometric_status_update_required)
    BiometricAvailability.UNSUPPORTED -> stringResource(R.string.biometric_status_unsupported)
  }

  val promptTitle = stringResource(R.string.biometric_prompt_title)
  val promptDescription = stringResource(R.string.biometric_prompt_description)
  val cancelText = stringResource(R.string.biometric_prompt_cancel)
  val successText = stringResource(R.string.biometric_auth_success)
  val cancelledText = stringResource(R.string.biometric_auth_cancelled)
  val failedText = stringResource(R.string.biometric_auth_failed)

  var isPinSet by remember { mutableStateOf(securityGate?.isPinConfigured() ?: false) }
  var showPinDialog by remember { mutableStateOf(false) }
  var pinFirst by remember { mutableStateOf("") }
  var pinSecond by remember { mutableStateOf("") }
  var pinError by remember { mutableStateOf<String?>(null) }

  if (showPinDialog) {
    AlertDialog(
      onDismissRequest = { showPinDialog = false },
      title = { Text(stringResource(R.string.pin_setup_title), color = TextPrimary) },
      text = {
        Column {
          Text(stringResource(R.string.pin_setup_desc), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
          Spacer(Modifier.height(14.dp))
          OutlinedTextField(
            value = pinFirst,
            onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) pinFirst = it },
            label = { Text(stringResource(R.string.pin_setup_enter_new)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(8.dp))
          OutlinedTextField(
            value = pinSecond,
            onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) pinSecond = it },
            label = { Text(stringResource(R.string.pin_setup_confirm_new)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          if (pinError != null) {
            Spacer(Modifier.height(8.dp))
            Text(pinError!!, color = Color(0xFFFF8888), style = MaterialTheme.typography.bodySmall)
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (pinFirst != pinSecond) {
              pinError = "PINs do not match"
            } else if (pinFirst.length < 4) {
              pinError = "PIN must be at least 4 digits"
            } else {
              pinSecurityManager?.setupPin(pinFirst)?.fold(
                onSuccess = {
                  isPinSet = true
                  showPinDialog = false
                  pinFirst = ""
                  pinSecond = ""
                  pinError = null
                },
                onFailure = {
                  pinError = it.message ?: "Failed to set PIN"
                }
              )
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = PrimaryTurquoise),
        ) {
          Text(stringResource(R.string.pin_setup_save), color = Color.White)
        }
      },
      dismissButton = {
        TextButton(onClick = { showPinDialog = false }) {
          Text(stringResource(R.string.biometric_prompt_cancel), color = PrimaryTurquoise)
        }
      },
      containerColor = Color(0xFF0F1E36),
    )
  }

  DetailPage(
    onBack = onBack,
    title = stringResource(R.string.detail_biometric_title),
    subtitle = stringResource(R.string.detail_biometric_desc)
  ) {
    // Sensor Availability Card
    PremiumSurface(Modifier.fillMaxWidth()) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        PremiumIcon(PremiumIconKind.Device, stringResource(R.string.detail_biometric_sensor_title))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
          Text(stringResource(R.string.detail_biometric_sensor_title), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
          Spacer(Modifier.height(2.dp))
          Text(
            availabilityText,
            color = if (sensorAvailability == BiometricAvailability.AVAILABLE) PrimaryTurquoise else Color(0xFFFFB74D),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
          )
        }
      }
    }

    // Application PIN Configuration Card
    PremiumSurface(Modifier.fillMaxWidth()) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        PremiumIcon(PremiumIconKind.Shield, stringResource(R.string.pin_setup_title))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
          Text(stringResource(R.string.pin_setup_title), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
          Spacer(Modifier.height(2.dp))
          Text(
            if (isPinSet) stringResource(R.string.pin_status_configured) else stringResource(R.string.pin_status_not_configured),
            color = if (isPinSet) PrimaryTurquoise else Color(0xFFFFB74D),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
          )
        }
      }
      Spacer(Modifier.height(12.dp))
      OutlinedButton(
        onClick = {
          pinFirst = ""
          pinSecond = ""
          pinError = null
          showPinDialog = true
        },
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(if (isPinSet) stringResource(R.string.pin_change_title) else stringResource(R.string.pin_setup_save), color = PrimaryTurquoise)
      }
    }

    // Live Test Action
    PremiumSurface(Modifier.fillMaxWidth()) {
      Text(stringResource(R.string.detail_biometric_test_action), color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(6.dp))
      Text(
        stringResource(R.string.biometric_prompt_description),
        color = TextSecondary,
        style = MaterialTheme.typography.bodySmall
      )
      Spacer(Modifier.height(14.dp))
      Button(
        onClick = {
          if (securityGate != null) {
            coroutineScope.launch {
              val result = securityGate.withAuthorization(
                scope = SecurityGateScope.VIEW_SECURITY_DETAILS,
                config = AuthPromptConfig(
                  title = promptTitle,
                  description = promptDescription,
                  negativeButtonText = cancelText,
                  allowDeviceCredential = true
                )
              ) {
                successText
              }
              authStatusMessage = result.fold(
                onSuccess = { it },
                onFailure = { error ->
                  if (error is SecurityGateException.AuthenticationDenied && error.result is AuthenticationResult.UserCancellation) {
                    cancelledText
                  } else {
                    failedText
                  }
                }
              )
            }
          }
        },
        enabled = sensorAvailability == BiometricAvailability.AVAILABLE,
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTurquoise),
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(stringResource(R.string.detail_biometric_test_action), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
      }

      if (authStatusMessage != null) {
        Spacer(Modifier.height(10.dp))
        Text(
          text = "${stringResource(R.string.detail_biometric_test_result_title)}: $authStatusMessage",
          color = PrimaryTurquoise,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium
        )
      }
    }

    // Gate Architecture
    PremiumSurface(Modifier.fillMaxWidth()) {
      Row(verticalAlignment = Alignment.Top) {
        PremiumIcon(PremiumIconKind.Shield, "SecurityGate")
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
          Text(
            "SecurityGate",
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
          )
          Spacer(Modifier.height(6.dp))
          Text(
            stringResource(R.string.detail_biometric_gate_desc),
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 18.sp
          )
        }
      }
    }
  }
}

// -------------------------------------------------------------
// 3. Location Sharing Detail Screen
// -------------------------------------------------------------
@Composable
fun LocationSharingDetailScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var hasPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    )
  }
  var isSharingEnabled by remember { mutableStateOf(false) }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
    val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
    hasPermission = fineGranted || coarseGranted
    if (!hasPermission) {
      isSharingEnabled = false
    }
  }

  DetailPage(
    onBack = onBack,
    title = stringResource(R.string.detail_location_title),
    subtitle = stringResource(R.string.detail_location_desc)
  ) {
    // Zero-Cloud Architecture Card
    PremiumSurface(Modifier.fillMaxWidth()) {
      Row(verticalAlignment = Alignment.Top) {
        PremiumIcon(PremiumIconKind.Location, stringResource(R.string.detail_location_zero_cloud_title))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
          Text(
            stringResource(R.string.detail_location_zero_cloud_title),
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
          )
          Spacer(Modifier.height(6.dp))
          Text(
            stringResource(R.string.detail_location_zero_cloud_desc),
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 18.sp
          )
        }
      }
    }

    // Permission Check
    PremiumSurface(Modifier.fillMaxWidth()) {
      Text(stringResource(R.string.detail_location_permission_title), color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(4.dp))
      Text(
        if (hasPermission) stringResource(R.string.detail_location_permission_granted) else stringResource(R.string.detail_location_permission_needed),
        color = if (hasPermission) PrimaryTurquoise else Color(0xFFFFB74D),
        style = MaterialTheme.typography.bodyMedium
      )
      if (!hasPermission) {
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
          onClick = {
            permissionLauncher.launch(
              arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
              )
            )
          },
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(stringResource(R.string.detail_location_permission_action), color = PrimaryTurquoise)
        }
      }
    }

    // Toggle Sharing
    PremiumSurface(Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(Modifier.weight(1f)) {
          Text(stringResource(R.string.detail_location_toggle_title), color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
          Spacer(Modifier.height(2.dp))
          Text(
            if (isSharingEnabled && hasPermission) stringResource(R.string.detail_location_toggle_active) else stringResource(R.string.detail_location_toggle_inactive),
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
          )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
          checked = isSharingEnabled && hasPermission,
          onCheckedChange = { checked ->
            if (checked && !hasPermission) {
              permissionLauncher.launch(
                arrayOf(
                  Manifest.permission.ACCESS_FINE_LOCATION,
                  Manifest.permission.ACCESS_COARSE_LOCATION
                )
              )
            } else {
              isSharingEnabled = checked
            }
          },
          colors = SwitchDefaults.colors(
            checkedThumbColor = PeykrTheme.colors.onPrimary,
            checkedTrackColor = PeykrTheme.colors.primaryAccent,
            uncheckedThumbColor = PeykrTheme.colors.textSecondary,
            uncheckedTrackColor = PeykrTheme.colors.border
          )
        )
      }
      Spacer(Modifier.height(8.dp))
      Text(
        text = if (isSharingEnabled && hasPermission) {
          stringResource(R.string.detail_location_notice_active)
        } else {
          stringResource(R.string.detail_location_notice_inactive)
        },
        color = TextMuted,
        style = MaterialTheme.typography.labelSmall,
        lineHeight = 16.sp
      )
    }
  }
}

// -------------------------------------------------------------
// 4. Data Backup Detail Screen
// -------------------------------------------------------------
@Composable
fun DataBackupDetailScreen(
  contactRepository: TrustedContactRepository,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val dbState by contactRepository.initializationState.collectAsStateWithLifecycle(DatabaseState.INITIALIZING)
  val isDbReady = dbState == DatabaseState.READY

  DetailPage(
    onBack = onBack,
    title = stringResource(R.string.detail_backup_title),
    subtitle = stringResource(R.string.detail_backup_desc)
  ) {
    // Database Health Card
    PremiumSurface(Modifier.fillMaxWidth()) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        PremiumIcon(PremiumIconKind.Backup, stringResource(R.string.detail_backup_db_title))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
          Text(stringResource(R.string.detail_backup_db_title), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
          Text(
            if (isDbReady) stringResource(R.string.system_status_ready) else stringResource(R.string.database_error_status),
            color = if (isDbReady) PrimaryTurquoise else Color(0xFFFF5252),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }
        Box(
          Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(if (isDbReady) PrimaryTurquoise else Color(0xFFFF5252))
        )
      }
    }

    // SQLCipher Description
    PremiumSurface(Modifier.fillMaxWidth()) {
      Text(stringResource(R.string.detail_backup_db_title), color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(6.dp))
      Text(stringResource(R.string.detail_backup_db_desc), color = TextSecondary, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
    }

    // Key Wrapping Description
    PremiumSurface(Modifier.fillMaxWidth()) {
      Text(stringResource(R.string.detail_backup_wrap_title), color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(6.dp))
      Text(stringResource(R.string.detail_backup_wrap_desc), color = TextSecondary, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
    }

    // Zero-Cloud No-Backup Notice
    PremiumSurface(Modifier.fillMaxWidth()) {
      Text(stringResource(R.string.detail_backup_zero_cloud_title), color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(6.dp))
      Text(stringResource(R.string.detail_backup_zero_cloud_desc), color = TextSecondary, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
    }

    // Verify Action
    Button(
      onClick = { contactRepository.retry() },
      colors = ButtonDefaults.buttonColors(containerColor = PrimaryTurquoise),
      modifier = Modifier.fillMaxWidth()
    ) {
      Text(stringResource(R.string.detail_backup_verify_action), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
    }
  }
}

// -------------------------------------------------------------
// 5. About Detail Screen
// -------------------------------------------------------------
@Composable
fun AboutDetailScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  DetailPage(
    onBack = onBack,
    title = stringResource(R.string.detail_about_title),
    subtitle = stringResource(R.string.detail_about_desc)
  ) {
    // App Brand Card
    PremiumSurface(Modifier.fillMaxWidth()) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(PeykrTheme.colors.primarySubtle),
          contentAlignment = Alignment.Center
        ) {
          PremiumIcon(
            kind = PremiumIconKind.Shield,
            description = "Peykr",
            tint = PeykrTheme.colors.primaryAccent
          )
        }
        Spacer(Modifier.width(14.dp))
        Column {
          Text("Peykr", color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
          Text(stringResource(R.string.prototype_version_detail), color = PrimaryTurquoise, style = MaterialTheme.typography.labelMedium)
        }
      }
      Spacer(Modifier.height(10.dp))
      Text(stringResource(R.string.detail_about_brand_desc), color = TextSecondary, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
    }

    // Technical Specifications
    PremiumSurface(Modifier.fillMaxWidth()) {
      Text(stringResource(R.string.detail_about_specs_title), color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(8.dp))
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("• " + stringResource(R.string.detail_about_spec_crypto), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        Text("• " + stringResource(R.string.detail_about_spec_storage), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        Text("• " + stringResource(R.string.detail_about_spec_auth), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        Text("• " + stringResource(R.string.detail_about_spec_transport), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}

private fun formatFingerprint(raw: String): String {
  if (raw.isBlank()) return raw
  return raw.chunked(4).chunked(4).joinToString("\n") { it.joinToString(" ") }
}
