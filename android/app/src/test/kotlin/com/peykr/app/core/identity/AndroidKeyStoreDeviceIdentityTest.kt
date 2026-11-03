package com.peykr.app.core.identity

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStoreException
import java.security.PrivateKey
import java.security.PublicKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidKeyStoreDeviceIdentityTest {

  @Test
  fun readyStateOnValidExistingKey() {
    val fakeGateway = FakeKeyStoreGateway()
    fakeGateway.prepopulateKey()
    val identity = AndroidKeyStoreDeviceIdentity(fakeGateway)

    val result = identity.getOrCreate()

    assertTrue(result is DeviceIdentityResult.Success)
    val success = result as DeviceIdentityResult.Success
    assertTrue(success.identity.id.isNotBlank())
    assertTrue(success.identity.publicKey.isNotBlank())
    assertEquals(DeviceIdentityState.Ready(success.identity), identity.identityState.value)
    assertEquals(0, fakeGateway.generationCount)
  }

  @Test
  fun repeatedInitializationReturnsStableIdentityWithoutRegeneration() {
    val fakeGateway = FakeKeyStoreGateway()
    val identity = AndroidKeyStoreDeviceIdentity(fakeGateway)

    val first = identity.getOrCreate()
    val second = identity.getOrCreate()

    assertTrue(first is DeviceIdentityResult.Success)
    assertTrue(second is DeviceIdentityResult.Success)
    assertEquals(first, second)
    assertEquals(1, fakeGateway.generationCount)
  }

  @Test
  fun keystoreUnavailableProducesKeystoreUnavailableWithoutDeletingKey() {
    val fakeGateway = FakeKeyStoreGateway()
    fakeGateway.prepopulateKey()
    fakeGateway.shouldThrowOnAccess = true

    val identity = AndroidKeyStoreDeviceIdentity(fakeGateway)
    val result = identity.getOrCreate()

    assertTrue(result is DeviceIdentityResult.KeystoreUnavailable)
    assertTrue(identity.identityState.value is DeviceIdentityState.KeystoreUnavailable)
    assertFalse("Existing identity must never be deleted on Keystore error", fakeGateway.deleteEntryCalled)

    // Once Keystore recovers, verify the identity was preserved and never deleted
    fakeGateway.shouldThrowOnAccess = false
    val recovered = identity.getOrCreate()
    assertTrue(recovered is DeviceIdentityResult.Success)
    assertEquals(0, fakeGateway.generationCount)
  }

  @Test
  fun invalidatedKeyProducesIdentityInvalidatedAndDoesNotSilentlyRegenerate() {
    val fakeGateway = FakeKeyStoreGateway()
    fakeGateway.hasAliasWithoutKey = true

    val identity = AndroidKeyStoreDeviceIdentity(fakeGateway)
    val result = identity.getOrCreate()

    assertTrue(result is DeviceIdentityResult.IdentityInvalidated)
    assertTrue(identity.identityState.value is DeviceIdentityState.IdentityInvalidated)
    assertFalse("Must not delete invalidated entry without policy action", fakeGateway.deleteEntryCalled)
    assertEquals("Must not generate replacement identity", 0, fakeGateway.generationCount)
  }

  @Test
  fun signingProducesNonEmptySignatureForValidTranscript() {
    val fakeGateway = FakeKeyStoreGateway()
    val identity = AndroidKeyStoreDeviceIdentity(fakeGateway)
    val initResult = identity.getOrCreate()
    assertTrue(initResult is DeviceIdentityResult.Success)

    val transcript = "test-session-transcript-payload".toByteArray(Charsets.UTF_8)
    val signResult = identity.sign(transcript)

    assertTrue(signResult.isSuccess)
    val signature = signResult.getOrThrow()
    assertTrue(signature.isNotEmpty())
  }

  @Test
  fun signatureVerificationSucceedsForValidTranscriptAndPublicKey() {
    val fakeGateway = FakeKeyStoreGateway()
    val identity = AndroidKeyStoreDeviceIdentity(fakeGateway)
    val initResult = identity.getOrCreate() as DeviceIdentityResult.Success

    val transcript = "test-session-transcript-payload".toByteArray(Charsets.UTF_8)
    val signature = identity.sign(transcript).getOrThrow()

    val verifiedWithBase64 = identity.verify(initResult.identity.publicKey, transcript, signature)
    assertTrue(verifiedWithBase64)

    val publicKey = fakeGateway.getPublicKey(AndroidKeyStoreDeviceIdentity.ALIAS)!!
    val verifiedWithObject = identity.verify(publicKey, transcript, signature)
    assertTrue(verifiedWithObject)
  }

  @Test
  fun signatureVerificationFailsWhenTranscriptIsModified() {
    val fakeGateway = FakeKeyStoreGateway()
    val identity = AndroidKeyStoreDeviceIdentity(fakeGateway)
    val initResult = identity.getOrCreate() as DeviceIdentityResult.Success

    val originalTranscript = "original-transcript".toByteArray(Charsets.UTF_8)
    val tamperedTranscript = "tampered-transcript".toByteArray(Charsets.UTF_8)
    val signature = identity.sign(originalTranscript).getOrThrow()

    val verified = identity.verify(initResult.identity.publicKey, tamperedTranscript, signature)
    assertFalse(verified)
  }

  @Test
  fun signatureVerificationFailsWhenCheckedAgainstDifferentPublicKey() {
    val fakeGateway = FakeKeyStoreGateway()
    val identity = AndroidKeyStoreDeviceIdentity(fakeGateway)
    identity.getOrCreate()

    val transcript = "secure-data-payload".toByteArray(Charsets.UTF_8)
    val signature = identity.sign(transcript).getOrThrow()

    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(256)
    val otherPublicKey = kpg.generateKeyPair().public
    val otherPublicKeyBase64 = java.util.Base64.getEncoder().encodeToString(otherPublicKey.encoded)

    assertFalse(identity.verify(otherPublicKey, transcript, signature))
    assertFalse(identity.verify(otherPublicKeyBase64, transcript, signature))
  }

  @Test
  fun signingFailsExplicitlyWhenIdentityNotReadyOrKeystoreUnavailable() {
    val fakeGateway = FakeKeyStoreGateway()
    val identity = AndroidKeyStoreDeviceIdentity(fakeGateway)

    val transcript = "transcript".toByteArray(Charsets.UTF_8)
    val unreadyResult = identity.sign(transcript)
    assertTrue(unreadyResult.isFailure)
    assertTrue(unreadyResult.exceptionOrNull() is DeviceIdentityException)

    fakeGateway.prepopulateKey()
    fakeGateway.shouldThrowOnAccess = true
    val failingIdentity = AndroidKeyStoreDeviceIdentity(fakeGateway)
    failingIdentity.getOrCreate()

    val failingResult = failingIdentity.sign(transcript)
    assertTrue(failingResult.isFailure)
    assertTrue(failingResult.exceptionOrNull() is DeviceIdentityException)
  }

  @Test
  fun retryingInitializationDoesNotDeleteExistingIdentityKey() {
    val fakeGateway = FakeKeyStoreGateway()
    fakeGateway.prepopulateKey()
    fakeGateway.shouldThrowOnAccess = true

    val identity = AndroidKeyStoreDeviceIdentity(fakeGateway)
    identity.getOrCreate()

    assertTrue(identity.identityState.value is DeviceIdentityState.KeystoreUnavailable)

    identity.retry()
    assertFalse("Retrying initialization must never delete the existing identity key", fakeGateway.deleteEntryCalled)

    fakeGateway.shouldThrowOnAccess = false
    val recovered = identity.getOrCreate()
    assertTrue(recovered is DeviceIdentityResult.Success)
    assertFalse("Must never delete entry even after recovery", fakeGateway.deleteEntryCalled)
    assertEquals(0, fakeGateway.generationCount)
  }

  @Test
  fun concurrentGetOrCreate_callsGenerateKeyPairOnlyOnce() {
    val fakeGateway = FakeKeyStoreGateway()
    val identity = AndroidKeyStoreDeviceIdentity(fakeGateway)

    val threads = List(10) {
      Thread {
        identity.getOrCreate()
      }
    }
    threads.forEach { it.start() }
    threads.forEach { it.join() }

    assertEquals(1, fakeGateway.generationCount)
    assertTrue(identity.identityState.value is DeviceIdentityState.Ready)
  }
}

private class FakeKeyStoreGateway : KeyStoreGateway {
  var generationCount = 0
  var deleteEntryCalled = false
  var shouldThrowOnAccess = false
  var hasAliasWithoutKey = false
  var storedPublicKey: PublicKey? = null
  var storedPrivateKey: PrivateKey? = null

  fun prepopulateKey() {
    val pair = generateECKeyPair()
    storedPublicKey = pair.public
    storedPrivateKey = pair.private
  }

  override fun loadKeyStore() {
    if (shouldThrowOnAccess) throw KeyStoreException("Simulated Keystore unavailable")
  }

  override fun containsAlias(alias: String): Boolean {
    if (shouldThrowOnAccess) throw KeyStoreException("Simulated Keystore containsAlias error")
    return hasAliasWithoutKey || storedPublicKey != null
  }

  override fun getPublicKey(alias: String): PublicKey? {
    if (shouldThrowOnAccess) throw KeyStoreException("Simulated Keystore getPublicKey error")
    if (hasAliasWithoutKey) return null
    return storedPublicKey
  }

  override fun getPrivateKey(alias: String): PrivateKey? {
    if (shouldThrowOnAccess) throw KeyStoreException("Simulated Keystore getPrivateKey error")
    if (hasAliasWithoutKey) return null
    return storedPrivateKey
  }

  override fun deleteEntry(alias: String) {
    deleteEntryCalled = true
    storedPublicKey = null
    storedPrivateKey = null
    hasAliasWithoutKey = false
  }

  override fun generateKeyPair(alias: String): PublicKey {
    generationCount++
    val pair = generateECKeyPair()
    storedPublicKey = pair.public
    storedPrivateKey = pair.private
    return pair.public
  }

  private fun generateECKeyPair(): KeyPair {
    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(256)
    return kpg.generateKeyPair()
  }
}
