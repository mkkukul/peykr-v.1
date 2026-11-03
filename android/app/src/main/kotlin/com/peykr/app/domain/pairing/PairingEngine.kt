package com.peykr.app.domain.pairing

import com.peykr.app.core.auth.AuthorizationToken

data class PairingRequestResult(
  val session: PairingSession,
  val request: PairingRequest,
)

data class PairingApprovalResult(
  val session: PairingSession,
  val response: PairingResponse,
)

data class PairingConfirmationResult(
  val session: PairingSession,
  val confirmation: PairingConfirmation,
)

interface PairingEngine {
  fun getSession(sessionId: String): PairingSession?

  suspend fun createInitiatorSession(
    initiatorDisplayName: String,
    validityDurationMillis: Long = 5 * 60 * 1000L,
  ): Result<PairingSession>

  suspend fun generateQrPayload(session: PairingSession): Result<PairingQrPayload>

  suspend fun processQrAndCreateRequest(
    qrPayloadString: String,
    responderDisplayName: String,
    authorizationToken: AuthorizationToken,
  ): Result<PairingRequestResult>

  suspend fun receivePairingRequest(
    session: PairingSession,
    request: PairingRequest,
  ): Result<PairingSession>

  suspend fun approvePairingRequest(
    session: PairingSession,
    authorizationToken: AuthorizationToken,
  ): Result<PairingApprovalResult>

  suspend fun finalizeResponderPairing(
    session: PairingSession,
    response: PairingResponse,
  ): Result<PairingConfirmationResult>

  suspend fun confirmInitiatorPairing(
    session: PairingSession,
    confirmation: PairingConfirmation,
  ): Result<PairingSession>

  suspend fun cancelSession(session: PairingSession): Result<PairingSession>
}
