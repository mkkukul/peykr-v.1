package com.peykr.app.domain.location

import com.peykr.app.core.identity.DeviceIdentity
import com.peykr.app.core.identity.DeviceIdentityInfo
import com.peykr.app.core.identity.DeviceIdentityResult
import com.peykr.app.core.identity.DeviceIdentityState
import com.peykr.app.domain.contacts.PairingStatus
import com.peykr.app.domain.contacts.TrustedContact
import com.peykr.app.domain.contacts.TrustedContactRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocationSafetyEngineTest {

  private lateinit var fakeIdentity: FakeDeviceIdentity
  private lateinit var fakeRepo: FakeTrustedContactRepo
  private var currentTime: Long = 1_000_000L
  private lateinit var engine: LocationSafetyEngine

  @Before
  fun setUp() {
    fakeIdentity = FakeDeviceIdentity()
    fakeRepo = FakeTrustedContactRepo()
    currentTime = 1_000_000L
    engine = LocationSafetyEngine(
      deviceIdentity = fakeIdentity,
      contactRepository = fakeRepo,
      timeProvider = { currentTime },
    )
  }

  @Test
  fun sharingWithPairedContact_succeeds() = runTest {
    fakeRepo.contactMap["alice"] = TrustedContact(
      id = "alice",
      displayName = "Alice",
      pairingStatus = PairingStatus.PAIRED,
      createdAtEpochMillis = 100L,
    )

    val result = engine.startSharingLocation("alice", LocationShareMode.TIME_LIMITED, 3600L)
    assertTrue(result.isSuccess)
    val session = result.getOrThrow()
    assertEquals("alice", session.recipientContactId)
    assertTrue(session.isActive)
  }

  @Test
  fun sharingWithUnpairedContact_isRejected() = runTest {
    fakeRepo.contactMap["bob"] = TrustedContact(
      id = "bob",
      displayName = "Bob",
      pairingStatus = PairingStatus.PENDING,
      createdAtEpochMillis = 100L,
    )

    val result = engine.startSharingLocation("bob", LocationShareMode.ONE_TIME)
    assertTrue(result.isFailure)
  }

  @Test
  fun staleLocation_isRejected() = runTest {
    val staleCoords = LocationCoordinates(
      latitude = 41.0082,
      longitude = 28.9784,
      accuracyMeters = 10f,
      timestampEpochMillis = currentTime - 300_000L, // 5 minutes old
    )

    val result = engine.createSignedLocationPayload(staleCoords)
    assertTrue(result.isFailure)
  }

  @Test
  fun missedCallSafetyAutomation_triggersAfterThreshold() = runTest {
    fakeRepo.contactMap["guardian"] = TrustedContact(
      id = "guardian",
      displayName = "Guardian",
      pairingStatus = PairingStatus.PAIRED,
      createdAtEpochMillis = 100L,
    )

    engine.updateSafetyConfig(
      MissedCallSafetyConfig(
        isEnabled = true,
        requiredMissedCalls = 3,
        timeWindowSeconds = 300L,
        trustedRecipientContactIds = listOf("guardian"),
        autoSharingDurationSeconds = 3600L,
      )
    )

    val freshCoords = LocationCoordinates(41.0, 29.0, 5f, currentTime)

    // Call 1 & 2 do not trigger yet
    assertFalse(engine.recordMissedCall("guardian", freshCoords))
    assertFalse(engine.recordMissedCall("guardian", freshCoords))

    // Call 3 within window triggers automation
    assertTrue(engine.recordMissedCall("guardian", freshCoords))
    val activeShares = engine.activeShares.value
    assertTrue(activeShares.isNotEmpty())
    val session = activeShares.values.first()
    assertEquals("guardian", session.recipientContactId)
    assertTrue(session.isActive)
  }

  @Test
  fun explicitStopSharing_deactivatesSession() = runTest {
    fakeRepo.contactMap["alice"] = TrustedContact(
      id = "alice",
      displayName = "Alice",
      pairingStatus = PairingStatus.PAIRED,
      createdAtEpochMillis = 100L,
    )

    val session = engine.startSharingLocation("alice", LocationShareMode.LIVE).getOrThrow()
    assertTrue(session.isActive)

    engine.stopSharing(session.sessionId)
    val updated = engine.activeShares.value[session.sessionId]
    assertFalse(updated?.isActive ?: true)
  }
}

private class FakeDeviceIdentity : DeviceIdentity {
  override val identityState: kotlinx.coroutines.flow.StateFlow<DeviceIdentityState> =
    MutableStateFlow(DeviceIdentityState.Ready(
      DeviceIdentityInfo("local_id", "pub_key", com.peykr.app.core.identity.KeySecurityLevel.SOFTWARE)
    ))
  override fun initialize() {}
  override fun retry() {}
  override fun getOrCreate(): DeviceIdentityResult = DeviceIdentityResult.Success(
    DeviceIdentityInfo("local_id", "pub_key", com.peykr.app.core.identity.KeySecurityLevel.SOFTWARE)
  )
  override fun sign(transcript: ByteArray): Result<ByteArray> = Result.success(ByteArray(64) { 0x07 })
  override fun verify(publicKeyBase64: String, transcript: ByteArray, signature: ByteArray): Boolean = true
  override fun verify(publicKey: java.security.PublicKey, transcript: ByteArray, signature: ByteArray): Boolean = true
  override fun close() {}
}

private class FakeTrustedContactRepo : TrustedContactRepository {
  val contactMap = mutableMapOf<String, TrustedContact>()
  override val contacts: Flow<List<TrustedContact>> get() = MutableStateFlow(contactMap.values.toList())
  override suspend fun getById(id: String): TrustedContact? = contactMap[id]
  override suspend fun add(contact: TrustedContact) {
    contactMap[contact.id] = contact
  }
  override suspend fun update(contact: TrustedContact) {
    contactMap[contact.id] = contact
  }
  override suspend fun delete(id: String) {
    contactMap.remove(id)
  }
}
