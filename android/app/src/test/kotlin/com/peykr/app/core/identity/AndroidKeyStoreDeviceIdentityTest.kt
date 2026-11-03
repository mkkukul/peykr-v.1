package com.peykr.app.core.identity

import java.security.KeyPairGenerator
import java.security.KeyStoreException
import java.security.PublicKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidKeyStoreDeviceIdentityTest {

  @Test
  fun existingIdentity_remainsStableAcrossRepeatedReads() {
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
  fun genuinelyMissingIdentity_generatesNewIdentityAccordingToPolicy() {
    val fakeGateway = FakeKeyStoreGateway()
    val identity = AndroidKeyStoreDeviceIdentity(fakeGateway)

    assertFalse(fakeGateway.containsAlias(AndroidKeyStoreDeviceIdentity.ALIAS))
    val result = identity.getOrCreate()

    assertTrue(result is DeviceIdentityResult.Success)
    assertEquals(1, fakeGateway.generationCount)
    assertTrue(fakeGateway.containsAlias(AndroidKeyStoreDeviceIdentity.ALIAS))
  }

  @Test
  fun keystoreAccessError_doesNotDeleteExistingIdentity() {
    val fakeGateway = FakeKeyStoreGateway()
    fakeGateway.prepopulateKey()
    fakeGateway.shouldThrowOnAccess = true

    val identity = AndroidKeyStoreDeviceIdentity(fakeGateway)
    val result = identity.getOrCreate()

    assertTrue(result is DeviceIdentityResult.KeystoreUnavailable)
    assertFalse("Existing identity must never be deleted on Keystore error", fakeGateway.deleteEntryCalled)

    // Once Keystore recovers, verify the identity was preserved and never deleted
    fakeGateway.shouldThrowOnAccess = false
    assertTrue(fakeGateway.containsAlias(AndroidKeyStoreDeviceIdentity.ALIAS))
    val recovered = identity.getOrCreate()
    assertTrue(recovered is DeviceIdentityResult.Success)
  }

  @Test
  fun invalidatedIdentityCertificate_returnsErrorAndDoesNotSilentlyRegenerate() {
    val fakeGateway = FakeKeyStoreGateway()
    fakeGateway.hasAliasWithoutKey = true

    val identity = AndroidKeyStoreDeviceIdentity(fakeGateway)
    val result = identity.getOrCreate()

    assertTrue(result is DeviceIdentityResult.IdentityInvalidated)
    assertFalse("Must not delete invalidated entry without policy action", fakeGateway.deleteEntryCalled)
    assertEquals("Must not generate replacement identity", 0, fakeGateway.generationCount)
  }
}

private class FakeKeyStoreGateway : KeyStoreGateway {
  var generationCount = 0
  var deleteEntryCalled = false
  var shouldThrowOnAccess = false
  var hasAliasWithoutKey = false
  private var storedPublicKey: PublicKey? = null

  fun prepopulateKey() {
    storedPublicKey = generateECKey()
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

  override fun deleteEntry(alias: String) {
    deleteEntryCalled = true
    storedPublicKey = null
    hasAliasWithoutKey = false
  }

  override fun generateKeyPair(alias: String): PublicKey {
    generationCount++
    val key = generateECKey()
    storedPublicKey = key
    return key
  }

  private fun generateECKey(): PublicKey {
    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(256)
    return kpg.generateKeyPair().public
  }
}
