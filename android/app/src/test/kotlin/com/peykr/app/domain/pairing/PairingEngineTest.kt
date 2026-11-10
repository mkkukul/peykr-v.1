package com.peykr.app.domain.pairing

import com.peykr.app.core.auth.AuthPromptConfig
import com.peykr.app.core.auth.AuthenticationResult
import com.peykr.app.core.auth.BiometricAuthenticator
import com.peykr.app.core.auth.BiometricAvailability
import com.peykr.app.core.auth.DefaultSecurityGate
import com.peykr.app.core.auth.SecurityGateScope
import com.peykr.app.core.identity.DeviceIdentity
import com.peykr.app.core.identity.DeviceIdentityInfo
import com.peykr.app.core.identity.DeviceIdentityResult
import com.peykr.app.core.identity.DeviceIdentityState
import com.peykr.app.domain.contacts.PairingStatus
import com.peykr.app.domain.contacts.TrustedContact
import com.peykr.app.domain.contacts.TrustedContactRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Locale
import java.util.UUID

class PairingEngineTest {

  private val promptConfig = AuthPromptConfig("Test Title", description = "Test Desc")

  @Test
  fun validPairingTranscript_producesDeterministicCanonicalBytes() {
    val transcript1 = PairingTranscript(
      protocolVersion = 1,
      sessionId = "session-123",
      initiatorDeviceId = "dev-a",
      initiatorPublicKeyBase64 = "pk-a",
      initiatorNonceBase64 = "nonce-a",
      responderDeviceId = "dev-b",
      responderPublicKeyBase64 = "pk-b",
      responderNonceBase64 = "nonce-b",
      expiresAtEpochMillis = 5000L,
    )
    val transcript2 = PairingTranscript(
      protocolVersion = 1,
      sessionId = "session-123",
      initiatorDeviceId = "dev-a",
      initiatorPublicKeyBase64 = "pk-a",
      initiatorNonceBase64 = "nonce-a",
      responderDeviceId = "dev-b",
      responderPublicKeyBase64 = "pk-b",
      responderNonceBase64 = "nonce-b",
      expiresAtEpochMillis = 5000L,
    )

    assertTrue(transcript1.toCanonicalBytes().contentEquals(transcript2.toCanonicalBytes()))
  }

  @Test
  fun mutualApproval_completeHandshake_persistsPairedContacts() = runTest {
    var currentTime = 100_000L
    val timeProvider: () -> Long = { currentTime }

    val deviceAIdentity = TestDeviceIdentity("DeviceA")
    val authA = FakeBiometricAuthenticator()
    val gateA = DefaultSecurityGate(authA, timeProvider = timeProvider)
    val repoA = InMemoryContactRepository()
    val engineA = DefaultPairingEngine(deviceAIdentity, gateA, repoA, timeProvider = timeProvider)

    val deviceBIdentity = TestDeviceIdentity("DeviceB")
    val authB = FakeBiometricAuthenticator()
    val gateB = DefaultSecurityGate(authB, timeProvider = timeProvider)
    val repoB = InMemoryContactRepository()
    val engineB = DefaultPairingEngine(deviceBIdentity, gateB, repoB, timeProvider = timeProvider)

    // 1. Device A creates session and generates QR
    val sessionA = engineA.createInitiatorSession("Alice's Phone").getOrThrow()
    val qrPayload = engineA.generateQrPayload(sessionA).getOrThrow().serialize()

    // 2. Device B processes QR and creates request (contact on B is PENDING)
    val tokenB = gateB.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val (sessionB, request) = engineB.processQrAndCreateRequest(qrPayload, "Bob's Phone", tokenB).getOrThrow()
    assertEquals(PairingSessionState.WAITING_FOR_REMOTE_RESPONSE, sessionB.state)
    assertEquals(PairingStatus.PENDING, repoB.getById(deviceAIdentity.deviceId)?.pairingStatus)

    // 3. Device A receives request (contact on A is PENDING)
    val sessionAWaiting = engineA.receivePairingRequest(sessionA, request).getOrThrow()
    assertEquals(PairingSessionState.WAITING_FOR_LOCAL_APPROVAL, sessionAWaiting.state)
    assertEquals(PairingStatus.PENDING, repoA.getById(deviceBIdentity.deviceId)?.pairingStatus)

    // 4. Device A approves request -> COMPLETION_SENT (contact on A remains PENDING!)
    val tokenA = gateA.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val (sessionASent, response) = engineA.approvePairingRequest(sessionAWaiting, tokenA).getOrThrow()
    assertEquals(PairingSessionState.COMPLETION_SENT, sessionASent.state)
    assertEquals(PairingStatus.PENDING, repoA.getById(deviceBIdentity.deviceId)?.pairingStatus)

    // 5. Device B finalizes pairing -> Responder becomes PAIRED, emits confirmation
    val (sessionBFinal, confirmation) = engineB.finalizeResponderPairing(sessionB, response).getOrThrow()
    assertEquals(PairingSessionState.PAIRED, sessionBFinal.state)
    assertEquals(PairingStatus.PAIRED, repoB.getById(deviceAIdentity.deviceId)?.pairingStatus)

    // 6. Device A verifies confirmation -> Initiator becomes PAIRED
    val sessionAFinal = engineA.confirmInitiatorPairing(sessionASent, confirmation).getOrThrow()
    assertEquals(PairingSessionState.PAIRED, sessionAFinal.state)
    assertEquals(PairingStatus.PAIRED, repoA.getById(deviceBIdentity.deviceId)?.pairingStatus)
  }

  @Test
  fun initiatorCrash_afterSendingFinalMessage_remainsPendingInDatabase() = runTest {
    var currentTime = 100_000L
    val timeProvider: () -> Long = { currentTime }

    val deviceA = TestDeviceIdentity("DeviceA")
    val gateA = DefaultSecurityGate(FakeBiometricAuthenticator(), timeProvider = timeProvider)
    val repoA = InMemoryContactRepository()
    val engineA = DefaultPairingEngine(deviceA, gateA, repoA, timeProvider = timeProvider)

    val deviceB = TestDeviceIdentity("DeviceB")
    val gateB = DefaultSecurityGate(FakeBiometricAuthenticator(), timeProvider = timeProvider)
    val repoB = InMemoryContactRepository()
    val engineB = DefaultPairingEngine(deviceB, gateB, repoB, timeProvider = timeProvider)

    val sessionA = engineA.createInitiatorSession("Alice").getOrThrow()
    val qr = engineA.generateQrPayload(sessionA).getOrThrow().serialize()

    val tokenB = gateB.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val (_, request) = engineB.processQrAndCreateRequest(qr, "Bob", tokenB).getOrThrow()

    val sessionAWaiting = engineA.receivePairingRequest(sessionA, request).getOrThrow()
    val tokenA = gateA.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val (sessionASent, _) = engineA.approvePairingRequest(sessionAWaiting, tokenA).getOrThrow()

    // Initiator emitted final message, but crashed before receiving confirmation
    assertEquals(PairingSessionState.COMPLETION_SENT, sessionASent.state)
    val contactOnA = repoA.getById(deviceB.deviceId)
    assertNotNull(contactOnA)
    assertEquals(PairingStatus.PENDING, contactOnA?.pairingStatus)
  }

  @Test
  fun responderCrash_beforeReceivingCompletion_remainsPendingInDatabase() = runTest {
    var currentTime = 100_000L
    val timeProvider: () -> Long = { currentTime }

    val deviceA = TestDeviceIdentity("DeviceA")
    val gateA = DefaultSecurityGate(FakeBiometricAuthenticator(), timeProvider = timeProvider)
    val repoA = InMemoryContactRepository()
    val engineA = DefaultPairingEngine(deviceA, gateA, repoA, timeProvider = timeProvider)

    val deviceB = TestDeviceIdentity("DeviceB")
    val gateB = DefaultSecurityGate(FakeBiometricAuthenticator(), timeProvider = timeProvider)
    val repoB = InMemoryContactRepository()
    val engineB = DefaultPairingEngine(deviceB, gateB, repoB, timeProvider = timeProvider)

    val sessionA = engineA.createInitiatorSession("Alice").getOrThrow()
    val qr = engineA.generateQrPayload(sessionA).getOrThrow().serialize()

    val tokenB = gateB.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val (sessionB, _) = engineB.processQrAndCreateRequest(qr, "Bob", tokenB).getOrThrow()

    // Responder sent request, but crashed before receiving response
    assertEquals(PairingSessionState.WAITING_FOR_REMOTE_RESPONSE, sessionB.state)
    val contactOnB = repoB.getById(deviceA.deviceId)
    assertNotNull(contactOnB)
    assertEquals(PairingStatus.PENDING, contactOnB?.pairingStatus)
  }

  @Test
  fun completionResponseLost_initiatorDoesNotClaimPaired() = runTest {
    val deviceA = TestDeviceIdentity("DeviceA")
    val gateA = DefaultSecurityGate(FakeBiometricAuthenticator())
    val repoA = InMemoryContactRepository()
    val engineA = DefaultPairingEngine(deviceA, gateA, repoA)

    val deviceB = TestDeviceIdentity("DeviceB")
    val gateB = DefaultSecurityGate(FakeBiometricAuthenticator())
    val repoB = InMemoryContactRepository()
    val engineB = DefaultPairingEngine(deviceB, gateB, repoB)

    val sessionA = engineA.createInitiatorSession("Alice").getOrThrow()
    val qr = engineA.generateQrPayload(sessionA).getOrThrow().serialize()

    val tokenB = gateB.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val (_, request) = engineB.processQrAndCreateRequest(qr, "Bob", tokenB).getOrThrow()

    val sessionAWaiting = engineA.receivePairingRequest(sessionA, request).getOrThrow()
    val tokenA = gateA.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val (sessionASent, _) = engineA.approvePairingRequest(sessionAWaiting, tokenA).getOrThrow()

    // Completion response lost in transmission
    assertEquals(PairingSessionState.COMPLETION_SENT, sessionASent.state)
    // Initiator does NOT claim PAIRED
    assertEquals(PairingStatus.PENDING, repoA.getById(deviceB.deviceId)?.pairingStatus)
  }

  @Test
  fun duplicateCompletionResponse_responderIdempotentlyReReturnsConfirmation() = runTest {
    val deviceA = TestDeviceIdentity("DeviceA")
    val gateA = DefaultSecurityGate(FakeBiometricAuthenticator())
    val repoA = InMemoryContactRepository()
    val engineA = DefaultPairingEngine(deviceA, gateA, repoA)

    val deviceB = TestDeviceIdentity("DeviceB")
    val gateB = DefaultSecurityGate(FakeBiometricAuthenticator())
    val repoB = InMemoryContactRepository()
    val engineB = DefaultPairingEngine(deviceB, gateB, repoB)

    val sessionA = engineA.createInitiatorSession("Alice").getOrThrow()
    val qr = engineA.generateQrPayload(sessionA).getOrThrow().serialize()

    val tokenB = gateB.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val (sessionB, request) = engineB.processQrAndCreateRequest(qr, "Bob", tokenB).getOrThrow()

    val sessionAWaiting = engineA.receivePairingRequest(sessionA, request).getOrThrow()
    val tokenA = gateA.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val (_, response) = engineA.approvePairingRequest(sessionAWaiting, tokenA).getOrThrow()

    val (sessionB1, confirm1) = engineB.finalizeResponderPairing(sessionB, response).getOrThrow()
    assertEquals(PairingSessionState.PAIRED, sessionB1.state)

    // Retransmit same response (e.g. Initiator didn't receive confirm1)
    val (sessionB2, confirm2) = engineB.finalizeResponderPairing(sessionB1, response).getOrThrow()
    assertEquals(PairingSessionState.PAIRED, sessionB2.state)
    assertEquals(confirm1.confirmationSignatureBase64, confirm2.confirmationSignatureBase64)
  }

  @Test
  fun duplicateCompletionConfirmation_initiatorIdempotentlyAccepts() = runTest {
    val deviceA = TestDeviceIdentity("DeviceA")
    val gateA = DefaultSecurityGate(FakeBiometricAuthenticator())
    val repoA = InMemoryContactRepository()
    val engineA = DefaultPairingEngine(deviceA, gateA, repoA)

    val deviceB = TestDeviceIdentity("DeviceB")
    val gateB = DefaultSecurityGate(FakeBiometricAuthenticator())
    val repoB = InMemoryContactRepository()
    val engineB = DefaultPairingEngine(deviceB, gateB, repoB)

    val sessionA = engineA.createInitiatorSession("Alice").getOrThrow()
    val qr = engineA.generateQrPayload(sessionA).getOrThrow().serialize()

    val tokenB = gateB.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val (sessionB, request) = engineB.processQrAndCreateRequest(qr, "Bob", tokenB).getOrThrow()

    val sessionAWaiting = engineA.receivePairingRequest(sessionA, request).getOrThrow()
    val tokenA = gateA.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val (sessionASent, response) = engineA.approvePairingRequest(sessionAWaiting, tokenA).getOrThrow()

    val (_, confirm) = engineB.finalizeResponderPairing(sessionB, response).getOrThrow()

    val sessionAFinal1 = engineA.confirmInitiatorPairing(sessionASent, confirm).getOrThrow()
    assertEquals(PairingSessionState.PAIRED, sessionAFinal1.state)

    // Duplicate confirmation received
    val sessionAFinal2 = engineA.confirmInitiatorPairing(sessionAFinal1, confirm).getOrThrow()
    assertEquals(PairingSessionState.PAIRED, sessionAFinal2.state)
  }

  @Test
  fun concurrentCompletion_threadSafety() = runTest {
    val deviceA = TestDeviceIdentity("DeviceA")
    val gateA = DefaultSecurityGate(FakeBiometricAuthenticator())
    val repoA = InMemoryContactRepository()
    val engineA = DefaultPairingEngine(deviceA, gateA, repoA)

    val deviceB = TestDeviceIdentity("DeviceB")
    val gateB = DefaultSecurityGate(FakeBiometricAuthenticator())
    val repoB = InMemoryContactRepository()
    val engineB = DefaultPairingEngine(deviceB, gateB, repoB)

    val sessionA = engineA.createInitiatorSession("Alice").getOrThrow()
    val qr = engineA.generateQrPayload(sessionA).getOrThrow().serialize()

    val tokenB = gateB.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val (sessionB, request) = engineB.processQrAndCreateRequest(qr, "Bob", tokenB).getOrThrow()

    val sessionAWaiting = engineA.receivePairingRequest(sessionA, request).getOrThrow()
    val tokenA = gateA.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val (_, response) = engineA.approvePairingRequest(sessionAWaiting, tokenA).getOrThrow()

    // Run 5 concurrent finalizations on Responder
    val deferredResults = (1..5).map {
      async { engineB.finalizeResponderPairing(sessionB, response) }
    }
    val results = deferredResults.awaitAll()
    assertTrue(results.all { it.isSuccess })
    assertEquals(1, repoB.contactsMap.size)
    assertEquals(PairingStatus.PAIRED, repoB.getById(deviceA.deviceId)?.pairingStatus)
  }

  @Test
  fun persistenceFailure_duringApproval_throwsPersistenceFailure() = runTest {
    val deviceA = TestDeviceIdentity("DeviceA")
    val gateA = DefaultSecurityGate(FakeBiometricAuthenticator())
    val failingRepo = FailingContactRepository()
    val engineA = DefaultPairingEngine(deviceA, gateA, failingRepo)

    val deviceB = TestDeviceIdentity("DeviceB")
    val gateB = DefaultSecurityGate(FakeBiometricAuthenticator())
    val repoB = InMemoryContactRepository()
    val engineB = DefaultPairingEngine(deviceB, gateB, repoB)

    val sessionA = engineA.createInitiatorSession("Alice").getOrThrow()
    val qr = engineA.generateQrPayload(sessionA).getOrThrow().serialize()

    val tokenB = gateB.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val (_, request) = engineB.processQrAndCreateRequest(qr, "Bob", tokenB).getOrThrow()

    val result = engineA.receivePairingRequest(sessionA, request)
    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is PairingException.PersistenceFailure)
  }

  @Test
  fun databaseException_duringResponderFinalization_throwsPersistenceFailure() = runTest {
    val deviceA = TestDeviceIdentity("DeviceA")
    val gateA = DefaultSecurityGate(FakeBiometricAuthenticator())
    val repoA = InMemoryContactRepository()
    val engineA = DefaultPairingEngine(deviceA, gateA, repoA)

    val deviceB = TestDeviceIdentity("DeviceB")
    val gateB = DefaultSecurityGate(FakeBiometricAuthenticator())
    val intermittentRepo = object : TrustedContactRepository {
      val contactsMap = mutableMapOf<String, TrustedContact>()
      var failOnUpdate = false
      override val contacts: Flow<List<TrustedContact>> = MutableStateFlow(emptyList())
      override val initializationState: StateFlow<com.peykr.app.data.contacts.DatabaseState> = MutableStateFlow(com.peykr.app.data.contacts.DatabaseState.READY)
      override fun retry() {}
      override suspend fun getById(id: String): TrustedContact? = contactsMap[id]
      override suspend fun add(contact: TrustedContact) {
        if (failOnUpdate) throw RuntimeException("Disk full")
        contactsMap[contact.id] = contact
      }
      override suspend fun update(contact: TrustedContact) {
        if (failOnUpdate) throw RuntimeException("Disk full")
        contactsMap[contact.id] = contact
      }
      override suspend fun delete(id: String) { contactsMap.remove(id) }
    }
    val engineB = DefaultPairingEngine(deviceB, gateB, intermittentRepo)

    val sessionA = engineA.createInitiatorSession("Alice").getOrThrow()
    val qr = engineA.generateQrPayload(sessionA).getOrThrow().serialize()

    val tokenB = gateB.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val (sessionB, request) = engineB.processQrAndCreateRequest(qr, "Bob", tokenB).getOrThrow()

    val sessionAWaiting = engineA.receivePairingRequest(sessionA, request).getOrThrow()
    val tokenA = gateA.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val (_, response) = engineA.approvePairingRequest(sessionAWaiting, tokenA).getOrThrow()

    // Trigger DB failure on finalization update
    intermittentRepo.failOnUpdate = true
    val finalizeResult = engineB.finalizeResponderPairing(sessionB, response)
    assertTrue(finalizeResult.isFailure)
    assertTrue(finalizeResult.exceptionOrNull() is PairingException.PersistenceFailure)
    // Contact is still PENDING in repository, NOT PAIRED
    assertEquals(PairingStatus.PENDING, intermittentRepo.getById(deviceA.deviceId)?.pairingStatus)
  }

  @Test
  fun processRestart_withPendingSession_contactRemainsPending() = runTest {
    val repo = InMemoryContactRepository()
    val deviceA = TestDeviceIdentity("DeviceA")
    val gateA = DefaultSecurityGate(FakeBiometricAuthenticator())
    val engineA1 = DefaultPairingEngine(deviceA, gateA, repo)

    val deviceB = TestDeviceIdentity("DeviceB")
    val gateB = DefaultSecurityGate(FakeBiometricAuthenticator())
    val engineB1 = DefaultPairingEngine(deviceB, gateB, InMemoryContactRepository())

    val sessionA = engineA1.createInitiatorSession("Alice").getOrThrow()
    val qr = engineA1.generateQrPayload(sessionA).getOrThrow().serialize()

    val tokenB = gateB.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val (_, request) = engineB1.processQrAndCreateRequest(qr, "Bob", tokenB).getOrThrow()

    engineA1.receivePairingRequest(sessionA, request).getOrThrow()

    // Process crash occurs on device A.
    // Create brand new engine instance representing process restart
    val engineA2 = DefaultPairingEngine(deviceA, gateA, repo)

    // Old in-memory session is gone
    val retrieved = engineA2.getSession(sessionA.sessionId)
    assertEquals(null, retrieved)

    // Contact in persistent repository remains PENDING, never automatically PAIRED
    val persistedContact = repo.getById(deviceB.deviceId)
    assertNotNull(persistedContact)
    assertEquals(PairingStatus.PENDING, persistedContact?.pairingStatus)
  }

  @Test
  fun invalidSignature_afterRetry_rejected() = runTest {
    val deviceA = TestDeviceIdentity("DeviceA")
    val gateA = DefaultSecurityGate(FakeBiometricAuthenticator())
    val repoA = InMemoryContactRepository()
    val engineA = DefaultPairingEngine(deviceA, gateA, repoA)

    val deviceB = TestDeviceIdentity("DeviceB")
    val gateB = DefaultSecurityGate(FakeBiometricAuthenticator())
    val repoB = InMemoryContactRepository()
    val engineB = DefaultPairingEngine(deviceB, gateB, repoB)

    val sessionA = engineA.createInitiatorSession("Alice").getOrThrow()
    val qr = engineA.generateQrPayload(sessionA).getOrThrow().serialize()

    val tokenB = gateB.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val (sessionB, request) = engineB.processQrAndCreateRequest(qr, "Bob", tokenB).getOrThrow()

    val sessionAWaiting = engineA.receivePairingRequest(sessionA, request).getOrThrow()
    val tokenA = gateA.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val (_, response) = engineA.approvePairingRequest(sessionAWaiting, tokenA).getOrThrow()

    // Tamper with response signature
    val tamperedSig = StrictBase64.encode(ByteArray(64) { 0x55.toByte() })
    val tamperedResponse = response.copy(initiatorSignatureBase64 = tamperedSig)

    val finalizeResult = engineB.finalizeResponderPairing(sessionB, tamperedResponse)
    assertTrue(finalizeResult.isFailure)
    assertTrue(finalizeResult.exceptionOrNull() is PairingException.InvalidSignature)
    assertEquals(PairingStatus.PENDING, repoB.getById(deviceA.deviceId)?.pairingStatus)
  }

  @Test
  fun sessionExpiration_duringInterruptedFlow_cannotBeCompleted() = runTest {
    var currentTime = 100_000L
    val timeProvider: () -> Long = { currentTime }

    val deviceA = TestDeviceIdentity("DeviceA")
    val gateA = DefaultSecurityGate(FakeBiometricAuthenticator(), timeProvider = timeProvider)
    val repoA = InMemoryContactRepository()
    val engineA = DefaultPairingEngine(deviceA, gateA, repoA, timeProvider = timeProvider)

    val deviceB = TestDeviceIdentity("DeviceB")
    val gateB = DefaultSecurityGate(FakeBiometricAuthenticator(), timeProvider = timeProvider)
    val repoB = InMemoryContactRepository()
    val engineB = DefaultPairingEngine(deviceB, gateB, repoB, timeProvider = timeProvider)

    val sessionA = engineA.createInitiatorSession("Alice", validityDurationMillis = 5000L).getOrThrow()
    val qr = engineA.generateQrPayload(sessionA).getOrThrow().serialize()

    val tokenB = gateB.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val (sessionB, request) = engineB.processQrAndCreateRequest(qr, "Bob", tokenB).getOrThrow()

    val sessionAWaiting = engineA.receivePairingRequest(sessionA, request).getOrThrow()
    val tokenA = gateA.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val (sessionASent, response) = engineA.approvePairingRequest(sessionAWaiting, tokenA).getOrThrow()

    // Advance time past expiration
    currentTime += 10_000L

    val finalizeResult = engineB.finalizeResponderPairing(sessionB, response)
    assertTrue(finalizeResult.isFailure)
    assertTrue(finalizeResult.exceptionOrNull() is PairingException.ExpiredSession)
  }

  @Test
  fun oneDevicePairedWhileOtherRemainsPending_safeAsymmetricState() = runTest {
    val deviceA = TestDeviceIdentity("DeviceA")
    val gateA = DefaultSecurityGate(FakeBiometricAuthenticator())
    val repoA = InMemoryContactRepository()
    val engineA = DefaultPairingEngine(deviceA, gateA, repoA)

    val deviceB = TestDeviceIdentity("DeviceB")
    val gateB = DefaultSecurityGate(FakeBiometricAuthenticator())
    val repoB = InMemoryContactRepository()
    val engineB = DefaultPairingEngine(deviceB, gateB, repoB)

    val sessionA = engineA.createInitiatorSession("Alice").getOrThrow()
    val qr = engineA.generateQrPayload(sessionA).getOrThrow().serialize()

    val tokenB = gateB.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val (sessionB, request) = engineB.processQrAndCreateRequest(qr, "Bob", tokenB).getOrThrow()

    val sessionAWaiting = engineA.receivePairingRequest(sessionA, request).getOrThrow()
    val tokenA = gateA.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val (sessionASent, response) = engineA.approvePairingRequest(sessionAWaiting, tokenA).getOrThrow()

    // Responder receives response and reaches PAIRED
    val (sessionBFinal, _) = engineB.finalizeResponderPairing(sessionB, response).getOrThrow()
    assertEquals(PairingSessionState.PAIRED, sessionBFinal.state)
    assertEquals(PairingStatus.PAIRED, repoB.getById(deviceA.deviceId)?.pairingStatus)

    // Initiator never received confirmation
    assertEquals(PairingSessionState.COMPLETION_SENT, sessionASent.state)
    assertEquals(PairingStatus.PENDING, repoA.getById(deviceB.deviceId)?.pairingStatus)
  }

  @Test
  fun tamperedConfirmationSignature_rejected() = runTest {
    val deviceA = TestDeviceIdentity("DeviceA")
    val gateA = DefaultSecurityGate(FakeBiometricAuthenticator())
    val repoA = InMemoryContactRepository()
    val engineA = DefaultPairingEngine(deviceA, gateA, repoA)

    val deviceB = TestDeviceIdentity("DeviceB")
    val gateB = DefaultSecurityGate(FakeBiometricAuthenticator())
    val repoB = InMemoryContactRepository()
    val engineB = DefaultPairingEngine(deviceB, gateB, repoB)

    val sessionA = engineA.createInitiatorSession("Alice").getOrThrow()
    val qr = engineA.generateQrPayload(sessionA).getOrThrow().serialize()

    val tokenB = gateB.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val (sessionB, request) = engineB.processQrAndCreateRequest(qr, "Bob", tokenB).getOrThrow()

    val sessionAWaiting = engineA.receivePairingRequest(sessionA, request).getOrThrow()
    val tokenA = gateA.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val (sessionASent, response) = engineA.approvePairingRequest(sessionAWaiting, tokenA).getOrThrow()

    val (_, confirm) = engineB.finalizeResponderPairing(sessionB, response).getOrThrow()

    // Tamper confirmation signature
    val tamperedConfirm = confirm.copy(
      confirmationSignatureBase64 = StrictBase64.encode(ByteArray(64) { 0x33.toByte() })
    )

    val confirmResult = engineA.confirmInitiatorPairing(sessionASent, tamperedConfirm)
    assertTrue(confirmResult.isFailure)
    assertTrue(confirmResult.exceptionOrNull() is PairingException.InvalidSignature)
    assertEquals(PairingStatus.PENDING, repoA.getById(deviceB.deviceId)?.pairingStatus)
  }

  @Test
  fun strictBase64_invalidChars_rejected() {
    val invalid = "abc!def"
    try {
      StrictBase64.decode(invalid)
      fail("Should have failed")
    } catch (e: PairingException.StrictDecodingFailure) {
      assertTrue(e.message!!.contains("not valid"))
    }
  }

  @Test
  fun corruptedPublicKeySpec_rejectedInRemoteIdentity() {
    val identity = RemoteDeviceIdentity(
      deviceId = "0000000000000000000000000000000000000000000000000000000000000000",
      publicKeyBase64 = StrictBase64.encode(ByteArray(32) { 0x11 }), // Not an EC public key spec
      displayName = "Malicious",
    )
    assertFalse(identity.verifyDeviceId())
  }

  @Test
  fun cancelledSession_rejectsFurtherOperationsAndCleansUpPendingContact() = runTest {
    val deviceA = TestDeviceIdentity("DeviceA")
    val gateA = DefaultSecurityGate(FakeBiometricAuthenticator())
    val repoA = InMemoryContactRepository()
    val engineA = DefaultPairingEngine(deviceA, gateA, repoA)

    val deviceB = TestDeviceIdentity("DeviceB")
    val gateB = DefaultSecurityGate(FakeBiometricAuthenticator())
    val repoB = InMemoryContactRepository()
    val engineB = DefaultPairingEngine(deviceB, gateB, repoB)

    val sessionA = engineA.createInitiatorSession("Alice").getOrThrow()
    val qr = engineA.generateQrPayload(sessionA).getOrThrow().serialize()

    val tokenB = gateB.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val (_, request) = engineB.processQrAndCreateRequest(qr, "Bob", tokenB).getOrThrow()

    val sessionAWaiting = engineA.receivePairingRequest(sessionA, request).getOrThrow()
    assertEquals(PairingStatus.PENDING, repoA.getById(deviceB.deviceId)?.pairingStatus)

    // User cancels session on Device A
    val cancelled = engineA.cancelSession(sessionAWaiting).getOrThrow()
    assertEquals(PairingSessionState.REJECTED, cancelled.state)
    // Pending unconfirmed contact is removed, never left as NOT_PAIRED to prevent "Eşleşmedi" bugs
    assertNull(repoA.getById(deviceB.deviceId))
  }

  @Test
  fun cancelledSession_neverDemotesAlreadyPairedContact() = runTest {
    val deviceA = TestDeviceIdentity("DeviceA")
    val gateA = DefaultSecurityGate(FakeBiometricAuthenticator())
    val repoA = InMemoryContactRepository()
    val engineA = DefaultPairingEngine(deviceA, gateA, repoA)

    val deviceB = TestDeviceIdentity("DeviceB")
    repoA.add(TrustedContact(deviceB.deviceId, "Bob", deviceB.publicKeyBase64, PairingStatus.PAIRED, 1000L))

    val sessionA = engineA.createInitiatorSession("Alice").getOrThrow()
    val cancelled = engineA.cancelSession(sessionA).getOrThrow()
    assertEquals(PairingSessionState.REJECTED, cancelled.state)
    // Already paired contact must remain PAIRED
    assertEquals(PairingStatus.PAIRED, repoA.getById(deviceB.deviceId)?.pairingStatus)
  }

  @Test
  fun selfPairing_prohibited() = runTest {
    val deviceA = TestDeviceIdentity("DeviceA")
    val gateA = DefaultSecurityGate(FakeBiometricAuthenticator())
    val repoA = InMemoryContactRepository()
    val engineA = DefaultPairingEngine(deviceA, gateA, repoA)

    val sessionA = engineA.createInitiatorSession("Alice").getOrThrow()
    val qr = engineA.generateQrPayload(sessionA).getOrThrow().serialize()

    val token = gateA.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val result = engineA.processQrAndCreateRequest(qr, "Alice", token)

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is PairingException.SelfPairingProhibited)
  }

  @Test
  fun alreadyPairedContact_rejected() = runTest {
    val deviceA = TestDeviceIdentity("DeviceA")
    val gateA = DefaultSecurityGate(FakeBiometricAuthenticator())
    val repoA = InMemoryContactRepository()
    val engineA = DefaultPairingEngine(deviceA, gateA, repoA)

    val deviceB = TestDeviceIdentity("DeviceB")
    val gateB = DefaultSecurityGate(FakeBiometricAuthenticator())
    val repoB = InMemoryContactRepository()
    val engineB = DefaultPairingEngine(deviceB, gateB, repoB)

    // Pre-insert DeviceA into B as PAIRED
    repoB.add(
      TrustedContact(
        id = deviceA.deviceId,
        displayName = "Alice",
        publicIdentity = deviceA.publicKeyBase64,
        pairingStatus = PairingStatus.PAIRED,
        createdAtEpochMillis = 1000L,
      )
    )

    val sessionA = engineA.createInitiatorSession("Alice").getOrThrow()
    val qr = engineA.generateQrPayload(sessionA).getOrThrow().serialize()

    val tokenB = gateB.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()
    val result = engineB.processQrAndCreateRequest(qr, "Bob", tokenB)

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is PairingException.ContactAlreadyPaired)
  }

  @Test
  fun oversizedQrPayload_rejected() {
    val oversized = "{" + "a".repeat(2048) + "}"
    val result = PairingQrPayload.parse(oversized)
    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is PairingException.OversizedPayload)
  }

  @Test
  fun malformedQrPayload_rejected() {
    val malformed = "not-a-json"
    val result = PairingQrPayload.parse(malformed)
    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is PairingException.MalformedPayload)
  }

  @Test
  fun wrongProtocolVersion_inQr_failsValidation() {
    val wrongVersionJson = "{\"v\":99,\"sid\":\"${UUID.randomUUID()}\",\"id\":\"0000000000000000000000000000000000000000000000000000000000000000\",\"pk\":\"pk1\",\"name\":\"n1\",\"nonce\":\"${StrictBase64.encode(ByteArray(16))}\",\"exp\":99999}"
    val result = PairingQrPayload.parse(wrongVersionJson)
    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is PairingException.ProtocolVersionMismatch)
  }

  @Test
  fun invalidStateTransition_throwsInvalidStateTransition() {
    val exception = org.junit.Assert.assertThrows(PairingException.InvalidStateTransition::class.java) {
      PairingStateValidator.validateTransition(
        PairingRole.INITIATOR,
        PairingSessionState.CREATED,
        PairingSessionState.PAIRED,
      )
    }
    assertEquals(PairingSessionState.CREATED, exception.current)
    assertEquals(PairingSessionState.PAIRED, exception.target)
  }
}

private class InMemoryContactRepository : TrustedContactRepository {
  val contactsMap = mutableMapOf<String, TrustedContact>()
  private val flow = MutableStateFlow<List<TrustedContact>>(emptyList())
  private val initFlow = MutableStateFlow(com.peykr.app.data.contacts.DatabaseState.READY)

  override val contacts: Flow<List<TrustedContact>> = flow
  override val initializationState: StateFlow<com.peykr.app.data.contacts.DatabaseState> = initFlow
  override fun retry() {}

  override suspend fun getById(id: String): TrustedContact? = contactsMap[id]

  override suspend fun add(contact: TrustedContact) {
    contactsMap[contact.id] = contact
    flow.value = contactsMap.values.toList()
  }

  override suspend fun update(contact: TrustedContact) {
    contactsMap[contact.id] = contact
    flow.value = contactsMap.values.toList()
  }

  override suspend fun delete(id: String) {
    contactsMap.remove(id)
    flow.value = contactsMap.values.toList()
  }
}

private class FailingContactRepository : TrustedContactRepository {
  override val contacts: Flow<List<TrustedContact>> = MutableStateFlow(emptyList())
  override val initializationState: StateFlow<com.peykr.app.data.contacts.DatabaseState> = MutableStateFlow(com.peykr.app.data.contacts.DatabaseState.READY)
  override fun retry() {}
  override suspend fun getById(id: String): TrustedContact? = null
  override suspend fun add(contact: TrustedContact) { throw RuntimeException("Disk I/O failure") }
  override suspend fun update(contact: TrustedContact) { throw RuntimeException("Disk I/O failure") }
  override suspend fun delete(id: String) { throw RuntimeException("Disk I/O failure") }
}

private class TestDeviceIdentity(val label: String) : DeviceIdentity {
  private val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
  val publicKeyBytes: ByteArray = keyPair.public.encoded
  val publicKeyBase64: String = StrictBase64.encode(publicKeyBytes)
  val deviceId: String = MessageDigest.getInstance("SHA-256").digest(publicKeyBytes).joinToString("") { "%02x".format(Locale.US, it) }
  val info = DeviceIdentityInfo(deviceId, publicKeyBase64)
  private val stateFlow = MutableStateFlow<DeviceIdentityState>(DeviceIdentityState.Ready(info))

  override val identityState: StateFlow<DeviceIdentityState> = stateFlow

  override fun initialize() {}
  override fun retry() {}
  override fun getOrCreate(): DeviceIdentityResult = DeviceIdentityResult.Success(info)

  override fun sign(transcript: ByteArray): Result<ByteArray> = runCatching {
    val signer = Signature.getInstance("SHA256withECDSA")
    signer.initSign(keyPair.private)
    signer.update(transcript)
    signer.sign()
  }

  override fun verify(publicKeyBase64: String, transcript: ByteArray, signature: ByteArray): Boolean = try {
    val keyBytes = StrictBase64.decode(publicKeyBase64)
    val keySpec = X509EncodedKeySpec(keyBytes)
    val pubKey = KeyFactory.getInstance("EC").generatePublic(keySpec)
    verify(pubKey, transcript, signature)
  } catch (_: Throwable) {
    false
  }

  override fun verify(publicKey: PublicKey, transcript: ByteArray, signature: ByteArray): Boolean = try {
    val verifier = Signature.getInstance("SHA256withECDSA")
    verifier.initVerify(publicKey)
    verifier.update(transcript)
    verifier.verify(signature)
  } catch (_: Throwable) {
    false
  }
}

private class FakeBiometricAuthenticator(
  var availability: BiometricAvailability = BiometricAvailability.AVAILABLE,
  var authResult: AuthenticationResult = AuthenticationResult.Success(),
) : BiometricAuthenticator {

  override fun checkAvailability(allowDeviceCredential: Boolean): BiometricAvailability = availability

  override suspend fun authenticate(config: AuthPromptConfig): AuthenticationResult = authResult
}
