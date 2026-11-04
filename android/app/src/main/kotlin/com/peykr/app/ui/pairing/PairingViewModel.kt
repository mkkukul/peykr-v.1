package com.peykr.app.ui.pairing

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peykr.app.core.auth.AuthPromptConfig
import com.peykr.app.core.auth.AuthorizationToken
import com.peykr.app.core.auth.RequiredAuthMethod
import com.peykr.app.core.auth.SecurityGate
import com.peykr.app.core.auth.SecurityGateScope
import com.peykr.app.core.identity.DeviceIdentity
import com.peykr.app.core.identity.getOrNull
import com.peykr.app.domain.pairing.PairingConfirmation
import com.peykr.app.domain.pairing.PairingEngine
import com.peykr.app.domain.pairing.PairingQrPayload
import com.peykr.app.domain.pairing.PairingRequest
import com.peykr.app.domain.pairing.PairingResponse
import com.peykr.app.domain.pairing.PairingRole
import com.peykr.app.domain.pairing.PairingSession
import com.peykr.app.domain.pairing.PairingSessionState
import com.peykr.app.domain.pairing.QrMessageClassifier
import com.peykr.app.domain.pairing.QrMessageType
import com.peykr.app.ui.pairing.qr.QrCodeGenerator
import com.peykr.app.ui.pairing.qr.QrMatrix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PairingUiState {
  data object RoleSelection : PairingUiState

  // Initiator steps
  data class InitiatorShowInitialQr(
    val session: PairingSession,
    val qrPayload: PairingQrPayload,
    val qrMatrix: QrMatrix,
  ) : PairingUiState

  data class InitiatorScanRequest(
    val session: PairingSession,
    val errorMessage: String? = null,
  ) : PairingUiState

  data class InitiatorReviewAndApprove(
    val session: PairingSession,
    val remoteName: String,
    val remoteFingerprint: String,
    val isAuthenticating: Boolean = false,
    val errorMessage: String? = null,
    val authMethod: RequiredAuthMethod = RequiredAuthMethod.BIOMETRIC_OR_PIN,
    val showPinDialog: Boolean = false,
  ) : PairingUiState

  data class InitiatorShowResponseQr(
    val session: PairingSession,
    val response: PairingResponse,
    val qrMatrix: QrMatrix,
  ) : PairingUiState

  data class InitiatorScanConfirmation(
    val session: PairingSession,
    val errorMessage: String? = null,
  ) : PairingUiState

  // Responder steps
  data class ResponderScanInitialQr(
    val errorMessage: String? = null,
  ) : PairingUiState

  data class ResponderReviewAndApprove(
    val qrPayloadString: String,
    val payload: PairingQrPayload,
    val remoteName: String,
    val remoteFingerprint: String,
    val isAuthenticating: Boolean = false,
    val errorMessage: String? = null,
    val authMethod: RequiredAuthMethod = RequiredAuthMethod.BIOMETRIC_OR_PIN,
    val showPinDialog: Boolean = false,
  ) : PairingUiState

  data class ResponderShowRequestQr(
    val session: PairingSession,
    val request: PairingRequest,
    val qrMatrix: QrMatrix,
  ) : PairingUiState

  data class ResponderScanResponse(
    val session: PairingSession,
    val errorMessage: String? = null,
  ) : PairingUiState

  data class ResponderShowConfirmationQr(
    val session: PairingSession,
    val confirmation: PairingConfirmation,
    val qrMatrix: QrMatrix,
  ) : PairingUiState

  // Terminal states
  data class Success(
    val role: PairingRole,
    val remoteName: String,
    val remoteFingerprint: String,
  ) : PairingUiState

  data class Error(
    val message: String,
    val canRetry: Boolean = true,
  ) : PairingUiState
}

class PairingViewModel(
  private val pairingEngine: PairingEngine,
  private val securityGate: SecurityGate,
  private val deviceIdentity: DeviceIdentity? = null,
  coroutineScope: CoroutineScope? = null,
) : ViewModel() {

  private val scope: CoroutineScope = coroutineScope ?: viewModelScope

  private val _uiState = MutableStateFlow<PairingUiState>(PairingUiState.RoleSelection)
  val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

  private var activeSession: PairingSession? = null
  private val isProcessingScan = java.util.concurrent.atomic.AtomicBoolean(false)

  private val defaultDeviceName: String
    get() = Build.MODEL.takeIf { !it.isNullOrBlank() } ?: "Peykr Device"

  fun startAsInitiator(displayName: String = defaultDeviceName) {
    scope.launch {
      val sessionResult = pairingEngine.createInitiatorSession(displayName)
      val session = sessionResult.getOrElse { error ->
        _uiState.value = PairingUiState.Error("Failed to initialize session: ${error.message}")
        return@launch
      }
      activeSession = session

      val qrPayloadResult = pairingEngine.generateQrPayload(session)
      val qrPayload = qrPayloadResult.getOrElse { error ->
        _uiState.value = PairingUiState.Error("Failed to generate QR payload: ${error.message}")
        return@launch
      }

      val serialized = qrPayload.serialize()
      val matrixResult = QrCodeGenerator.generateQrMatrix(serialized)
      val matrix = matrixResult.getOrElse { error ->
        _uiState.value = PairingUiState.Error("Failed to generate QR matrix: ${error.message}")
        return@launch
      }

      val updatedSession = pairingEngine.getSession(session.sessionId) ?: session.copy(state = PairingSessionState.QR_READY)
      activeSession = updatedSession

      _uiState.value = PairingUiState.InitiatorShowInitialQr(updatedSession, qrPayload, matrix)
    }
  }

  fun proceedToScanResponderRequest() {
    val session = activeSession ?: return
    _uiState.value = PairingUiState.InitiatorScanRequest(session)
  }

  fun onInitiatorScannedRequestQr(scannedString: String) {
    val session = activeSession ?: return
    if (!isProcessingScan.compareAndSet(false, true)) return
    scope.launch {
      try {
        val detectedType = QrMessageClassifier.classify(scannedString)
        if (detectedType != QrMessageType.PAIRING_REQUEST) {
          _uiState.value = PairingUiState.InitiatorScanRequest(
            session,
            errorMessage = "Scanned QR code is not a pairing request. Please scan the responder's request code.",
          )
          return@launch
        }

        val requestResult = PairingRequest.parse(scannedString)
        val request = requestResult.getOrElse { error ->
          _uiState.value = PairingUiState.InitiatorScanRequest(
            session,
            errorMessage = "Malformed request payload: ${error.message}",
          )
          return@launch
        }

        val receiveResult = pairingEngine.receivePairingRequest(session, request)
        val updatedSession = receiveResult.getOrElse { error ->
          _uiState.value = PairingUiState.InitiatorScanRequest(
            session,
            errorMessage = "Validation failed: ${error.message}",
          )
          return@launch
        }
        activeSession = updatedSession

        val remote = updatedSession.remoteIdentity
        val fingerprint = remote?.deviceId?.let { formatFingerprint(it) } ?: "Unknown"

        val requiredAuth = securityGate.getRequiredAuthMethod()
        _uiState.value = PairingUiState.InitiatorReviewAndApprove(
          session = updatedSession,
          remoteName = remote?.displayName ?: "Unknown Device",
          remoteFingerprint = fingerprint,
          authMethod = requiredAuth,
        )
      } finally {
        isProcessingScan.set(false)
      }
    }
  }

  fun dismissPinDialog() {
    val current = _uiState.value
    if (current is PairingUiState.InitiatorReviewAndApprove) {
      _uiState.value = current.copy(showPinDialog = false)
    } else if (current is PairingUiState.ResponderReviewAndApprove) {
      _uiState.value = current.copy(showPinDialog = false)
    }
  }

  fun approveInitiatorRequest() {
    val session = activeSession ?: return
    val current = _uiState.value as? PairingUiState.InitiatorReviewAndApprove ?: return

    when (securityGate.getRequiredAuthMethod()) {
      RequiredAuthMethod.SETUP_PIN_REQUIRED -> {
        _uiState.value = current.copy(
          errorMessage = "PIN setup required: please configure your application PIN in Security Settings before approving devices."
        )
      }
      RequiredAuthMethod.PIN_REQUIRED -> {
        _uiState.value = current.copy(showPinDialog = true, errorMessage = null)
      }
      RequiredAuthMethod.BIOMETRIC_OR_PIN -> {
        _uiState.value = current.copy(isAuthenticating = true, errorMessage = null)
        scope.launch {
          val promptConfig = AuthPromptConfig(
            title = "Approve Device Pairing",
            description = "Authenticate to confirm pairing with ${current.remoteName}",
          )
          val authResult = securityGate.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig)
          val token = authResult.getOrElse { error ->
            if (securityGate.isPinConfigured()) {
              _uiState.value = current.copy(
                isAuthenticating = false,
                showPinDialog = true,
                errorMessage = "Biometric authentication not completed. Enter PIN to approve.",
              )
            } else {
              _uiState.value = current.copy(
                isAuthenticating = false,
                errorMessage = "Authentication failed: ${error.message}",
              )
            }
            return@launch
          }
          executeInitiatorApproval(session, current, token)
        }
      }
    }
  }

  fun submitPinForInitiatorApproval(pin: String) {
    val session = activeSession ?: return
    val current = _uiState.value as? PairingUiState.InitiatorReviewAndApprove ?: return

    _uiState.value = current.copy(isAuthenticating = true, errorMessage = null)
    scope.launch {
      val authResult = securityGate.authorizeWithPin(SecurityGateScope.APPROVE_TRUSTED_DEVICE, pin)
      val token = authResult.getOrElse { error ->
        _uiState.value = current.copy(
          isAuthenticating = false,
          showPinDialog = true,
          errorMessage = "PIN verification failed: ${error.message}",
        )
        return@launch
      }
      _uiState.value = current.copy(showPinDialog = false)
      executeInitiatorApproval(session, current, token)
    }
  }

  private suspend fun executeInitiatorApproval(
    session: PairingSession,
    current: PairingUiState.InitiatorReviewAndApprove,
    token: AuthorizationToken,
  ) {
    val approvalResult = pairingEngine.approvePairingRequest(session, token)
    val (sentSession, response) = approvalResult.getOrElse { error ->
      _uiState.value = current.copy(
        isAuthenticating = false,
        errorMessage = "Approval failed: ${error.message}",
      )
      return
    }
    activeSession = sentSession

    val serializedResponse = response.serialize()
    val matrixResult = QrCodeGenerator.generateQrMatrix(serializedResponse)
    val matrix = matrixResult.getOrElse { error ->
      _uiState.value = PairingUiState.Error("Failed to render approval QR: ${error.message}")
      return
    }

    _uiState.value = PairingUiState.InitiatorShowResponseQr(sentSession, response, matrix)
  }

  fun proceedToScanFinalConfirmation() {
    val session = activeSession ?: return
    _uiState.value = PairingUiState.InitiatorScanConfirmation(session)
  }

  fun onInitiatorScannedConfirmationQr(scannedString: String) {
    val session = activeSession ?: return
    if (!isProcessingScan.compareAndSet(false, true)) return
    scope.launch {
      try {
        val detectedType = QrMessageClassifier.classify(scannedString)
        if (detectedType != QrMessageType.PAIRING_CONFIRMATION) {
          _uiState.value = PairingUiState.InitiatorScanConfirmation(
            session,
            errorMessage = "Scanned QR code is not a pairing confirmation.",
          )
          return@launch
        }

        val confirmResult = PairingConfirmation.parse(scannedString)
        val confirmation = confirmResult.getOrElse { error ->
          _uiState.value = PairingUiState.InitiatorScanConfirmation(
            session,
            errorMessage = "Malformed confirmation payload: ${error.message}",
          )
          return@launch
        }

        val confirmPairingResult = pairingEngine.confirmInitiatorPairing(session, confirmation)
        val completedSession = confirmPairingResult.getOrElse { error ->
          _uiState.value = PairingUiState.InitiatorScanConfirmation(
            session,
            errorMessage = "Confirmation verification failed: ${error.message}",
          )
          return@launch
        }
        activeSession = completedSession

        val remote = completedSession.remoteIdentity
        val fingerprint = remote?.deviceId?.let { formatFingerprint(it) } ?: "Unknown"

        _uiState.value = PairingUiState.Success(
          role = PairingRole.INITIATOR,
          remoteName = remote?.displayName ?: "Unknown Device",
          remoteFingerprint = fingerprint,
        )
      } finally {
        isProcessingScan.set(false)
      }
    }
  }

  fun startAsResponder() {
    _uiState.value = PairingUiState.ResponderScanInitialQr()
  }

  fun onResponderScannedInitialQr(scannedString: String) {
    if (!isProcessingScan.compareAndSet(false, true)) return
    scope.launch {
      try {
        val detectedType = QrMessageClassifier.classify(scannedString)
        if (detectedType != QrMessageType.INITIAL_PAYLOAD) {
          _uiState.value = PairingUiState.ResponderScanInitialQr(
            errorMessage = "Scanned QR is not an initial pairing payload. Please scan the initiator's QR code.",
          )
          return@launch
        }

        val parseResult = PairingQrPayload.parse(scannedString)
        val payload = parseResult.getOrElse { error ->
          _uiState.value = PairingUiState.ResponderScanInitialQr(
            errorMessage = "Malformed initial pairing payload: ${error.message}",
          )
          return@launch
        }

        val remoteIdentity = com.peykr.app.domain.pairing.RemoteDeviceIdentity(
          deviceId = payload.initiatorDeviceId,
          publicKeyBase64 = payload.initiatorPublicKeyBase64,
          displayName = payload.initiatorDisplayName,
        )

        if (!remoteIdentity.verifyDeviceId()) {
          _uiState.value = PairingUiState.ResponderScanInitialQr(
            errorMessage = "Device identity mismatch: public key does not match device ID.",
          )
          return@launch
        }

        val localId = deviceIdentity?.getOrCreate()?.getOrNull()?.id
        if (localId != null && remoteIdentity.deviceId == localId) {
          _uiState.value = PairingUiState.ResponderScanInitialQr(
            errorMessage = "Cannot pair a device with itself.",
          )
          return@launch
        }

        val requiredAuth = securityGate.getRequiredAuthMethod()
        _uiState.value = PairingUiState.ResponderReviewAndApprove(
          qrPayloadString = scannedString,
          payload = payload,
          remoteName = remoteIdentity.displayName,
          remoteFingerprint = formatFingerprint(remoteIdentity.deviceId),
          authMethod = requiredAuth,
        )
      } finally {
        isProcessingScan.set(false)
      }
    }
  }

  fun approveResponderRequest(responderDisplayName: String = defaultDeviceName) {
    val current = _uiState.value as? PairingUiState.ResponderReviewAndApprove ?: return

    when (securityGate.getRequiredAuthMethod()) {
      RequiredAuthMethod.SETUP_PIN_REQUIRED -> {
        _uiState.value = current.copy(
          errorMessage = "PIN setup required: please configure your application PIN in Security Settings before approving devices."
        )
      }
      RequiredAuthMethod.PIN_REQUIRED -> {
        _uiState.value = current.copy(showPinDialog = true, errorMessage = null)
      }
      RequiredAuthMethod.BIOMETRIC_OR_PIN -> {
        _uiState.value = current.copy(isAuthenticating = true, errorMessage = null)
        scope.launch {
          val promptConfig = AuthPromptConfig(
            title = "Approve Device Pairing",
            description = "Authenticate to request pairing with ${current.remoteName}",
          )
          val authResult = securityGate.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig)
          val token = authResult.getOrElse { error ->
            if (securityGate.isPinConfigured()) {
              _uiState.value = current.copy(
                isAuthenticating = false,
                showPinDialog = true,
                errorMessage = "Biometric authentication not completed. Enter PIN to approve.",
              )
            } else {
              _uiState.value = current.copy(
                isAuthenticating = false,
                errorMessage = "Authentication required to pair: ${error.message}",
              )
            }
            return@launch
          }
          executeResponderApproval(current, token, responderDisplayName)
        }
      }
    }
  }

  fun submitPinForResponderApproval(pin: String, responderDisplayName: String = defaultDeviceName) {
    val current = _uiState.value as? PairingUiState.ResponderReviewAndApprove ?: return

    _uiState.value = current.copy(isAuthenticating = true, errorMessage = null)
    scope.launch {
      val authResult = securityGate.authorizeWithPin(SecurityGateScope.APPROVE_TRUSTED_DEVICE, pin)
      val token = authResult.getOrElse { error ->
        _uiState.value = current.copy(
          isAuthenticating = false,
          showPinDialog = true,
          errorMessage = "PIN verification failed: ${error.message}",
        )
        return@launch
      }
      _uiState.value = current.copy(showPinDialog = false)
      executeResponderApproval(current, token, responderDisplayName)
    }
  }

  private suspend fun executeResponderApproval(
    current: PairingUiState.ResponderReviewAndApprove,
    token: AuthorizationToken,
    responderDisplayName: String,
  ) {
    val requestResult = pairingEngine.processQrAndCreateRequest(
      qrPayloadString = current.qrPayloadString,
      responderDisplayName = responderDisplayName,
      authorizationToken = token,
    )

    val (session, request) = requestResult.getOrElse { error ->
      _uiState.value = current.copy(
        isAuthenticating = false,
        errorMessage = "Failed to process pairing proposal: ${error.message}",
      )
      return
    }
    activeSession = session

    val serializedRequest = request.serialize()
    val matrixResult = QrCodeGenerator.generateQrMatrix(serializedRequest)
    val matrix = matrixResult.getOrElse { error ->
      _uiState.value = PairingUiState.Error("Failed to render request QR: ${error.message}")
      return
    }

    _uiState.value = PairingUiState.ResponderShowRequestQr(session, request, matrix)
  }

  fun proceedToScanInitiatorResponse() {
    val session = activeSession ?: return
    _uiState.value = PairingUiState.ResponderScanResponse(session)
  }

  fun onResponderScannedResponseQr(scannedString: String) {
    val session = activeSession ?: return
    if (!isProcessingScan.compareAndSet(false, true)) return
    scope.launch {
      try {
        val detectedType = QrMessageClassifier.classify(scannedString)
        if (detectedType != QrMessageType.PAIRING_RESPONSE) {
          _uiState.value = PairingUiState.ResponderScanResponse(
            session,
            errorMessage = "Scanned QR code is not a pairing response.",
          )
          return@launch
        }

        val parseResult = PairingResponse.parse(scannedString)
        val response = parseResult.getOrElse { error ->
          _uiState.value = PairingUiState.ResponderScanResponse(
            session,
            errorMessage = "Malformed response payload: ${error.message}",
          )
          return@launch
        }

        val finalizeResult = pairingEngine.finalizeResponderPairing(session, response)
        val (completedSession, confirmation) = finalizeResult.getOrElse { error ->
          _uiState.value = PairingUiState.ResponderScanResponse(
            session,
            errorMessage = "Finalization failed: ${error.message}",
          )
          return@launch
        }
        activeSession = completedSession

        val serializedConfirmation = confirmation.serialize()
        val matrixResult = QrCodeGenerator.generateQrMatrix(serializedConfirmation)
        val matrix = matrixResult.getOrElse { error ->
          _uiState.value = PairingUiState.Error("Failed to render confirmation QR: ${error.message}")
          return@launch
        }

        _uiState.value = PairingUiState.ResponderShowConfirmationQr(completedSession, confirmation, matrix)
      } finally {
        isProcessingScan.set(false)
      }
    }
  }

  fun finishResponderFlow() {
    val session = activeSession ?: return
    if (session.state != PairingSessionState.PAIRED) return
    val remote = session.remoteIdentity
    val fingerprint = remote?.deviceId?.let { formatFingerprint(it) } ?: "Unknown"

    _uiState.value = PairingUiState.Success(
      role = PairingRole.RESPONDER,
      remoteName = remote?.displayName ?: "Unknown Device",
      remoteFingerprint = fingerprint,
    )
  }

  fun cancelPairing() {
    scope.launch {
      activeSession?.let { session ->
        pairingEngine.cancelSession(session)
      }
      activeSession = null
      _uiState.value = PairingUiState.RoleSelection
    }
  }

  fun resetToRoleSelection() {
    activeSession = null
    _uiState.value = PairingUiState.RoleSelection
  }

  fun formatFingerprint(deviceIdHex: String): String {
    if (deviceIdHex.isBlank()) return "Unknown"
    val clean = deviceIdHex.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }
    if (clean.length != 64) return deviceIdHex
    return clean.chunked(4).joinToString(" ")
  }
}
