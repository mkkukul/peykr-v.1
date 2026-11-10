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
import com.peykr.app.core.network.NetworkUtils
import com.peykr.app.domain.contacts.TrustedContactRepository
import com.peykr.app.domain.localsend.LocalHttpClient
import com.peykr.app.domain.localsend.LocalHttpServer
import com.peykr.app.domain.localsend.LocalNetworkDiscovery
import com.peykr.app.domain.pairing.PairingConfirmation
import com.peykr.app.domain.pairing.PairingEngine
import com.peykr.app.domain.pairing.PairingExchangePayload
import com.peykr.app.domain.pairing.PairingQrPayload
import com.peykr.app.domain.pairing.PairingRequest
import com.peykr.app.domain.pairing.PairingResponse
import com.peykr.app.domain.pairing.PairingRole
import com.peykr.app.domain.pairing.PairingSession
import com.peykr.app.domain.pairing.PairingSessionState
import com.peykr.app.domain.pairing.QrMessageClassifier
import com.peykr.app.domain.pairing.QrMessageType
import com.peykr.app.domain.pairing.RemoteDeviceIdentity
import com.peykr.app.ui.pairing.qr.QrCodeGenerator
import com.peykr.app.ui.pairing.qr.QrMatrix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64

sealed interface PairingUiState {
  data object RoleSelection : PairingUiState

  // Initiator steps
  data class InitiatorShowInitialQr(
    val session: PairingSession,
    val qrPayload: PairingQrPayload,
    val qrMatrix: QrMatrix,
    val oneTimeCode: String = qrPayload.computeOneTimeCode(),
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
    val showSetupPinDialog: Boolean = false,
    val sasCode: String? = null,
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
    val showSetupPinDialog: Boolean = false,
    val sasCode: String? = null,
    val oneTimeCode: String = payload.computeOneTimeCode(),
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
  private val localHttpClient: LocalHttpClient? = null,
  private val contactRepository: TrustedContactRepository? = null,
  private val localHttpServer: LocalHttpServer? = null,
  private val localNetworkDiscovery: LocalNetworkDiscovery? = null,
  coroutineScope: CoroutineScope? = null,
) : ViewModel() {

  init {
    localHttpServer?.let { server ->
      val previousCallback = server.onPairingCompleted
      server.onPairingCompleted = { contact ->
        previousCallback?.invoke(contact)
        scope.launch {
          val curr = _uiState.value
          if (curr is PairingUiState.InitiatorShowInitialQr || curr is PairingUiState.InitiatorScanRequest) {
            _uiState.value = PairingUiState.Success(
              role = PairingRole.INITIATOR,
              remoteName = contact.displayName,
              remoteFingerprint = formatFingerprint(contact.id),
            )
          }
        }
      }
    }
  }

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

      localHttpServer?.activeQrPayload = qrPayload

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
        val sas = updatedSession.transcript?.computeSasCode() ?: updatedSession.computeOneTimeCode()
        _uiState.value = PairingUiState.InitiatorReviewAndApprove(
          session = updatedSession,
          remoteName = remote?.displayName ?: "Unknown Device",
          remoteFingerprint = fingerprint,
          authMethod = requiredAuth,
          sasCode = sas,
        )
      } finally {
        isProcessingScan.set(false)
      }
    }
  }

  fun dismissPinDialog() {
    val current = _uiState.value
    if (current is PairingUiState.InitiatorReviewAndApprove) {
      _uiState.value = current.copy(showPinDialog = false, showSetupPinDialog = false)
    } else if (current is PairingUiState.ResponderReviewAndApprove) {
      _uiState.value = current.copy(showPinDialog = false, showSetupPinDialog = false)
    }
  }

  fun openSetupPinDialog() {
    val current = _uiState.value
    if (current is PairingUiState.InitiatorReviewAndApprove) {
      _uiState.value = current.copy(showSetupPinDialog = true, errorMessage = null)
    } else if (current is PairingUiState.ResponderReviewAndApprove) {
      _uiState.value = current.copy(showSetupPinDialog = true, errorMessage = null)
    }
  }

  fun approveInitiatorRequest() {
    val session = activeSession ?: return
    val current = _uiState.value as? PairingUiState.InitiatorReviewAndApprove ?: return
    _uiState.value = current.copy(isAuthenticating = true, errorMessage = null)

    scope.launch {
      val tokenResult = securityGate.authorizeDirect(SecurityGateScope.APPROVE_TRUSTED_DEVICE)
      val token = tokenResult.getOrElse {
        val promptConfig = AuthPromptConfig(
          title = "Approve Device Pairing",
          description = "Authenticate to confirm pairing with ${current.remoteName}",
        )
        securityGate.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrElse { error ->
          if (securityGate.isPinConfigured()) {
            _uiState.value = current.copy(
              isAuthenticating = false,
              showPinDialog = true,
              errorMessage = "Biometric authentication not completed. Enter PIN to approve.",
            )
          } else {
            _uiState.value = current.copy(
              isAuthenticating = false,
              showSetupPinDialog = true,
              errorMessage = null,
            )
          }
          return@launch
        }
      }
      executeInitiatorApproval(session, current, token)
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

  fun setupInlinePinForInitiator(pin: String) {
    val session = activeSession ?: return
    val current = _uiState.value as? PairingUiState.InitiatorReviewAndApprove ?: return

    _uiState.value = current.copy(isAuthenticating = true, errorMessage = null)
    scope.launch {
      val setupResult = securityGate.setupPin(pin)
      if (setupResult.isFailure) {
        _uiState.value = current.copy(
          isAuthenticating = false,
          showSetupPinDialog = true,
          errorMessage = "PIN kurulumu başarısız: ${setupResult.exceptionOrNull()?.message}",
        )
        return@launch
      }
      val authResult = securityGate.authorizeWithPin(SecurityGateScope.APPROVE_TRUSTED_DEVICE, pin)
      val token = authResult.getOrElse { error ->
        _uiState.value = current.copy(
          isAuthenticating = false,
          showSetupPinDialog = false,
          errorMessage = "Yetkilendirme başarısız: ${error.message}",
        )
        return@launch
      }
      _uiState.value = current.copy(showSetupPinDialog = false)
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
        val code = payload.computeOneTimeCode()
        _uiState.value = PairingUiState.ResponderReviewAndApprove(
          qrPayloadString = scannedString,
          payload = payload,
          remoteName = remoteIdentity.displayName,
          remoteFingerprint = formatFingerprint(remoteIdentity.deviceId),
          authMethod = requiredAuth,
          oneTimeCode = code,
        )
      } finally {
        isProcessingScan.set(false)
      }
    }
  }

  fun approveResponderRequest(responderDisplayName: String = defaultDeviceName) {
    val current = _uiState.value as? PairingUiState.ResponderReviewAndApprove ?: return
    _uiState.value = current.copy(isAuthenticating = true, errorMessage = null)

    scope.launch {
      // 1. First attempt: Direct network pairing if remote IP/port and localHttpClient are available
      val targetIp = current.payload.initiatorIp
      val targetPort = current.payload.initiatorPort
      if (!targetIp.isNullOrBlank() && targetPort > 0 && localHttpClient != null) {
        val identity = deviceIdentity?.getOrCreate()?.getOrNull()
        if (identity != null) {
          val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
          val nonceB64 = Base64.getEncoder().encodeToString(nonce)
          val transcript = "$nonceB64:${current.payload.initiatorDeviceId}".toByteArray(StandardCharsets.UTF_8)
          val sigBytes = deviceIdentity.sign(transcript).getOrNull()
          val sigB64 = sigBytes?.let { Base64.getEncoder().encodeToString(it) } ?: ""

          val exchangePayload = PairingExchangePayload(
            version = 1,
            sessionId = current.payload.sessionId,
            responderDeviceId = identity.id,
            responderPublicKeyBase64 = identity.publicKey,
            responderDisplayName = responderDisplayName,
            responderNonceBase64 = nonceB64,
            responderSignatureBase64 = sigB64,
            responderIp = NetworkUtils.getLocalIpAddress(),
            responderPort = 53317,
          )

          val exchangeResult = localHttpClient.sendPairingExchange(targetIp, targetPort, exchangePayload)
          if (exchangeResult.isSuccess) {
            val remoteIdentity = RemoteDeviceIdentity(
              deviceId = current.payload.initiatorDeviceId,
              publicKeyBase64 = current.payload.initiatorPublicKeyBase64,
              displayName = current.payload.initiatorDisplayName,
            )
            pairingEngine.completeDirectPairing(remoteIdentity)
            localNetworkDiscovery?.addVirtualDiscoveredDevice(
              com.peykr.app.domain.transfer.DiscoveredDevice(
                deviceId = current.payload.initiatorDeviceId,
                displayName = current.payload.initiatorDisplayName,
                deviceModel = "Android",
                ipAddress = targetIp,
                port = targetPort,
                fingerprint = current.payload.initiatorPublicKeyBase64,
                lastSeenEpochMillis = System.currentTimeMillis()
              )
            )
            localNetworkDiscovery?.cachePeerAddress(current.payload.initiatorDeviceId, targetIp, targetPort)
            _uiState.value = PairingUiState.Success(
              role = PairingRole.RESPONDER,
              remoteName = current.payload.initiatorDisplayName,
              remoteFingerprint = formatFingerprint(current.payload.initiatorDeviceId),
            )
            return@launch
          }
        }
      }

      // 2. Optical pairing fallback: obtain authorization token and proceed
      val tokenResult = securityGate.authorizeDirect(SecurityGateScope.APPROVE_TRUSTED_DEVICE)
      val token = tokenResult.getOrElse {
        val promptConfig = AuthPromptConfig(
          title = "Approve Device Pairing",
          description = "Authenticate to request pairing with ${current.remoteName}",
        )
        securityGate.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrElse { error ->
          if (securityGate.isPinConfigured()) {
            _uiState.value = current.copy(
              isAuthenticating = false,
              showPinDialog = true,
              errorMessage = "Biometric authentication not completed. Enter PIN to approve.",
            )
          } else {
            _uiState.value = current.copy(
              isAuthenticating = false,
              showSetupPinDialog = true,
              errorMessage = null,
            )
          }
          return@launch
        }
      }
      executeResponderApproval(current, token, responderDisplayName)
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

  fun setupInlinePinForResponder(pin: String, responderDisplayName: String = defaultDeviceName) {
    val current = _uiState.value as? PairingUiState.ResponderReviewAndApprove ?: return

    _uiState.value = current.copy(isAuthenticating = true, errorMessage = null)
    scope.launch {
      val setupResult = securityGate.setupPin(pin)
      if (setupResult.isFailure) {
        _uiState.value = current.copy(
          isAuthenticating = false,
          showSetupPinDialog = true,
          errorMessage = "PIN kurulumu başarısız: ${setupResult.exceptionOrNull()?.message}",
        )
        return@launch
      }
      val authResult = securityGate.authorizeWithPin(SecurityGateScope.APPROVE_TRUSTED_DEVICE, pin)
      val token = authResult.getOrElse { error ->
        _uiState.value = current.copy(
          isAuthenticating = false,
          showSetupPinDialog = false,
          errorMessage = "Yetkilendirme başarısız: ${error.message}",
        )
        return@launch
      }
      _uiState.value = current.copy(showSetupPinDialog = false)
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
      localHttpServer?.activeQrPayload = null
      activeSession?.let { session ->
        pairingEngine.cancelSession(session)
      }
      activeSession = null
      _uiState.value = PairingUiState.RoleSelection
    }
  }

  fun resetToRoleSelection() {
    localHttpServer?.activeQrPayload = null
    activeSession = null
    _uiState.value = PairingUiState.RoleSelection
  }

  fun onManualInput(input: String) {
    val trimmed = input.trim()
    if (trimmed.startsWith("{")) {
      onResponderScannedInitialQr(trimmed)
      return
    }

    val cleanCode = trimmed.replace(" ", "")
    if (cleanCode.length == 6 && cleanCode.all { it.isDigit() }) {
      scope.launch {
        // 1. Probe already discovered peers for matching one-time code
        val discovered = (localNetworkDiscovery?.discoveredDevices?.value ?: emptyList())
        for (dev in discovered) {
          try {
            val url = java.net.URL("http://${dev.ipAddress}:${dev.port}/api/peykr/v1/pair/qr")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
              connectTimeout = 1500
              readTimeout = 1500
            }
            if (conn.responseCode == 200) {
              val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
              val parseResult = PairingQrPayload.parse(jsonStr)
              val payload = parseResult.getOrNull()
              if (payload != null && payload.computeOneTimeCode().replace(" ", "") == cleanCode) {
                onResponderScannedInitialQr(jsonStr)
                return@launch
              }
            }
          } catch (_: Exception) {}
        }

        // 2. Probe local subnet if not yet discovered
        val probedList = localNetworkDiscovery?.probeSubnet(53317) ?: emptyList()
        for (dev in probedList) {
          try {
            val url = java.net.URL("http://${dev.ipAddress}:${dev.port}/api/peykr/v1/pair/qr")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
              connectTimeout = 1500
              readTimeout = 1500
            }
            if (conn.responseCode == 200) {
              val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
              val parseResult = PairingQrPayload.parse(jsonStr)
              val payload = parseResult.getOrNull()
              if (payload != null && payload.computeOneTimeCode().replace(" ", "") == cleanCode) {
                onResponderScannedInitialQr(jsonStr)
                return@launch
              }
            }
          } catch (_: Exception) {}
        }

        _uiState.value = PairingUiState.ResponderScanInitialQr(
          errorMessage = "Kod ($cleanCode) ile eşleşen cihaz yerel ağda bulunamadı. Lütfen QR kodunu tarayın veya karşı cihazdaki 'Kopyala' butonundan metni yapıştırın."
        )
      }
    } else {
      onResponderScannedInitialQr(trimmed)
    }
  }

  fun formatFingerprint(deviceIdHex: String): String {
    if (deviceIdHex.isBlank()) return "Unknown"
    val clean = deviceIdHex.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }
    if (clean.length != 64) return deviceIdHex
    return clean.chunked(4).joinToString(" ")
  }
}
