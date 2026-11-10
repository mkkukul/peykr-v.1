package com.peykr.app.domain.pairing

import com.peykr.app.core.auth.AuthorizationToken
import com.peykr.app.core.auth.SecurityGate
import com.peykr.app.core.auth.SecurityGateScope
import com.peykr.app.core.identity.DeviceIdentity
import com.peykr.app.core.identity.DeviceIdentityInfo
import com.peykr.app.core.identity.DeviceIdentityState
import com.peykr.app.domain.contacts.PairingStatus
import com.peykr.app.domain.contacts.TrustedContact
import com.peykr.app.domain.contacts.TrustedContactRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DefaultPairingEngine(
  private val deviceIdentity: DeviceIdentity,
  private val securityGate: SecurityGate,
  private val contactRepository: TrustedContactRepository,
  private val secureRandom: SecureRandom = SecureRandom(),
  private val timeProvider: () -> Long = { System.currentTimeMillis() },
) : PairingEngine {

  private val activeSessions = ConcurrentHashMap<String, PairingSession>()
  private val sessionMutexes = ConcurrentHashMap<String, Mutex>()

  private fun getMutex(sessionId: String): Mutex =
    sessionMutexes.computeIfAbsent(sessionId) { Mutex() }

  override fun getSession(sessionId: String): PairingSession? = activeSessions[sessionId]

  override suspend fun createInitiatorSession(
    initiatorDisplayName: String,
    validityDurationMillis: Long,
  ): Result<PairingSession> = runCatching {
    val localIdentity = getReadyIdentity()
    val now = timeProvider()
    val sessionId = UUID.randomUUID().toString()
    val normalizedName = RemoteDeviceIdentity.normalizeDisplayName(initiatorDisplayName)

    if (normalizedName.isBlank()) {
      throw PairingException.MalformedPayload("Initiator display name cannot be blank")
    }

    val initiatorNonceBytes = ByteArray(16).also(secureRandom::nextBytes)
    val initiatorNonceBase64 = StrictBase64.encode(initiatorNonceBytes)

    val session = PairingSession(
      sessionId = sessionId,
      role = PairingRole.INITIATOR,
      state = PairingSessionState.CREATED,
      localDeviceId = localIdentity.id,
      localPublicKeyBase64 = localIdentity.publicKey,
      localDisplayName = normalizedName,
      localNonceBase64 = initiatorNonceBase64,
      createdAtEpochMillis = now,
      expiresAtEpochMillis = now + validityDurationMillis,
    )

    activeSessions[sessionId] = session
    session
  }

  override suspend fun generateQrPayload(session: PairingSession): Result<PairingQrPayload> = runCatching {
    getMutex(session.sessionId).withLock {
      val currentSession = activeSessions[session.sessionId] ?: session
      if (currentSession.role != PairingRole.INITIATOR) {
        throw PairingException.InvalidStateTransition(currentSession.state, PairingSessionState.QR_READY)
      }
      if (currentSession.isExpired(timeProvider())) {
        val expired = currentSession.copy(state = PairingSessionState.EXPIRED)
        activeSessions[currentSession.sessionId] = expired
        throw PairingException.ExpiredSession("Pairing session ${currentSession.sessionId} has expired")
      }
      PairingStateValidator.validateTransition(
        currentSession.role,
        currentSession.state,
        PairingSessionState.QR_READY,
      )

      val localIp = com.peykr.app.core.network.NetworkUtils.getLocalIpAddress()
      val payload = PairingQrPayload(
        version = PairingQrPayload.CURRENT_VERSION,
        sessionId = currentSession.sessionId,
        initiatorDeviceId = currentSession.localDeviceId,
        initiatorPublicKeyBase64 = currentSession.localPublicKeyBase64,
        initiatorDisplayName = currentSession.localDisplayName,
        initiatorNonceBase64 = currentSession.localNonceBase64,
        expiresAtEpochMillis = currentSession.expiresAtEpochMillis,
        initiatorIp = localIp,
        initiatorPort = 53317,
      )

      val updatedSession = currentSession.copy(state = PairingSessionState.QR_READY)
      activeSessions[currentSession.sessionId] = updatedSession
      payload
    }
  }

  override suspend fun processQrAndCreateRequest(
    qrPayloadString: String,
    responderDisplayName: String,
    authorizationToken: AuthorizationToken,
  ): Result<PairingRequestResult> = runCatching {
    val localIdentity = getReadyIdentity()
    val now = timeProvider()

    val qrPayload = PairingQrPayload.parse(qrPayloadString).getOrThrow()
    if (now > qrPayload.expiresAtEpochMillis) {
      throw PairingException.ExpiredSession("QR pairing payload has expired")
    }

    val remoteIdentity = RemoteDeviceIdentity(
      deviceId = qrPayload.initiatorDeviceId,
      publicKeyBase64 = qrPayload.initiatorPublicKeyBase64,
      displayName = qrPayload.initiatorDisplayName,
    )

    if (!remoteIdentity.verifyDeviceId()) {
      throw PairingException.DeviceIdMismatch(
        claimedId = qrPayload.initiatorDeviceId,
        computedId = "unmatched_public_key_digest",
      )
    }

    if (remoteIdentity.deviceId == localIdentity.id) {
      throw PairingException.SelfPairingProhibited("Cannot pair a device with itself")
    }

    val existingContact = contactRepository.getById(remoteIdentity.deviceId)
    if (existingContact != null && existingContact.pairingStatus == PairingStatus.BLOCKED) {
      throw PairingException.Unauthorized("Device is blocked: ${remoteIdentity.deviceId}")
    }
    if (existingContact != null && existingContact.pairingStatus == PairingStatus.PAIRED) {
      throw PairingException.ContactAlreadyPaired(remoteIdentity.deviceId)
    }

    val normalizedResponderName = RemoteDeviceIdentity.normalizeDisplayName(responderDisplayName)
    if (normalizedResponderName.isBlank()) {
      throw PairingException.MalformedPayload("Responder display name cannot be blank")
    }

    val isAuthorized = securityGate.verifyAuthorization(authorizationToken, SecurityGateScope.APPROVE_TRUSTED_DEVICE) ||
      securityGate.verifyAuthorization(authorizationToken, SecurityGateScope.CONFIRM_PAIRING_REQUEST)

    if (!isAuthorized) {
      throw PairingException.Unauthorized("SecurityGate authorization token was rejected")
    }
    securityGate.consumeAuthorization(authorizationToken)

    val responderNonceBytes = ByteArray(16).also(secureRandom::nextBytes)
    val responderNonceBase64 = StrictBase64.encode(responderNonceBytes)

    val transcript = PairingTranscript(
      protocolVersion = qrPayload.version,
      sessionId = qrPayload.sessionId,
      initiatorDeviceId = qrPayload.initiatorDeviceId,
      initiatorPublicKeyBase64 = qrPayload.initiatorPublicKeyBase64,
      initiatorNonceBase64 = qrPayload.initiatorNonceBase64,
      responderDeviceId = localIdentity.id,
      responderPublicKeyBase64 = localIdentity.publicKey,
      responderNonceBase64 = responderNonceBase64,
      expiresAtEpochMillis = qrPayload.expiresAtEpochMillis,
    )

    val signResult = deviceIdentity.sign(transcript.toCanonicalBytes())
    val responderSignatureBase64 = StrictBase64.encode(signResult.getOrThrow())

    // Save contact as PENDING initially — never PAIRED
    saveContactWithStatus(
      id = remoteIdentity.deviceId,
      displayName = remoteIdentity.displayName,
      publicKey = remoteIdentity.publicKeyBase64,
      status = PairingStatus.PENDING,
      now = now,
    )

    val request = PairingRequest(
      protocolVersion = qrPayload.version,
      sessionId = qrPayload.sessionId,
      responderDeviceId = localIdentity.id,
      responderPublicKeyBase64 = localIdentity.publicKey,
      responderDisplayName = normalizedResponderName,
      responderNonceBase64 = responderNonceBase64,
      responderSignatureBase64 = responderSignatureBase64,
    )

    val responderSession = PairingSession(
      sessionId = qrPayload.sessionId,
      role = PairingRole.RESPONDER,
      state = PairingSessionState.WAITING_FOR_REMOTE_RESPONSE,
      localDeviceId = localIdentity.id,
      localPublicKeyBase64 = localIdentity.publicKey,
      localDisplayName = normalizedResponderName,
      localNonceBase64 = responderNonceBase64,
      remoteIdentity = remoteIdentity,
      remoteNonceBase64 = qrPayload.initiatorNonceBase64,
      transcript = transcript,
      localSignatureBase64 = responderSignatureBase64,
      createdAtEpochMillis = now,
      expiresAtEpochMillis = qrPayload.expiresAtEpochMillis,
    )

    activeSessions[qrPayload.sessionId] = responderSession
    PairingRequestResult(responderSession, request)
  }

  override suspend fun receivePairingRequest(
    session: PairingSession,
    request: PairingRequest,
  ): Result<PairingSession> = runCatching {
    getMutex(session.sessionId).withLock {
      val currentSession = activeSessions[session.sessionId] ?: session
      if (currentSession.role != PairingRole.INITIATOR) {
        throw PairingException.InvalidStateTransition(currentSession.state, PairingSessionState.REQUEST_RECEIVED)
      }

      if (currentSession.state != PairingSessionState.QR_READY) {
        throw PairingException.InvalidStateTransition(currentSession.state, PairingSessionState.REQUEST_RECEIVED)
      }

      if (currentSession.isExpired(timeProvider())) {
        val expired = currentSession.copy(state = PairingSessionState.EXPIRED)
        activeSessions[currentSession.sessionId] = expired
        throw PairingException.ExpiredSession("Pairing session ${currentSession.sessionId} has expired")
      }

      if (request.sessionId != currentSession.sessionId) {
        throw PairingException.MalformedPayload("Session ID mismatch: expected ${currentSession.sessionId} but got ${request.sessionId}")
      }

      if (request.protocolVersion != PairingQrPayload.CURRENT_VERSION) {
        throw PairingException.ProtocolVersionMismatch("Expected version ${PairingQrPayload.CURRENT_VERSION} but got ${request.protocolVersion}")
      }

      val remoteIdentity = RemoteDeviceIdentity(
        deviceId = request.responderDeviceId,
        publicKeyBase64 = request.responderPublicKeyBase64,
        displayName = RemoteDeviceIdentity.normalizeDisplayName(request.responderDisplayName),
      )

      if (!remoteIdentity.verifyDeviceId()) {
        throw PairingException.DeviceIdMismatch(request.responderDeviceId, "unmatched_public_key_digest")
      }

      if (remoteIdentity.deviceId == currentSession.localDeviceId) {
        throw PairingException.SelfPairingProhibited("Cannot pair with self")
      }

      val existingContact = contactRepository.getById(remoteIdentity.deviceId)
      if (existingContact != null && existingContact.pairingStatus == PairingStatus.BLOCKED) {
        throw PairingException.Unauthorized("Device is blocked: ${remoteIdentity.deviceId}")
      }
      if (existingContact != null && existingContact.pairingStatus == PairingStatus.PAIRED) {
        throw PairingException.ContactAlreadyPaired(remoteIdentity.deviceId)
      }

      val nonceBytes = StrictBase64.decode(request.responderNonceBase64)
      if (nonceBytes.size != 16) {
        throw PairingException.MalformedPayload("Responder nonce must be exactly 16 bytes")
      }

      val transcript = PairingTranscript(
        protocolVersion = request.protocolVersion,
        sessionId = currentSession.sessionId,
        initiatorDeviceId = currentSession.localDeviceId,
        initiatorPublicKeyBase64 = currentSession.localPublicKeyBase64,
        initiatorNonceBase64 = currentSession.localNonceBase64,
        responderDeviceId = request.responderDeviceId,
        responderPublicKeyBase64 = request.responderPublicKeyBase64,
        responderNonceBase64 = request.responderNonceBase64,
        expiresAtEpochMillis = currentSession.expiresAtEpochMillis,
      )

      val signatureBytes = StrictBase64.decode(request.responderSignatureBase64)
      val isSignatureValid = deviceIdentity.verify(
        publicKeyBase64 = request.responderPublicKeyBase64,
        transcript = transcript.toCanonicalBytes(),
        signature = signatureBytes,
      )

      if (!isSignatureValid) {
        val failed = currentSession.copy(state = PairingSessionState.FAILED)
        activeSessions[currentSession.sessionId] = failed
        throw PairingException.InvalidSignature("Responder signature verification failed on canonical transcript")
      }

      PairingStateValidator.validateTransition(
        PairingRole.INITIATOR,
        currentSession.state,
        PairingSessionState.REQUEST_RECEIVED,
      )

      PairingStateValidator.validateTransition(
        PairingRole.INITIATOR,
        PairingSessionState.REQUEST_RECEIVED,
        PairingSessionState.VERIFIED,
      )

      PairingStateValidator.validateTransition(
        PairingRole.INITIATOR,
        PairingSessionState.VERIFIED,
        PairingSessionState.WAITING_FOR_LOCAL_APPROVAL,
      )

      // Persist contact as PENDING initially
      saveContactWithStatus(
        id = remoteIdentity.deviceId,
        displayName = remoteIdentity.displayName,
        publicKey = remoteIdentity.publicKeyBase64,
        status = PairingStatus.PENDING,
        now = timeProvider(),
      )

      val updated = currentSession.copy(
        state = PairingSessionState.WAITING_FOR_LOCAL_APPROVAL,
        remoteIdentity = remoteIdentity,
        remoteNonceBase64 = request.responderNonceBase64,
        transcript = transcript,
        remoteSignatureBase64 = request.responderSignatureBase64,
      )
      activeSessions[currentSession.sessionId] = updated
      updated
    }
  }

  override suspend fun approvePairingRequest(
    session: PairingSession,
    authorizationToken: AuthorizationToken,
  ): Result<PairingApprovalResult> = runCatching {
    getMutex(session.sessionId).withLock {
      val currentSession = activeSessions[session.sessionId] ?: session
      if (currentSession.role != PairingRole.INITIATOR) {
        throw PairingException.InvalidStateTransition(currentSession.state, PairingSessionState.COMPLETION_SENT)
      }

      if (currentSession.state != PairingSessionState.WAITING_FOR_LOCAL_APPROVAL) {
        throw PairingException.InvalidStateTransition(currentSession.state, PairingSessionState.COMPLETION_SENT)
      }

      if (currentSession.isExpired(timeProvider())) {
        val expired = currentSession.copy(state = PairingSessionState.EXPIRED)
        activeSessions[currentSession.sessionId] = expired
        throw PairingException.ExpiredSession("Pairing session ${currentSession.sessionId} has expired")
      }

      val isAuthorized = securityGate.verifyAuthorization(authorizationToken, SecurityGateScope.APPROVE_TRUSTED_DEVICE) ||
        securityGate.verifyAuthorization(authorizationToken, SecurityGateScope.CONFIRM_PAIRING_REQUEST)

      if (!isAuthorized) {
        throw PairingException.Unauthorized("SecurityGate authorization token was rejected")
      }
      securityGate.consumeAuthorization(authorizationToken)

      PairingStateValidator.validateTransition(
        PairingRole.INITIATOR,
        currentSession.state,
        PairingSessionState.LOCAL_APPROVED,
      )

      PairingStateValidator.validateTransition(
        PairingRole.INITIATOR,
        PairingSessionState.LOCAL_APPROVED,
        PairingSessionState.COMPLETION_SENT,
      )

      val transcript = currentSession.transcript
        ?: throw PairingException.InvalidStateTransition(currentSession.state, PairingSessionState.COMPLETION_SENT)

      val signResult = deviceIdentity.sign(transcript.toCanonicalBytes())
      val initiatorSignatureBase64 = StrictBase64.encode(signResult.getOrThrow())

      val response = PairingResponse(
        protocolVersion = transcript.protocolVersion,
        sessionId = currentSession.sessionId,
        initiatorDeviceId = currentSession.localDeviceId,
        initiatorSignatureBase64 = initiatorSignatureBase64,
      )

      // Initiator enters COMPLETION_SENT — contact remains PENDING!
      val updatedSession = currentSession.copy(
        state = PairingSessionState.COMPLETION_SENT,
        localSignatureBase64 = initiatorSignatureBase64,
      )

      activeSessions[currentSession.sessionId] = updatedSession
      PairingApprovalResult(updatedSession, response)
    }
  }

  override suspend fun finalizeResponderPairing(
    session: PairingSession,
    response: PairingResponse,
  ): Result<PairingConfirmationResult> = runCatching {
    getMutex(session.sessionId).withLock {
      val currentSession = activeSessions[session.sessionId] ?: session
      if (currentSession.role != PairingRole.RESPONDER) {
        throw PairingException.InvalidStateTransition(currentSession.state, PairingSessionState.PAIRED)
      }

      // Idempotency check: if Responder is already PAIRED for this session, re-return confirmation
      if (currentSession.state == PairingSessionState.PAIRED && currentSession.confirmationSignatureBase64 != null) {
        val cachedConfirmation = PairingConfirmation(
          protocolVersion = currentSession.transcript?.protocolVersion ?: PairingQrPayload.CURRENT_VERSION,
          sessionId = currentSession.sessionId,
          responderDeviceId = currentSession.localDeviceId,
          confirmationSignatureBase64 = currentSession.confirmationSignatureBase64,
        )
        return@withLock PairingConfirmationResult(currentSession, cachedConfirmation)
      }

      if (currentSession.state != PairingSessionState.WAITING_FOR_REMOTE_RESPONSE) {
        throw PairingException.InvalidStateTransition(currentSession.state, PairingSessionState.PAIRED)
      }

      if (currentSession.isExpired(timeProvider())) {
        val expired = currentSession.copy(state = PairingSessionState.EXPIRED)
        activeSessions[currentSession.sessionId] = expired
        throw PairingException.ExpiredSession("Pairing session ${currentSession.sessionId} has expired")
      }

      if (response.sessionId != currentSession.sessionId) {
        throw PairingException.MalformedPayload("Session ID mismatch: expected ${currentSession.sessionId} but got ${response.sessionId}")
      }

      val transcript = currentSession.transcript
        ?: throw PairingException.InvalidStateTransition(currentSession.state, PairingSessionState.PAIRED)

      val remote = currentSession.remoteIdentity
        ?: throw PairingException.MalformedPayload("Remote identity missing from responder session")

      val signatureBytes = StrictBase64.decode(response.initiatorSignatureBase64)
      val isSignatureValid = deviceIdentity.verify(
        publicKeyBase64 = remote.publicKeyBase64,
        transcript = transcript.toCanonicalBytes(),
        signature = signatureBytes,
      )

      if (!isSignatureValid) {
        val failed = currentSession.copy(state = PairingSessionState.FAILED)
        activeSessions[currentSession.sessionId] = failed
        throw PairingException.InvalidSignature("Initiator signature verification failed on canonical transcript")
      }

      PairingStateValidator.validateTransition(
        PairingRole.RESPONDER,
        currentSession.state,
        PairingSessionState.COMPLETION_RECEIVED,
      )

      PairingStateValidator.validateTransition(
        PairingRole.RESPONDER,
        PairingSessionState.COMPLETION_RECEIVED,
        PairingSessionState.PAIRED,
      )

      // Generate confirmation signature over canonical confirmation transcript
      val confirmationTranscript = PairingConfirmationTranscript(
        protocolVersion = transcript.protocolVersion,
        sessionId = currentSession.sessionId,
        initiatorSignatureBase64 = response.initiatorSignatureBase64,
        responderSignatureBase64 = currentSession.localSignatureBase64
          ?: throw PairingException.MalformedPayload("Responder local signature missing"),
      )

      val confirmSignResult = deviceIdentity.sign(confirmationTranscript.toCanonicalBytes())
      val confirmSignatureBase64 = StrictBase64.encode(confirmSignResult.getOrThrow())

      // Persist contact as PAIRED on Responder
      saveContactWithStatus(
        id = remote.deviceId,
        displayName = remote.displayName,
        publicKey = remote.publicKeyBase64,
        status = PairingStatus.PAIRED,
        now = timeProvider(),
      )

      val confirmation = PairingConfirmation(
        protocolVersion = transcript.protocolVersion,
        sessionId = currentSession.sessionId,
        responderDeviceId = currentSession.localDeviceId,
        confirmationSignatureBase64 = confirmSignatureBase64,
      )

      val completedSession = currentSession.copy(
        state = PairingSessionState.PAIRED,
        remoteSignatureBase64 = response.initiatorSignatureBase64,
        confirmationSignatureBase64 = confirmSignatureBase64,
      )

      activeSessions[currentSession.sessionId] = completedSession
      PairingConfirmationResult(completedSession, confirmation)
    }
  }

  override suspend fun confirmInitiatorPairing(
    session: PairingSession,
    confirmation: PairingConfirmation,
  ): Result<PairingSession> = runCatching {
    getMutex(session.sessionId).withLock {
      val currentSession = activeSessions[session.sessionId] ?: session
      if (currentSession.role != PairingRole.INITIATOR) {
        throw PairingException.InvalidStateTransition(currentSession.state, PairingSessionState.PAIRED)
      }

      // Idempotency check: if Initiator is already PAIRED, return successfully
      if (currentSession.state == PairingSessionState.PAIRED) {
        return@withLock currentSession
      }

      if (currentSession.state != PairingSessionState.COMPLETION_SENT) {
        throw PairingException.InvalidStateTransition(currentSession.state, PairingSessionState.PAIRED)
      }

      if (currentSession.isExpired(timeProvider())) {
        val expired = currentSession.copy(state = PairingSessionState.EXPIRED)
        activeSessions[currentSession.sessionId] = expired
        throw PairingException.ExpiredSession("Pairing session ${currentSession.sessionId} has expired")
      }

      if (confirmation.sessionId != currentSession.sessionId) {
        throw PairingException.MalformedPayload("Session ID mismatch: expected ${currentSession.sessionId} but got ${confirmation.sessionId}")
      }

      val remote = currentSession.remoteIdentity
        ?: throw PairingException.MalformedPayload("Remote identity missing from initiator session")

      val initiatorSignature = currentSession.localSignatureBase64
        ?: throw PairingException.MalformedPayload("Initiator local signature missing")

      val responderSignature = currentSession.remoteSignatureBase64
        ?: throw PairingException.MalformedPayload("Responder signature missing")

      val confirmationTranscript = PairingConfirmationTranscript(
        protocolVersion = confirmation.protocolVersion,
        sessionId = currentSession.sessionId,
        initiatorSignatureBase64 = initiatorSignature,
        responderSignatureBase64 = responderSignature,
      )

      val confirmSigBytes = StrictBase64.decode(confirmation.confirmationSignatureBase64)
      val isConfirmSigValid = deviceIdentity.verify(
        publicKeyBase64 = remote.publicKeyBase64,
        transcript = confirmationTranscript.toCanonicalBytes(),
        signature = confirmSigBytes,
      )

      if (!isConfirmSigValid) {
        val failed = currentSession.copy(state = PairingSessionState.FAILED)
        activeSessions[currentSession.sessionId] = failed
        throw PairingException.InvalidSignature("Responder confirmation signature verification failed")
      }

      PairingStateValidator.validateTransition(
        PairingRole.INITIATOR,
        currentSession.state,
        PairingSessionState.COMPLETION_RECEIVED,
      )

      PairingStateValidator.validateTransition(
        PairingRole.INITIATOR,
        PairingSessionState.COMPLETION_RECEIVED,
        PairingSessionState.PAIRED,
      )

      // Promote contact from PENDING to PAIRED only after confirmation verified!
      saveContactWithStatus(
        id = remote.deviceId,
        displayName = remote.displayName,
        publicKey = remote.publicKeyBase64,
        status = PairingStatus.PAIRED,
        now = timeProvider(),
      )

      val completedSession = currentSession.copy(
        state = PairingSessionState.PAIRED,
        confirmationSignatureBase64 = confirmation.confirmationSignatureBase64,
      )

      activeSessions[currentSession.sessionId] = completedSession
      completedSession
    }
  }

  override suspend fun cancelSession(session: PairingSession): Result<PairingSession> = runCatching {
    getMutex(session.sessionId).withLock {
      val currentSession = activeSessions[session.sessionId] ?: session
      if (currentSession.state == PairingSessionState.PAIRED) {
        throw PairingException.InvalidStateTransition(currentSession.state, PairingSessionState.REJECTED)
      }

      val cancelledSession = currentSession.copy(state = PairingSessionState.REJECTED)
      activeSessions[currentSession.sessionId] = cancelledSession

      // If contact was unconfirmed in PENDING, clean up temporary record without overwriting valid pairings
      currentSession.remoteIdentity?.let { remote ->
        try {
          val existing = contactRepository.getById(remote.deviceId)
          if (existing != null && existing.pairingStatus == PairingStatus.PENDING) {
            contactRepository.delete(existing.id)
          }
        } catch (_: Throwable) {}
      }

      cancelledSession
    }
  }

  override suspend fun completeDirectPairing(
    remoteIdentity: RemoteDeviceIdentity,
  ): Result<TrustedContact> = runCatching {
    if (!remoteIdentity.verifyDeviceId()) {
      throw PairingException.DeviceIdMismatch(remoteIdentity.deviceId, "Public key digest mismatch")
    }
    val localId = getReadyIdentity().id
    if (remoteIdentity.deviceId == localId) {
      throw PairingException.SelfPairingProhibited("Cannot pair a device with itself")
    }
    val contact = TrustedContact(
      id = remoteIdentity.deviceId,
      displayName = remoteIdentity.displayName,
      publicIdentity = remoteIdentity.publicKeyBase64,
      pairingStatus = PairingStatus.PAIRED,
      createdAtEpochMillis = timeProvider(),
    )
    val existing = contactRepository.getById(contact.id)
    if (existing == null) {
      contactRepository.add(contact)
    } else {
      contactRepository.update(contact)
    }
    contact
  }

  private suspend fun saveContactWithStatus(
    id: String,
    displayName: String,
    publicKey: String,
    status: PairingStatus,
    now: Long,
  ) {
    try {
      val contact = TrustedContact(
        id = id,
        displayName = displayName,
        publicIdentity = publicKey,
        pairingStatus = status,
        createdAtEpochMillis = now,
      )
      val existing = contactRepository.getById(contact.id)
      if (existing == null) {
        contactRepository.add(contact)
      } else {
        contactRepository.update(contact)
      }
    } catch (e: Throwable) {
      throw PairingException.PersistenceFailure("Failed to persist contact with status $status: ${e.message}", e)
    }
  }

  private fun getReadyIdentity(): DeviceIdentityInfo {
    return when (val current = deviceIdentity.identityState.value) {
      is DeviceIdentityState.Ready -> current.identity
      else -> throw PairingException.Unauthorized("Device identity is not initialized")
    }
  }
}
