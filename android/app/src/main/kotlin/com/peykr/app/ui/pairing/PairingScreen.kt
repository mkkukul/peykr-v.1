package com.peykr.app.ui.pairing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.peykr.app.R
import com.peykr.app.core.auth.RequiredAuthMethod
import com.peykr.app.core.auth.SecurityGate
import com.peykr.app.core.identity.DeviceIdentity
import com.peykr.app.domain.pairing.PairingEngine
import com.peykr.app.ui.pairing.qr.QrMatrix
import com.peykr.app.ui.pairing.scanner.CameraScannerView
import com.peykr.app.ui.theme.Navy950
import com.peykr.app.ui.theme.PrimaryTurquoise
import com.peykr.app.ui.theme.TextPrimary
import com.peykr.app.ui.theme.TextSecondary

@Composable
fun PairingScreen(
  onBack: () -> Unit,
  pairingEngine: PairingEngine,
  securityGate: SecurityGate,
  deviceIdentity: DeviceIdentity? = null,
  viewModel: PairingViewModel? = null,
  modifier: Modifier = Modifier,
) {
  val actualViewModel = viewModel ?: viewModel {
    PairingViewModel(pairingEngine, securityGate, deviceIdentity)
  }
  val state by actualViewModel.uiState.collectAsStateWithLifecycle()

  androidx.activity.compose.BackHandler {
    if (state is PairingUiState.RoleSelection || state is PairingUiState.Success || state is PairingUiState.Error) {
      onBack()
    } else {
      actualViewModel.cancelPairing()
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = Navy950,
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 24.dp, vertical = 16.dp)
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(onClick = {
          if (state is PairingUiState.RoleSelection || state is PairingUiState.Success) {
            onBack()
          } else {
            actualViewModel.cancelPairing()
          }
        }) {
          Text(
            text = if (state is PairingUiState.Success) stringResource(R.string.pairing_action_done) else stringResource(R.string.pairing_action_cancel),
            color = PrimaryTurquoise,
            style = MaterialTheme.typography.labelLarge,
          )
        }
        Spacer(Modifier.weight(1f))
        Text(
          text = stringResource(R.string.pairing_title),
          color = TextPrimary,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(48.dp))
      }

      Spacer(Modifier.height(16.dp))

      when (val current = state) {
        is PairingUiState.RoleSelection -> {
          RoleSelectionContent(
            onStartInitiator = { actualViewModel.startAsInitiator() },
            onStartResponder = { actualViewModel.startAsResponder() },
          )
        }

        is PairingUiState.InitiatorShowInitialQr -> {
          QrDisplayContent(
            title = stringResource(R.string.pairing_step1_init_title),
            instruction = stringResource(R.string.pairing_step1_init_desc),
            matrix = current.qrMatrix,
            buttonText = stringResource(R.string.pairing_action_next_scan_request),
            onButtonClick = { actualViewModel.proceedToScanResponderRequest() },
          )
        }

        is PairingUiState.InitiatorScanRequest -> {
          ScannerContent(
            title = stringResource(R.string.pairing_step2_init_title),
            instruction = stringResource(R.string.pairing_step2_init_desc),
            errorMessage = current.errorMessage,
            onScanned = { actualViewModel.onInitiatorScannedRequestQr(it) },
          )
        }

        is PairingUiState.InitiatorReviewAndApprove -> {
          ReviewAndApproveContent(
            remoteName = current.remoteName,
            remoteFingerprint = current.remoteFingerprint,
            isAuthenticating = current.isAuthenticating,
            errorMessage = current.errorMessage,
            authMethod = current.authMethod,
            showPinDialog = current.showPinDialog,
            onApprove = { actualViewModel.approveInitiatorRequest() },
            onSubmitPin = { actualViewModel.submitPinForInitiatorApproval(it) },
            onDismissPin = { actualViewModel.dismissPinDialog() },
          )
        }

        is PairingUiState.InitiatorShowResponseQr -> {
          QrDisplayContent(
            title = stringResource(R.string.pairing_step3_init_title),
            instruction = stringResource(R.string.pairing_step3_init_desc),
            matrix = current.qrMatrix,
            buttonText = stringResource(R.string.pairing_action_next_scan_confirm),
            onButtonClick = { actualViewModel.proceedToScanFinalConfirmation() },
          )
        }

        is PairingUiState.InitiatorScanConfirmation -> {
          ScannerContent(
            title = stringResource(R.string.pairing_step4_init_title),
            instruction = stringResource(R.string.pairing_step4_init_desc),
            errorMessage = current.errorMessage,
            onScanned = { actualViewModel.onInitiatorScannedConfirmationQr(it) },
          )
        }

        is PairingUiState.ResponderScanInitialQr -> {
          ScannerContent(
            title = stringResource(R.string.pairing_step1_resp_title),
            instruction = stringResource(R.string.pairing_step1_resp_desc),
            errorMessage = current.errorMessage,
            onScanned = { actualViewModel.onResponderScannedInitialQr(it) },
          )
        }

        is PairingUiState.ResponderReviewAndApprove -> {
          ReviewAndApproveContent(
            remoteName = current.remoteName,
            remoteFingerprint = current.remoteFingerprint,
            isAuthenticating = current.isAuthenticating,
            errorMessage = current.errorMessage,
            authMethod = current.authMethod,
            showPinDialog = current.showPinDialog,
            onApprove = { actualViewModel.approveResponderRequest() },
            onSubmitPin = { actualViewModel.submitPinForResponderApproval(it) },
            onDismissPin = { actualViewModel.dismissPinDialog() },
          )
        }

        is PairingUiState.ResponderShowRequestQr -> {
          QrDisplayContent(
            title = stringResource(R.string.pairing_step2_resp_title),
            instruction = stringResource(R.string.pairing_step2_resp_desc),
            matrix = current.qrMatrix,
            buttonText = stringResource(R.string.pairing_action_next_scan_response),
            onButtonClick = { actualViewModel.proceedToScanInitiatorResponse() },
          )
        }

        is PairingUiState.ResponderScanResponse -> {
          ScannerContent(
            title = stringResource(R.string.pairing_step3_resp_title),
            instruction = stringResource(R.string.pairing_step3_resp_desc),
            errorMessage = current.errorMessage,
            onScanned = { actualViewModel.onResponderScannedResponseQr(it) },
          )
        }

        is PairingUiState.ResponderShowConfirmationQr -> {
          QrDisplayContent(
            title = stringResource(R.string.pairing_step4_resp_title),
            instruction = stringResource(R.string.pairing_step4_resp_desc),
            matrix = current.qrMatrix,
            buttonText = stringResource(R.string.pairing_action_done),
            onButtonClick = { actualViewModel.finishResponderFlow() },
          )
        }

        is PairingUiState.Success -> {
          SuccessContent(
            remoteName = current.remoteName,
            remoteFingerprint = current.remoteFingerprint,
            onFinish = onBack,
          )
        }

        is PairingUiState.Error -> {
          ErrorContent(
            message = current.message,
            canRetry = current.canRetry,
            onRetry = { actualViewModel.resetToRoleSelection() },
            onCancel = onBack,
          )
        }
      }
    }
  }
}

@Composable
fun QrCodeView(
  matrix: QrMatrix,
  modifier: Modifier = Modifier,
  foregroundColor: Color = Color.Black,
  backgroundColor: Color = Color.White,
) {
  Canvas(modifier = modifier) {
    drawRect(backgroundColor)
    val cellWidth = size.width / matrix.width
    val cellHeight = size.height / matrix.height
    val pixelPad = 0.5f
    for (y in 0 until matrix.height) {
      for (x in 0 until matrix.width) {
        if (matrix.get(x, y)) {
          drawRect(
            color = foregroundColor,
            topLeft = Offset(x * cellWidth, y * cellHeight),
            size = Size(cellWidth + pixelPad, cellHeight + pixelPad),
          )
        }
      }
    }
  }
}

@Composable
private fun RoleSelectionContent(
  onStartInitiator: () -> Unit,
  onStartResponder: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = stringResource(R.string.pairing_select_mode_title),
      color = TextPrimary,
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
      text = stringResource(R.string.pairing_select_mode_desc),
      color = TextSecondary,
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(32.dp))

    Button(
      onClick = onStartInitiator,
      modifier = Modifier.fillMaxWidth().height(52.dp),
      colors = ButtonDefaults.buttonColors(containerColor = PrimaryTurquoise),
    ) {
      Text(stringResource(R.string.pairing_mode_initiator), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
    }

    Spacer(Modifier.height(16.dp))

    OutlinedButton(
      onClick = onStartResponder,
      modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
      Text(stringResource(R.string.pairing_mode_responder), color = PrimaryTurquoise, fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
private fun QrDisplayContent(
  title: String,
  instruction: String,
  matrix: QrMatrix,
  buttonText: String,
  onButtonClick: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(instruction, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))

    Box(
      modifier = Modifier
        .size(280.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(Color.White)
        .padding(16.dp),
      contentAlignment = Alignment.Center,
    ) {
      QrCodeView(
        matrix = matrix,
        modifier = Modifier.fillMaxSize(),
      )
    }

    Spacer(Modifier.height(28.dp))

    Button(
      onClick = onButtonClick,
      modifier = Modifier.fillMaxWidth().height(50.dp),
      colors = ButtonDefaults.buttonColors(containerColor = PrimaryTurquoise),
    ) {
      Text(buttonText, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
private fun ScannerContent(
  title: String,
  instruction: String,
  errorMessage: String?,
  onScanned: (String) -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(instruction, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))

    if (errorMessage != null) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color(0x33FF5555), RoundedCornerShape(8.dp))
          .padding(12.dp),
      ) {
        Text(errorMessage, color = Color(0xFFFF8888), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
      }
      Spacer(Modifier.height(12.dp))
    }

    CameraScannerView(
      modifier = Modifier.fillMaxWidth().height(320.dp),
      onQrCodeScanned = onScanned,
    )
  }
}

@Composable
private fun ReviewAndApproveContent(
  remoteName: String,
  remoteFingerprint: String,
  isAuthenticating: Boolean,
  errorMessage: String?,
  authMethod: RequiredAuthMethod = RequiredAuthMethod.BIOMETRIC_OR_PIN,
  showPinDialog: Boolean = false,
  onApprove: () -> Unit,
  onSubmitPin: (String) -> Unit = {},
  onDismissPin: () -> Unit = {},
) {
  var pinInput by remember { mutableStateOf("") }

  if (showPinDialog) {
    AlertDialog(
      onDismissRequest = onDismissPin,
      title = { Text(stringResource(R.string.pin_dialog_title), color = TextPrimary) },
      text = {
        Column {
          Text(
            stringResource(R.string.pin_dialog_desc, remoteName),
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
          )
          Spacer(Modifier.height(16.dp))
          OutlinedTextField(
            value = pinInput,
            onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) pinInput = it },
            label = { Text("PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (pinInput.isNotBlank()) {
              onSubmitPin(pinInput)
              pinInput = ""
            }
          },
          enabled = pinInput.length >= 4 && !isAuthenticating,
          colors = ButtonDefaults.buttonColors(containerColor = PrimaryTurquoise),
        ) {
          if (isAuthenticating) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
          } else {
            Text(stringResource(R.string.pin_dialog_confirm), color = Color.White)
          }
        }
      },
      dismissButton = {
        TextButton(onClick = onDismissPin) {
          Text(stringResource(R.string.pairing_action_cancel), color = PrimaryTurquoise)
        }
      },
      containerColor = Color(0xFF0F1E36),
    )
  }

  Column(
    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(stringResource(R.string.pairing_review_title), color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.pairing_review_desc), color = TextSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF0F1E36), RoundedCornerShape(12.dp))
        .border(1.dp, Color(0xFF1E3A5F), RoundedCornerShape(12.dp))
        .padding(16.dp),
    ) {
      Column {
        Text(stringResource(R.string.pairing_review_device_name), color = TextSecondary, style = MaterialTheme.typography.labelMedium)
        Text(remoteName, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.pairing_review_fingerprint), color = TextSecondary, style = MaterialTheme.typography.labelMedium)
        Text(remoteFingerprint, color = PrimaryTurquoise, style = MaterialTheme.typography.bodyMedium)
      }
    }

    Spacer(Modifier.height(24.dp))

    if (errorMessage != null) {
      Text(errorMessage, color = Color(0xFFFF8888), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
      Spacer(Modifier.height(12.dp))
    }

    val buttonLabel = when (authMethod) {
      RequiredAuthMethod.PIN_REQUIRED -> stringResource(R.string.pairing_action_enter_pin)
      RequiredAuthMethod.SETUP_PIN_REQUIRED -> stringResource(R.string.pairing_action_setup_pin)
      RequiredAuthMethod.BIOMETRIC_OR_PIN -> stringResource(R.string.pairing_review_action_approve)
    }

    Button(
      onClick = onApprove,
      enabled = !isAuthenticating,
      modifier = Modifier.fillMaxWidth().height(52.dp),
      colors = ButtonDefaults.buttonColors(containerColor = PrimaryTurquoise),
    ) {
      if (isAuthenticating) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
      } else {
        Text(buttonLabel, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
      }
    }

    if (authMethod == RequiredAuthMethod.BIOMETRIC_OR_PIN) {
      Spacer(Modifier.height(8.dp))
      TextButton(onClick = { onApprove() /* will trigger fallback */ }) {
        Text(stringResource(R.string.pairing_action_use_pin_fallback), color = PrimaryTurquoise)
      }
    }
  }
}

@Composable
private fun SuccessContent(
  remoteName: String,
  remoteFingerprint: String,
  onFinish: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text("✓", color = PrimaryTurquoise, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.pairing_success_title), color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(
      text = stringResource(R.string.pairing_success_desc, remoteName),
      color = TextSecondary,
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    Text(remoteFingerprint, color = PrimaryTurquoise, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(32.dp))

    Button(
      onClick = onFinish,
      modifier = Modifier.fillMaxWidth().height(50.dp),
      colors = ButtonDefaults.buttonColors(containerColor = PrimaryTurquoise),
    ) {
      Text(stringResource(R.string.pairing_action_return_contacts), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
private fun ErrorContent(
  message: String,
  canRetry: Boolean,
  onRetry: () -> Unit,
  onCancel: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(stringResource(R.string.pairing_error_title), color = Color(0xFFFF8888), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(message, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(32.dp))

    if (canRetry) {
      Button(
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTurquoise),
      ) {
        Text(stringResource(R.string.pairing_action_retry), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
      }
      Spacer(Modifier.height(12.dp))
    }

    OutlinedButton(
      onClick = onCancel,
      modifier = Modifier.fillMaxWidth().height(50.dp),
    ) {
      Text(stringResource(R.string.pairing_action_cancel), color = PrimaryTurquoise)
    }
  }
}
