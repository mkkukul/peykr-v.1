package com.peykr.app.core.identity

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec

class AndroidKeyStoreDeviceIdentity : DeviceIdentity {
  override fun getOrCreate(): DeviceIdentityInfo {
    val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
    val existing = keyStore.getCertificate(ALIAS)?.publicKey
    if (existing != null) return info(existing.encoded)
    keyStore.deleteEntry(ALIAS)
    KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEY_STORE).apply {
      initialize(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
        .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
        .build())
    }.generateKeyPair()
    val publicKey = keyStore.getCertificate(ALIAS)?.publicKey
      ?: error("Android Keystore did not return the generated public key")
    return info(publicKey.encoded)
  }

  private fun info(encodedPublicKey: ByteArray): DeviceIdentityInfo {
    val publicKey = Base64.encodeToString(encodedPublicKey, Base64.NO_WRAP)
    val id = MessageDigest.getInstance("SHA-256").digest(encodedPublicKey).joinToString("") { "%02x".format(it) }
    return DeviceIdentityInfo(id, publicKey)
  }

  private companion object {
    const val ALIAS = "peykr.device.identity.v1"
    const val ANDROID_KEY_STORE = "AndroidKeyStore"
  }
}
