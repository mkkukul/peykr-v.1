package com.peykr.app.ui.pairing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.peykr.app.R
import com.peykr.app.core.auth.RequiredAuthMethod
import com.peykr.app.core.auth.SecurityGate
import com.peykr.app.core.identity.DeviceIdentity
import com.peykr.app.domain.pairing.PairingEngine
import com.peykr.app.ui.pairing.qr.QrMatrix
import com.peykr.app.ui.pairing.scanner.CameraScannerView
import com.peykr.app.ui.theme.CardBorderColor
import com.peykr.app.ui.theme.CardShadowColor
import com.peykr.app.ui.theme.PastelBlueBg
import com.peykr.app.ui.theme.PastelBlueFg
import com.peykr.app.ui.theme.PastelTealBg
import com.peykr.app.ui.theme.PastelTealFg
import com.peykr.app.ui.theme.PeykrCard
import com.peykr.app.ui.theme.PeykrScreenContainer
import com.peykr.app.ui.theme.PeykrTheme
import com.peykr.app.ui.theme.PremiumIcon
import com.peykr.app.ui.theme.PremiumIconKind
import com.peykr.app.ui.theme.SuccessGreen

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
    containerColor = Color.Transparent,
  ) { padding ->
    PeykrScreenContainer {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .padding(horizontal = 20.dp, vertical = 14.dp)
          .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        // Top Navigation Bar
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
              color = PeykrTheme.colors.primaryAccent,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
            )
          }
          Spacer(Modifier.weight(1f))
          Text(
            text = stringResource(R.string.pairing_title),
            color = PeykrTheme.colors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Spacer(Modifier.weight(1f))
          Spacer(Modifier.width(48.dp))
        }

        Spacer(Modifier.height(10.dp))

        // Screen Step Flow
        when (val current = state) {
          is PairingUiState.RoleSelection -> {
            RoleSelectionContent(
              onStartInitiator = { actualViewModel.startAsInitiator() },
              onStartResponder = { actualViewModel.startAsResponder() },
            )
          }

          is PairingUiState.InitiatorShowInitialQr -> {
            PairingStepProgress(currentStep = 1, totalSteps = 4, stepLabel = "QR Kod & Güvenlik Kodu")
            Spacer(Modifier.height(14.dp))
            QrDisplayWithCodeContent(
              title = stringResource(R.string.pairing_step1_init_title),
              instruction = stringResource(R.string.pairing_step1_init_desc),
              oneTimeCode = current.oneTimeCode,
              matrix = current.qrMatrix,
              buttonText = stringResource(R.string.pairing_action_next_scan_request),
              onButtonClick = { actualViewModel.proceedToScanResponderRequest() },
            )
          }

          is PairingUiState.InitiatorScanRequest -> {
            PairingStepProgress(currentStep = 2, totalSteps = 4, stepLabel = "İstek Kodunu Tara")
            Spacer(Modifier.height(14.dp))
            ScannerContent(
              title = stringResource(R.string.pairing_step2_init_title),
              instruction = stringResource(R.string.pairing_step2_init_desc),
              errorMessage = current.errorMessage,
              onScanned = { actualViewModel.onInitiatorScannedRequestQr(it) },
            )
          }

          is PairingUiState.InitiatorReviewAndApprove -> {
            PairingStepProgress(currentStep = 3, totalSteps = 4, stepLabel = "Cihazı Doğrula & Onayla")
            Spacer(Modifier.height(14.dp))
            ReviewAndApproveContent(
              remoteName = current.remoteName,
              remoteFingerprint = current.remoteFingerprint,
              sasCode = current.sasCode,
              oneTimeCode = null,
              isAuthenticating = current.isAuthenticating,
              errorMessage = current.errorMessage,
              authMethod = current.authMethod,
              showPinDialog = current.showPinDialog,
              showSetupPinDialog = current.showSetupPinDialog,
              onApprove = { actualViewModel.approveInitiatorRequest() },
              onSubmitPin = { actualViewModel.submitPinForInitiatorApproval(it) },
              onSubmitSetupPin = { actualViewModel.setupInlinePinForInitiator(it) },
              onDismissPin = { actualViewModel.dismissPinDialog() },
              onRequestCreatePin = { actualViewModel.openSetupPinDialog() },
            )
          }

          is PairingUiState.InitiatorShowResponseQr -> {
            PairingStepProgress(currentStep = 4, totalSteps = 4, stepLabel = "Yanıt Kodunu Göster")
            Spacer(Modifier.height(14.dp))
            QrDisplayContent(
              title = stringResource(R.string.pairing_step3_init_title),
              instruction = stringResource(R.string.pairing_step3_init_desc),
              matrix = current.qrMatrix,
              buttonText = stringResource(R.string.pairing_action_next_scan_confirm),
              onButtonClick = { actualViewModel.proceedToScanFinalConfirmation() },
            )
          }

          is PairingUiState.InitiatorScanConfirmation -> {
            PairingStepProgress(currentStep = 4, totalSteps = 4, stepLabel = "Son Onayı Tara")
            Spacer(Modifier.height(14.dp))
            ScannerContent(
              title = stringResource(R.string.pairing_step4_init_title),
              instruction = stringResource(R.string.pairing_step4_init_desc),
              errorMessage = current.errorMessage,
              onScanned = { actualViewModel.onInitiatorScannedConfirmationQr(it) },
            )
          }

          is PairingUiState.ResponderScanInitialQr -> {
            PairingStepProgress(currentStep = 1, totalSteps = 4, stepLabel = "Başlatıcı Kodunu Tara")
            Spacer(Modifier.height(14.dp))
            ScannerContent(
              title = stringResource(R.string.pairing_step1_resp_title),
              instruction = stringResource(R.string.pairing_step1_resp_desc),
              errorMessage = current.errorMessage,
              onScanned = { actualViewModel.onResponderScannedInitialQr(it) },
            )
          }

          is PairingUiState.ResponderReviewAndApprove -> {
            PairingStepProgress(currentStep = 2, totalSteps = 4, stepLabel = "İncele & PIN Onayı")
            Spacer(Modifier.height(14.dp))
            ReviewAndApproveContent(
              remoteName = current.remoteName,
              remoteFingerprint = current.remoteFingerprint,
              sasCode = current.sasCode,
              oneTimeCode = current.oneTimeCode,
              isAuthenticating = current.isAuthenticating,
              errorMessage = current.errorMessage,
              authMethod = current.authMethod,
              showPinDialog = current.showPinDialog,
              showSetupPinDialog = current.showSetupPinDialog,
              onApprove = { actualViewModel.approveResponderRequest() },
              onSubmitPin = { actualViewModel.submitPinForResponderApproval(it) },
              onSubmitSetupPin = { actualViewModel.setupInlinePinForResponder(it) },
              onDismissPin = { actualViewModel.dismissPinDialog() },
              onRequestCreatePin = { actualViewModel.openSetupPinDialog() },
            )
          }

          is PairingUiState.ResponderShowRequestQr -> {
            PairingStepProgress(currentStep = 2, totalSteps = 4, stepLabel = "İstek Kodunu Göster")
            Spacer(Modifier.height(14.dp))
            QrDisplayContent(
              title = stringResource(R.string.pairing_step2_resp_title),
              instruction = stringResource(R.string.pairing_step2_resp_desc),
              matrix = current.qrMatrix,
              buttonText = stringResource(R.string.pairing_action_next_scan_response),
              onButtonClick = { actualViewModel.proceedToScanInitiatorResponse() },
            )
          }

          is PairingUiState.ResponderScanResponse -> {
            PairingStepProgress(currentStep = 3, totalSteps = 4, stepLabel = "Yanıt Kodunu Tara")
            Spacer(Modifier.height(14.dp))
            ScannerContent(
              title = stringResource(R.string.pairing_step3_resp_title),
              instruction = stringResource(R.string.pairing_step3_resp_desc),
              errorMessage = current.errorMessage,
              onScanned = { actualViewModel.onResponderScannedResponseQr(it) },
            )
          }

          is PairingUiState.ResponderShowConfirmationQr -> {
            PairingStepProgress(currentStep = 4, totalSteps = 4, stepLabel = "Eşleşme Tamamlandı")
            Spacer(Modifier.height(14.dp))
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
}

@Composable
fun PairingStepProgress(
  currentStep: Int,
  totalSteps: Int = 4,
  stepLabel: String,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 4.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Adım $currentStep / $totalSteps",
        style = MaterialTheme.typography.labelMedium,
        color = PeykrTheme.colors.primaryAccent,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = stepLabel,
        style = MaterialTheme.typography.labelMedium,
        color = PeykrTheme.colors.textSecondary,
        fontWeight = FontWeight.Medium
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      for (i in 1..totalSteps) {
        val isActive = i <= currentStep
        Box(
          modifier = Modifier
            .weight(1f)
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (isActive) PeykrTheme.colors.primaryAccent else PeykrTheme.colors.border)
        )
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
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier
        .size(64.dp)
        .clip(CircleShape)
        .background(PastelBlueBg),
      contentAlignment = Alignment.Center
    ) {
      PremiumIcon(PremiumIconKind.Device, "", tint = PastelBlueFg, modifier = Modifier.size(32.dp))
    }

    Spacer(Modifier.height(16.dp))

    Text(
      text = stringResource(R.string.pairing_select_mode_title),
      color = PeykrTheme.colors.textPrimary,
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
      text = stringResource(R.string.pairing_select_mode_desc),
      color = PeykrTheme.colors.textSecondary,
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(28.dp))

    PeykrCard(
      modifier = Modifier.fillMaxWidth(),
      onClick = onStartInitiator,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PastelBlueBg),
          contentAlignment = Alignment.Center
        ) {
          PremiumIcon(PremiumIconKind.MicroChevron, "", tint = PastelBlueFg, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.pairing_mode_initiator),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = PeykrTheme.colors.textPrimary
          )
          Text(
            text = "Eşleştirme kodunu gösterir ve responder cihazın taramasını bekler.",
            style = MaterialTheme.typography.bodySmall,
            color = PeykrTheme.colors.textSecondary
          )
        }
      }
    }

    Spacer(Modifier.height(14.dp))

    PeykrCard(
      modifier = Modifier.fillMaxWidth(),
      onClick = onStartResponder,
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
          PremiumIcon(PremiumIconKind.MicroChevron, "", tint = PastelTealFg, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.pairing_mode_responder),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = PeykrTheme.colors.textPrimary
          )
          Text(
            text = "Başlatıcı cihazın QR kodunu kamerayla tarar ve eşleşme başlatır.",
            style = MaterialTheme.typography.bodySmall,
            color = PeykrTheme.colors.textSecondary
          )
        }
      }
    }
  }
}

@Composable
private fun QrDisplayWithCodeContent(
  title: String,
  instruction: String,
  oneTimeCode: String,
  matrix: QrMatrix,
  buttonText: String,
  onButtonClick: () -> Unit,
) {
  val clipboardManager = LocalClipboardManager.current
  var codeCopied by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      title,
      color = PeykrTheme.colors.textPrimary,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(6.dp))
    Text(
      instruction,
      color = PeykrTheme.colors.textSecondary,
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(16.dp))

    // Prominent 6-Digit One-Time Pairing Code Card
    PeykrCard(
      modifier = Modifier.fillMaxWidth(),
      containerColor = PeykrTheme.colors.surfaceElevated,
      contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "GEÇİCİ EŞLEŞTİRME KODU",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = PeykrTheme.colors.textSecondary,
            letterSpacing = 1.sp
          )
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(PastelBlueBg)
              .padding(horizontal = 8.dp, vertical = 2.dp)
          ) {
            Text(
              text = "5 dakika geçerli",
              color = PastelBlueFg,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold
            )
          }
        }

        Spacer(Modifier.height(10.dp))

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          Text(
            text = oneTimeCode,
            style = MaterialTheme.typography.headlineLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            color = PeykrTheme.colors.primaryAccent,
            letterSpacing = 4.sp
          )
          Spacer(Modifier.width(12.dp))
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(if (codeCopied) SuccessGreen else PeykrTheme.colors.surface)
              .border(1.dp, PeykrTheme.colors.border, RoundedCornerShape(8.dp))
              .clickable {
                clipboardManager.setText(AnnotatedString(oneTimeCode))
                codeCopied = true
              }
              .padding(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Text(
              text = if (codeCopied) "Kopyalandı ✓" else "Kopyala",
              color = if (codeCopied) Color.White else PeykrTheme.colors.textPrimary,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold
            )
          }
        }

        Spacer(Modifier.height(6.dp))

        Text(
          text = "Bu cihaz güvenlik kodunu gösteriyor. Her iki ekranda aynı kodun göründüğünü doğrulayın.",
          style = MaterialTheme.typography.bodySmall,
          color = PeykrTheme.colors.textSecondary,
          textAlign = TextAlign.Center
        )
      }
    }

    Spacer(Modifier.height(16.dp))

    // QR Code Canvas
    Box(
      modifier = Modifier
        .size(240.dp)
        .clip(RoundedCornerShape(18.dp))
        .background(Color.White)
        .border(1.5.dp, PeykrTheme.colors.border, RoundedCornerShape(18.dp))
        .padding(14.dp),
      contentAlignment = Alignment.Center,
    ) {
      QrCodeView(
        matrix = matrix,
        modifier = Modifier.fillMaxSize(),
      )
    }

    Spacer(Modifier.height(20.dp))

    Button(
      onClick = onButtonClick,
      modifier = Modifier
        .fillMaxWidth()
        .height(50.dp),
      colors = ButtonDefaults.buttonColors(containerColor = PeykrTheme.colors.primaryAccent),
      shape = RoundedCornerShape(14.dp)
    ) {
      Text(buttonText, color = PeykrTheme.colors.onPrimary, fontWeight = FontWeight.Bold)
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
    Text(
      title,
      color = PeykrTheme.colors.textPrimary,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(6.dp))
    Text(
      instruction,
      color = PeykrTheme.colors.textSecondary,
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(18.dp))

    Box(
      modifier = Modifier
        .size(240.dp)
        .clip(RoundedCornerShape(18.dp))
        .background(Color.White)
        .border(1.5.dp, PeykrTheme.colors.border, RoundedCornerShape(18.dp))
        .padding(14.dp),
      contentAlignment = Alignment.Center,
    ) {
      QrCodeView(
        matrix = matrix,
        modifier = Modifier.fillMaxSize(),
      )
    }

    Spacer(Modifier.height(20.dp))

    Button(
      onClick = onButtonClick,
      modifier = Modifier
        .fillMaxWidth()
        .height(50.dp),
      colors = ButtonDefaults.buttonColors(containerColor = PeykrTheme.colors.primaryAccent),
      shape = RoundedCornerShape(14.dp)
    ) {
      Text(buttonText, color = PeykrTheme.colors.onPrimary, fontWeight = FontWeight.Bold)
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
    Text(
      title,
      color = PeykrTheme.colors.textPrimary,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(6.dp))
    Text(
      instruction,
      color = PeykrTheme.colors.textSecondary,
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(14.dp))

    if (errorMessage != null) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color(0xFFFEE2E2), RoundedCornerShape(10.dp))
          .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(10.dp))
          .padding(12.dp),
      ) {
        Text(
          errorMessage,
          color = Color(0xFFDC2626),
          style = MaterialTheme.typography.bodySmall,
          textAlign = TextAlign.Center
        )
      }
      Spacer(Modifier.height(12.dp))
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(300.dp)
        .clip(RoundedCornerShape(20.dp))
        .border(1.5.dp, PeykrTheme.colors.border, RoundedCornerShape(20.dp))
    ) {
      CameraScannerView(
        modifier = Modifier.fillMaxSize(),
        onQrCodeScanned = onScanned,
      )
    }
  }
}

@Composable
private fun ReviewAndApproveContent(
  remoteName: String,
  remoteFingerprint: String,
  sasCode: String?,
  oneTimeCode: String?,
  isAuthenticating: Boolean,
  errorMessage: String?,
  authMethod: RequiredAuthMethod,
  showPinDialog: Boolean,
  showSetupPinDialog: Boolean,
  onApprove: () -> Unit,
  onSubmitPin: (String) -> Unit,
  onSubmitSetupPin: (String) -> Unit,
  onDismissPin: () -> Unit,
  onRequestCreatePin: () -> Unit,
) {
  var pinInput by remember { mutableStateOf("") }
  var setupPinInput by remember { mutableStateOf("") }
  var setupPinConfirmInput by remember { mutableStateOf("") }
  var setupPinError by remember { mutableStateOf<String?>(null) }

  // Enter Existing PIN Dialog
  if (showPinDialog) {
    AlertDialog(
      onDismissRequest = onDismissPin,
      title = { Text("Güvenlik PIN'i Girin", color = PeykrTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            "$remoteName ile eşleştirmeyi onaylamak için PIN kodunuzu girin.",
            color = PeykrTheme.colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
          )
          OutlinedTextField(
            value = pinInput,
            onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) pinInput = it },
            label = { Text("PIN Kodu") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = PeykrTheme.colors.primaryAccent,
              cursorColor = PeykrTheme.colors.primaryAccent
            )
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
          colors = ButtonDefaults.buttonColors(containerColor = PeykrTheme.colors.primaryAccent),
        ) {
          if (isAuthenticating) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
          } else {
            Text("Onayla", color = Color.White, fontWeight = FontWeight.Bold)
          }
        }
      },
      dismissButton = {
        TextButton(onClick = onDismissPin) {
          Text("İptal", color = PeykrTheme.colors.textSecondary)
        }
      },
      containerColor = PeykrTheme.colors.surface,
    )
  }

  // Create New PIN Inline Dialog
  if (showSetupPinDialog) {
    AlertDialog(
      onDismissRequest = onDismissPin,
      title = { Text("Eşleştirme PIN'i Oluştur", color = PeykrTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            "Cihazınızda henüz bir güvenlik PIN'i bulunmuyor. Eşleştirmeyi onaylamak için 4-8 haneli bir PIN belirleyin.",
            color = PeykrTheme.colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
          )
          OutlinedTextField(
            value = setupPinInput,
            onValueChange = {
              if (it.length <= 8 && it.all { c -> c.isDigit() }) {
                setupPinInput = it
                setupPinError = null
              }
            },
            label = { Text("Yeni PIN (en az 4 hane)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = PeykrTheme.colors.primaryAccent,
              cursorColor = PeykrTheme.colors.primaryAccent
            )
          )
          OutlinedTextField(
            value = setupPinConfirmInput,
            onValueChange = {
              if (it.length <= 8 && it.all { c -> c.isDigit() }) {
                setupPinConfirmInput = it
                setupPinError = null
              }
            },
            label = { Text("PIN'i Tekrar Girin") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = PeykrTheme.colors.primaryAccent,
              cursorColor = PeykrTheme.colors.primaryAccent
            )
          )

          if (setupPinError != null) {
            Text(setupPinError!!, color = Color(0xFFDC2626), style = MaterialTheme.typography.bodySmall)
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (setupPinInput.length < 4) {
              setupPinError = "PIN en az 4 haneli olmalıdır."
            } else if (setupPinInput != setupPinConfirmInput) {
              setupPinError = "Girdiğiniz PIN kodları uyuşmuyor."
            } else {
              onSubmitSetupPin(setupPinInput)
              setupPinInput = ""
              setupPinConfirmInput = ""
            }
          },
          enabled = setupPinInput.length >= 4 && !isAuthenticating,
          colors = ButtonDefaults.buttonColors(containerColor = PeykrTheme.colors.primaryAccent),
        ) {
          if (isAuthenticating) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
          } else {
            Text("Kaydet ve Onayla", color = Color.White, fontWeight = FontWeight.Bold)
          }
        }
      },
      dismissButton = {
        TextButton(onClick = onDismissPin) {
          Text("İptal", color = PeykrTheme.colors.textSecondary)
        }
      },
      containerColor = PeykrTheme.colors.surface,
    )
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      stringResource(R.string.pairing_review_title),
      color = PeykrTheme.colors.textPrimary,
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(6.dp))
    Text(
      stringResource(R.string.pairing_review_desc),
      color = PeykrTheme.colors.textSecondary,
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(16.dp))

    // Remote Device Identity Card
    PeykrCard(
      modifier = Modifier.fillMaxWidth(),
      containerColor = PeykrTheme.colors.surface,
      borderColor = PeykrTheme.colors.border,
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        Text(
          stringResource(R.string.pairing_review_device_name),
          color = PeykrTheme.colors.textSecondary,
          style = MaterialTheme.typography.labelMedium
        )
        Text(
          remoteName,
          color = PeykrTheme.colors.textPrimary,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
          stringResource(R.string.pairing_review_fingerprint),
          color = PeykrTheme.colors.textSecondary,
          style = MaterialTheme.typography.labelMedium
        )
        Text(
          remoteFingerprint,
          color = PeykrTheme.colors.primaryAccent,
          style = MaterialTheme.typography.bodySmall,
          fontFamily = FontFamily.Monospace
        )
      }
    }

    // One-Time Security Code Display for Verification
    if (oneTimeCode != null) {
      Spacer(Modifier.height(12.dp))
      PeykrCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = PeykrTheme.colors.surfaceElevated,
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            "DOĞRULAMA GÜVENLİK KODU",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = PeykrTheme.colors.textSecondary
          )
          Spacer(Modifier.height(4.dp))
          Text(
            text = oneTimeCode,
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = PeykrTheme.colors.primaryAccent,
            letterSpacing = 2.sp
          )
          Text(
            text = "Başlatıcı cihazın ekranındaki kodla eşleştiğini doğrulayın.",
            style = MaterialTheme.typography.bodySmall,
            color = PeykrTheme.colors.textSecondary,
            textAlign = TextAlign.Center
          )
        }
      }
    }

    if (sasCode != null) {
      Spacer(Modifier.height(12.dp))
      PeykrCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = PeykrTheme.colors.surfaceElevated,
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            "SAS DOĞRULAMA KODU",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = PeykrTheme.colors.textSecondary
          )
          Spacer(Modifier.height(4.dp))
          Text(
            text = sasCode,
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = SuccessGreen,
            letterSpacing = 2.sp
          )
        }
      }
    }

    // Inline PIN Creation Banner if required
    if (authMethod == RequiredAuthMethod.SETUP_PIN_REQUIRED) {
      Spacer(Modifier.height(14.dp))
      PeykrCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = Color(0xFFEFF6FF),
        borderColor = Color(0xFFBFDBFE),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(PastelBlueBg),
            contentAlignment = Alignment.Center
          ) {
            PremiumIcon(PremiumIconKind.Lock, "", tint = PastelBlueFg, modifier = Modifier.size(18.dp))
          }
          Spacer(Modifier.width(10.dp))
          Column(Modifier.weight(1f)) {
            Text(
              "Eşleştirme PIN'i Gerekli",
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.bodyMedium,
              color = Color(0xFF1E3A8A)
            )
            Text(
              "Onaylamak için hemen bir PIN oluşturabilirsiniz.",
              style = MaterialTheme.typography.bodySmall,
              color = Color(0xFF1E40AF)
            )
          }
          TextButton(onClick = onRequestCreatePin) {
            Text("Oluştur", color = PeykrTheme.colors.primaryAccent, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    Spacer(Modifier.height(16.dp))

    if (errorMessage != null) {
      Text(
        errorMessage,
        color = Color(0xFFDC2626),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center
      )
      Spacer(Modifier.height(10.dp))
    }

    val buttonLabel = when (authMethod) {
      RequiredAuthMethod.PIN_REQUIRED -> stringResource(R.string.pairing_action_enter_pin)
      RequiredAuthMethod.SETUP_PIN_REQUIRED -> "Eşleştirme PIN'i Oluştur & Onayla"
      RequiredAuthMethod.BIOMETRIC_OR_PIN -> stringResource(R.string.pairing_review_action_approve)
    }

    Button(
      onClick = onApprove,
      enabled = !isAuthenticating,
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp),
      colors = ButtonDefaults.buttonColors(containerColor = PeykrTheme.colors.primaryAccent),
      shape = RoundedCornerShape(14.dp)
    ) {
      if (isAuthenticating) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = PeykrTheme.colors.onPrimary)
      } else {
        Text(buttonLabel, color = PeykrTheme.colors.onPrimary, fontWeight = FontWeight.Bold)
      }
    }

    if (authMethod == RequiredAuthMethod.BIOMETRIC_OR_PIN) {
      Spacer(Modifier.height(8.dp))
      TextButton(onClick = { onApprove() }) {
        Text(
          stringResource(R.string.pairing_action_use_pin_fallback),
          color = PeykrTheme.colors.primaryAccent,
          fontWeight = FontWeight.SemiBold
        )
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
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 28.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier
        .size(68.dp)
        .clip(CircleShape)
        .background(Color(0xFFDCFCE7)),
      contentAlignment = Alignment.Center
    ) {
      Text("✓", color = SuccessGreen, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
    }

    Spacer(Modifier.height(16.dp))

    Text(
      stringResource(R.string.pairing_success_title),
      color = PeykrTheme.colors.textPrimary,
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(8.dp))
    Text(
      text = stringResource(R.string.pairing_success_desc, remoteName),
      color = PeykrTheme.colors.textSecondary,
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(14.dp))
    Text(
      remoteFingerprint,
      color = PeykrTheme.colors.primaryAccent,
      style = MaterialTheme.typography.bodySmall,
      fontFamily = FontFamily.Monospace
    )
    Spacer(Modifier.height(28.dp))

    Button(
      onClick = onFinish,
      modifier = Modifier
        .fillMaxWidth()
        .height(50.dp),
      colors = ButtonDefaults.buttonColors(containerColor = PeykrTheme.colors.primaryAccent),
      shape = RoundedCornerShape(14.dp)
    ) {
      Text(
        stringResource(R.string.pairing_action_return_contacts),
        color = PeykrTheme.colors.onPrimary,
        fontWeight = FontWeight.Bold
      )
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
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 28.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier
        .size(64.dp)
        .clip(CircleShape)
        .background(Color(0xFFFEE2E2)),
      contentAlignment = Alignment.Center
    ) {
      Text("✕", color = Color(0xFFDC2626), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    }

    Spacer(Modifier.height(16.dp))

    Text(
      stringResource(R.string.pairing_error_title),
      color = Color(0xFFDC2626),
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(8.dp))
    Text(
      message,
      color = PeykrTheme.colors.textSecondary,
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(28.dp))

    if (canRetry) {
      Button(
        onClick = onRetry,
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PeykrTheme.colors.primaryAccent),
        shape = RoundedCornerShape(14.dp)
      ) {
        Text(
          stringResource(R.string.pairing_action_retry),
          color = PeykrTheme.colors.onPrimary,
          fontWeight = FontWeight.Bold
        )
      }
      Spacer(Modifier.height(12.dp))
    }

    OutlinedButton(
      onClick = onCancel,
      modifier = Modifier
        .fillMaxWidth()
        .height(50.dp),
      shape = RoundedCornerShape(14.dp)
    ) {
      Text(stringResource(R.string.pairing_action_cancel), color = PeykrTheme.colors.primaryAccent)
    }
  }
}
