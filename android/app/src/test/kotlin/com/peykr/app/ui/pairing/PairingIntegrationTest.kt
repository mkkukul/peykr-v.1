package com.peykr.app.ui.pairing

import android.graphics.Bitmap
import com.peykr.app.core.auth.AuthPromptConfig
import com.peykr.app.core.auth.AuthenticationResult
import com.peykr.app.core.auth.BiometricAuthenticator
import com.peykr.app.core.auth.BiometricAvailability
import com.peykr.app.core.auth.DefaultSecurityGate
import com.peykr.app.core.identity.DeviceIdentity
import com.peykr.app.core.identity.DeviceIdentityInfo
import com.peykr.app.core.identity.DeviceIdentityResult
import com.peykr.app.core.identity.DeviceIdentityState
import com.peykr.app.domain.contacts.PairingStatus
import com.peykr.app.domain.contacts.TrustedContact
import com.peykr.app.domain.contacts.TrustedContactRepository
import com.peykr.app.domain.pairing.DefaultPairingEngine
import com.peykr.app.domain.pairing.PairingConfirmation
import com.peykr.app.domain.pairing.PairingException
import com.peykr.app.domain.pairing.PairingQrPayload
import com.peykr.app.domain.pairing.PairingRequest
import com.peykr.app.domain.pairing.PairingResponse
import com.peykr.app.domain.pairing.PairingRole
import com.peykr.app.domain.pairing.QrMessageClassifier
import com.peykr.app.domain.pairing.QrMessageType
import com.peykr.app.domain.pairing.StrictBase64
import com.peykr.app.ui.pairing.qr.QrCodeGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Locale
import java.util.UUID

class PairingIntegrationTest {

  @Test
  fun qrMessageSerializationAndParsing_allTypes_roundTrip() {
    val sid = UUID.randomUUID().toString()
    val idA = "a".repeat(64)
    val idB = "b".repeat(64)
    val pk = StrictBase64.encode(ByteArray(32) { 0x01 })
    val nonce = StrictBase64.encode(ByteArray(16) { 0x02 })
    val sig = StrictBase64.encode(ByteArray(64) { 0x03 })

    // 1. Initial QR Payload
    val initPayload = PairingQrPayload(
      version = 1,
      sessionId = sid,
      initiatorDeviceId = idA,
      initiatorPublicKeyBase64 = pk,
      initiatorDisplayName = "Alice",
      initiatorNonceBase64 = nonce,
      expiresAtEpochMillis = 999999L,
    )
    val initSerialized = initPayload.serialize()
    val initParsed = PairingQrPayload.parse(initSerialized).getOrThrow()
    assertEquals(initPayload, initParsed)
    assertEquals(QrMessageType.INITIAL_PAYLOAD, QrMessageClassifier.classify(initSerialized))

    // 2. PairingRequest
    val request = PairingRequest(
      protocolVersion = 1,
      sessionId = sid,
      responderDeviceId = idB,
      responderPublicKeyBase64 = pk,
      responderDisplayName = "Bob",
      responderNonceBase64 = nonce,
      responderSignatureBase64 = sig,
    )
    val reqSerialized = request.serialize()
    val reqParsed = PairingRequest.parse(reqSerialized).getOrThrow()
    assertEquals(request, reqParsed)
    assertEquals(QrMessageType.PAIRING_REQUEST, QrMessageClassifier.classify(reqSerialized))

    // 3. PairingResponse
    val response = PairingResponse(
      protocolVersion = 1,
      sessionId = sid,
      initiatorDeviceId = idA,
      initiatorSignatureBase64 = sig,
    )
    val respSerialized = response.serialize()
    val respParsed = PairingResponse.parse(respSerialized).getOrThrow()
    assertEquals(response, respParsed)
    assertEquals(QrMessageType.PAIRING_RESPONSE, QrMessageClassifier.classify(respSerialized))

    // 4. PairingConfirmation
    val confirmation = PairingConfirmation(
      protocolVersion = 1,
      sessionId = sid,
      responderDeviceId = idB,
      confirmationSignatureBase64 = sig,
    )
    val confSerialized = confirmation.serialize()
    val confParsed = PairingConfirmation.parse(confSerialized).getOrThrow()
    assertEquals(confirmation, confParsed)
    assertEquals(QrMessageType.PAIRING_CONFIRMATION, QrMessageClassifier.classify(confSerialized))
  }

  @Test
  fun qrMessageClassifier_detectsAllTypesAccurately() {
    assertEquals(QrMessageType.UNKNOWN, QrMessageClassifier.classify("not-a-json"))
    assertEquals(QrMessageType.UNKNOWN, QrMessageClassifier.classify("{\"random\":\"json\"}"))

    val sid = UUID.randomUUID().toString()
    val id = "a".repeat(64)
    val sig = StrictBase64.encode(ByteArray(64))

    val reqJson = "{\"t\":\"req\",\"v\":1,\"sid\":\"$sid\",\"id\":\"$id\",\"pk\":\"pk\",\"name\":\"Bob\",\"nonce\":\"nc\",\"sig\":\"$sig\"}"
    assertEquals(QrMessageType.PAIRING_REQUEST, QrMessageClassifier.classify(reqJson))

    val respJson = "{\"t\":\"resp\",\"v\":1,\"sid\":\"$sid\",\"id\":\"$id\",\"sig\":\"$sig\"}"
    assertEquals(QrMessageType.PAIRING_RESPONSE, QrMessageClassifier.classify(respJson))

    val confJson = "{\"t\":\"conf\",\"v\":1,\"sid\":\"$sid\",\"id\":\"$id\",\"sig\":\"$sig\"}"
    assertEquals(QrMessageType.PAIRING_CONFIRMATION, QrMessageClassifier.classify(confJson))
  }

  @Test
  fun oversizedPayload_rejectedAcrossAllTypes() {
    val oversized = "{" + "a".repeat(2048) + "}"
    assertTrue(PairingQrPayload.parse(oversized).isFailure)
    assertTrue(PairingRequest.parse(oversized).isFailure)
    assertTrue(PairingResponse.parse(oversized).isFailure)
    assertTrue(PairingConfirmation.parse(oversized).isFailure)
  }

  @Test
  fun unsupportedProtocolVersion_rejectedAcrossAllTypes() {
    val sid = UUID.randomUUID().toString()
    val id = "a".repeat(64)
    val sig = StrictBase64.encode(ByteArray(64))

    val wrongVersionReq = "{\"t\":\"req\",\"v\":99,\"sid\":\"$sid\",\"id\":\"$id\",\"pk\":\"pk\",\"name\":\"n\",\"nonce\":\"nc\",\"sig\":\"$sig\"}"
    val result = PairingRequest.parse(wrongVersionReq)
    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is PairingException.ProtocolVersionMismatch)
  }

  @Test
  fun qrCodeGenerator_generatesValidMatrix_andRejectsOversized() {
    val validPayload = "{\"test\":\"qr_content\"}"
    val matrixResult = QrCodeGenerator.generateQrMatrix(validPayload, size = 256)
    assertTrue(matrixResult.isSuccess)
    val matrix = matrixResult.getOrThrow()
    assertEquals(256, matrix.width)
    assertEquals(256, matrix.height)

    val oversized = "a".repeat(2049)
    val oversizedResult = QrCodeGenerator.generateQrMatrix(oversized)
    assertTrue(oversizedResult.isFailure)
    assertTrue(oversizedResult.exceptionOrNull() is PairingException.OversizedPayload)
  }

  @Test
  fun pairingViewModel_completeInitiatorAndResponderWorkflow_mutualPaired() = runTest {
    var currentTime = 100_000L
    val timeProvider: () -> Long = { currentTime }

    val deviceA = TestDeviceIdentity("DeviceA")
    val gateA = DefaultSecurityGate(FakeBiometricAuthenticator(), timeProvider = timeProvider)
    val repoA = InMemoryContactRepository()
    val engineA = DefaultPairingEngine(deviceA, gateA, repoA, timeProvider = timeProvider)
    val viewModelA = PairingViewModel(engineA, gateA, deviceA, coroutineScope = this)

    val deviceB = TestDeviceIdentity("DeviceB")
    val gateB = DefaultSecurityGate(FakeBiometricAuthenticator(), timeProvider = timeProvider)
    val repoB = InMemoryContactRepository()
    val engineB = DefaultPairingEngine(deviceB, gateB, repoB, timeProvider = timeProvider)
    val viewModelB = PairingViewModel(engineB, gateB, deviceB, coroutineScope = this)

    // Step A: Device A starts as Initiator and displays initial QR
    viewModelA.startAsInitiator("Alice's Phone")
    testScheduler.advanceUntilIdle()
    val stateA1 = viewModelA.uiState.value
    assertTrue(stateA1 is PairingUiState.InitiatorShowInitialQr)
    val qrPayloadA = (stateA1 as PairingUiState.InitiatorShowInitialQr).qrPayload.serialize()

    // Step B: Device B scans initial QR, reviews identity, and approves with biometrics
    viewModelB.startAsResponder()
    assertTrue(viewModelB.uiState.value is PairingUiState.ResponderScanInitialQr)
    viewModelB.onResponderScannedInitialQr(qrPayloadA)
    testScheduler.advanceUntilIdle()
    val stateB1 = viewModelB.uiState.value
    assertTrue(stateB1 is PairingUiState.ResponderReviewAndApprove)
    assertEquals("Alice's Phone", (stateB1 as PairingUiState.ResponderReviewAndApprove).remoteName)

    viewModelB.approveResponderRequest("Bob's Phone")
    testScheduler.advanceUntilIdle()
    val stateB2 = viewModelB.uiState.value
    assertTrue(stateB2 is PairingUiState.ResponderShowRequestQr)
    val requestB = (stateB2 as PairingUiState.ResponderShowRequestQr).request.serialize()

    // Contact on B is PENDING
    assertEquals(PairingStatus.PENDING, repoB.getById(deviceA.deviceId)?.pairingStatus)

    // Step C: Device A scans Responder's Request QR
    viewModelA.proceedToScanResponderRequest()
    assertTrue(viewModelA.uiState.value is PairingUiState.InitiatorScanRequest)
    viewModelA.onInitiatorScannedRequestQr(requestB)
    testScheduler.advanceUntilIdle()
    val stateA3 = viewModelA.uiState.value
    assertTrue(stateA3 is PairingUiState.InitiatorReviewAndApprove)

    // Contact on A is PENDING
    assertEquals(PairingStatus.PENDING, repoA.getById(deviceB.deviceId)?.pairingStatus)

    // Step C.2: Device A approves request with biometric authorization -> displays Response QR
    viewModelA.approveInitiatorRequest()
    testScheduler.advanceUntilIdle()
    val stateA4 = viewModelA.uiState.value
    assertTrue(stateA4 is PairingUiState.InitiatorShowResponseQr)
    val responseA = (stateA4 as PairingUiState.InitiatorShowResponseQr).response.serialize()

    // Contact on A remains PENDING!
    assertEquals(PairingStatus.PENDING, repoA.getById(deviceB.deviceId)?.pairingStatus)

    // Step D: Device B scans Initiator's Response QR -> promotes contact to PAIRED, shows Confirmation QR
    viewModelB.proceedToScanInitiatorResponse()
    assertTrue(viewModelB.uiState.value is PairingUiState.ResponderScanResponse)
    viewModelB.onResponderScannedResponseQr(responseA)
    testScheduler.advanceUntilIdle()
    val stateB4 = viewModelB.uiState.value
    assertTrue(stateB4 is PairingUiState.ResponderShowConfirmationQr)
    val confirmB = (stateB4 as PairingUiState.ResponderShowConfirmationQr).confirmation.serialize()

    // Contact on B is now PAIRED
    assertEquals(PairingStatus.PAIRED, repoB.getById(deviceA.deviceId)?.pairingStatus)

    // Step E: Device A scans Confirmation QR -> promotes contact to PAIRED
    viewModelA.proceedToScanFinalConfirmation()
    assertTrue(viewModelA.uiState.value is PairingUiState.InitiatorScanConfirmation)
    viewModelA.onInitiatorScannedConfirmationQr(confirmB)
    testScheduler.advanceUntilIdle()
    val stateA5 = viewModelA.uiState.value
    assertTrue(stateA5 is PairingUiState.Success)

    // Contact on A is now PAIRED
    assertEquals(PairingStatus.PAIRED, repoA.getById(deviceB.deviceId)?.pairingStatus)

    // Complete responder UI
    viewModelB.finishResponderFlow()
    assertTrue(viewModelB.uiState.value is PairingUiState.Success)
  }

  @Test
  fun pairingViewModel_wrongMessageType_rejectedInScanner() = runTest {
    val deviceA = TestDeviceIdentity("DeviceA")
    val gateA = DefaultSecurityGate(FakeBiometricAuthenticator())
    val repoA = InMemoryContactRepository()
    val engineA = DefaultPairingEngine(deviceA, gateA, repoA)
    val viewModelA = PairingViewModel(engineA, gateA, deviceA, coroutineScope = this)

    viewModelA.startAsInitiator("Alice")
    testScheduler.advanceUntilIdle()
    viewModelA.proceedToScanResponderRequest()

    // Pass invalid payload type (e.g. initial QR instead of request)
    val wrongPayload = "{\"t\":\"init\",\"v\":1,\"sid\":\"${UUID.randomUUID()}\",\"id\":\"${"a".repeat(64)}\",\"pk\":\"pk\",\"name\":\"n\",\"nonce\":\"${StrictBase64.encode(ByteArray(16))}\",\"exp\":99999}"
    viewModelA.onInitiatorScannedRequestQr(wrongPayload)
    testScheduler.advanceUntilIdle()

    val state = viewModelA.uiState.value
    assertTrue(state is PairingUiState.InitiatorScanRequest)
    assertNotNull((state as PairingUiState.InitiatorScanRequest).errorMessage)
    assertTrue(state.errorMessage!!.contains("not a pairing request"))
  }

  @Test
  fun pairingViewModel_cancelPairing_resetsToRoleSelection() = runTest {
    val deviceA = TestDeviceIdentity("DeviceA")
    val gateA = DefaultSecurityGate(FakeBiometricAuthenticator())
    val repoA = InMemoryContactRepository()
    val engineA = DefaultPairingEngine(deviceA, gateA, repoA)
    val viewModelA = PairingViewModel(engineA, gateA, deviceA, coroutineScope = this)

    viewModelA.startAsInitiator("Alice")
    testScheduler.advanceUntilIdle()
    assertTrue(viewModelA.uiState.value is PairingUiState.InitiatorShowInitialQr)

    viewModelA.cancelPairing()
    testScheduler.advanceUntilIdle()
    assertEquals(PairingUiState.RoleSelection, viewModelA.uiState.value)
  }

  @Test
  fun displayNameSanitization_stripsBidiAndControlCharacters() {
    val raw = "Attacker\u202E\u200B\u0000\u001F\nName   "
    val sanitized = com.peykr.app.domain.pairing.RemoteDeviceIdentity.normalizeDisplayName(raw)
    assertEquals("AttackerName", sanitized)

    val longName = "A".repeat(100)
    val truncated = com.peykr.app.domain.pairing.RemoteDeviceIdentity.normalizeDisplayName(longName)
    assertEquals(64, truncated.length)
  }

  @Test
  fun jsonUtils_escapesAndUnescapesControlCharactersCorrectly() {
    val input = "Special\n\"Value\"\tWith\\Backslash\rAnd\bMore"
    val escaped = com.peykr.app.domain.pairing.JsonUtils.escape(input)
    val json = "{\"key\":\"$escaped\"}"
    val extracted = com.peykr.app.domain.pairing.JsonUtils.extractString(json, "key")
    assertEquals(input, extracted)
  }

  @Test
  fun pairingViewModel_finishResponderFlow_rejectedIfNotPaired() = runTest {
    val deviceB = TestDeviceIdentity("DeviceB")
    val gateB = DefaultSecurityGate(FakeBiometricAuthenticator())
    val repoB = InMemoryContactRepository()
    val engineB = DefaultPairingEngine(deviceB, gateB, repoB)
    val viewModelB = PairingViewModel(engineB, gateB, deviceB, coroutineScope = this)

    viewModelB.startAsResponder()
    assertEquals(PairingUiState.ResponderScanInitialQr(), viewModelB.uiState.value)

    // finishResponderFlow when not PAIRED must NOT transition to Success
    viewModelB.finishResponderFlow()
    assertEquals(PairingUiState.ResponderScanInitialQr(), viewModelB.uiState.value)
  }

  @Test
  fun pairingViewModel_fingerprintFormat_full256BitFormatted() = runTest {
    val deviceA = TestDeviceIdentity("DeviceA")
    val gateA = DefaultSecurityGate(FakeBiometricAuthenticator())
    val repoA = InMemoryContactRepository()
    val engineA = DefaultPairingEngine(deviceA, gateA, repoA)
    val viewModelA = PairingViewModel(engineA, gateA, deviceA, coroutineScope = this)

    val sampleSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    val formatted = viewModelA.formatFingerprint(sampleSha256)
    // 64 hex chars chunked into 16 groups of 4 = 15 spaces -> 79 chars
    assertEquals(79, formatted.length)
    assertTrue(formatted.startsWith("E3B0 C442 98FC"))
    assertTrue(formatted.endsWith("7852 B855"))
  }

  @Test
  fun qrCodeAnalyzer_close_isIdempotentAndSafe() {
    val analyzer = com.peykr.app.ui.pairing.scanner.QrCodeAnalyzer(onQrCodeDetected = { })
    // Calling close should not throw
    analyzer.close()
    analyzer.close()
  }

  @Test
  fun transcript_computesDeterministicSasCode_onBothPeers() {
    val transcript = com.peykr.app.domain.pairing.PairingTranscript(
      protocolVersion = 1,
      sessionId = "sess_123",
      initiatorDeviceId = "devA",
      initiatorPublicKeyBase64 = "pkA",
      initiatorNonceBase64 = "nonceA",
      responderDeviceId = "devB",
      responderPublicKeyBase64 = "pkB",
      responderNonceBase64 = "nonceB",
      expiresAtEpochMillis = 999999L,
    )

    val sasCode1 = transcript.computeSasCode()
    val sasCode2 = transcript.computeSasCode()

    assertEquals(sasCode1, sasCode2)
    assertTrue("SAS code should match pattern 000-000", sasCode1.matches("^\\d{3}-\\d{3}$".toRegex()))
  }

  @Test
  fun blockedContact_isRejectedByPairingEngine() = runTest {
    val identityA = TestDeviceIdentity("DeviceA")
    val identityB = TestDeviceIdentity("DeviceB")
    val repoA = InMemoryContactRepository()
    val repoB = InMemoryContactRepository()

    // B is blocked on A
    repoA.contactsMap[identityB.deviceId] = TrustedContact(
      id = identityB.deviceId,
      displayName = "Blocked Peer",
      pairingStatus = PairingStatus.BLOCKED,
      createdAtEpochMillis = 100L,
    )

    val gateA = DefaultSecurityGate(FakeBiometricAuthenticator())
    val gateB = DefaultSecurityGate(FakeBiometricAuthenticator())
    val engineA = DefaultPairingEngine(identityA, gateA, repoA)
    val engineB = DefaultPairingEngine(identityB, gateB, repoB)

    // B scans A's QR and sends request to A
    val sessionA = engineA.createInitiatorSession("DeviceA").getOrThrow()
    val qrPayload = engineA.generateQrPayload(sessionA).getOrThrow()

    val tokenB = gateB.authorize(com.peykr.app.core.auth.SecurityGateScope.APPROVE_TRUSTED_DEVICE, AuthPromptConfig("title")).getOrThrow()
    val requestResult = engineB.processQrAndCreateRequest(qrPayload.serialize(), "DeviceB", tokenB).getOrThrow()

    // A receives request from blocked device B
    val receiveResult = engineA.receivePairingRequest(sessionA, requestResult.request)
    assertTrue("Pairing with blocked device must fail", receiveResult.isFailure)
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
