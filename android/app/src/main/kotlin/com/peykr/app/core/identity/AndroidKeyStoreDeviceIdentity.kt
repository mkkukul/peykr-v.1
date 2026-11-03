package com.peykr.app.core.identity

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec

internal interface KeyStoreGateway {
  fun loadKeyStore()
  fun containsAlias(alias: String): Boolean
  fun getPublicKey(alias: String): PublicKey?
  fun deleteEntry(alias: String)
  fun generateKeyPair(alias: String): PublicKey
}

internal class AndroidKeyStoreGateway : KeyStoreGateway {
  private val keyStore: KeyStore by lazy {
    KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
  }

  override fun loadKeyStore() {
    keyStore
  }

  override fun containsAlias(alias: String): Boolean = keyStore.containsAlias(alias)

  override fun getPublicKey(alias: String): PublicKey? = keyStore.getCertificate(alias)?.publicKey

  override fun deleteEntry(alias: String) {
    keyStore.deleteEntry(alias)
  }

  override fun generateKeyPair(alias: String): PublicKey {
    val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEY_STORE)
    val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
      .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
      .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
      .build()
    kpg.initialize(spec)
    kpg.generateKeyPair()
    return keyStore.getCertificate(alias)?.publicKey
      ?: throw IllegalStateException("Android Keystore did not return the generated public key")
  }

  private companion object {
    const val ANDROID_KEY_STORE = "AndroidKeyStore"
  }
}

class AndroidKeyStoreDeviceIdentity internal constructor(
  private val keyStoreGateway: KeyStoreGateway = AndroidKeyStoreGateway()
) : DeviceIdentity {
  constructor() : this(AndroidKeyStoreGateway())

  override fun getOrCreate(): DeviceIdentityResult {
    return try {
      keyStoreGateway.loadKeyStore()
      val entryExists = try {
        keyStoreGateway.containsAlias(ALIAS)
      } catch (e: Exception) {
        return DeviceIdentityResult.KeystoreUnavailable(e)
      }

      if (entryExists) {
        val existing = try {
          keyStoreGateway.getPublicKey(ALIAS)
        } catch (e: Exception) {
          return DeviceIdentityResult.KeystoreUnavailable(e)
        }

        if (existing != null) {
          DeviceIdentityResult.Success(info(existing.encoded))
        } else {
          DeviceIdentityResult.IdentityInvalidated(
            "Device identity key entry exists in Keystore but its public certificate is missing or inaccessible."
          )
        }
      } else {
        if (!IdentityLossPolicy.canGenerateNewIdentity(keyExistsInStore = false)) {
          DeviceIdentityResult.IdentityInvalidated("Identity generation prohibited by policy.")
        } else {
          val generated = try {
            keyStoreGateway.generateKeyPair(ALIAS)
          } catch (e: Exception) {
            return DeviceIdentityResult.GenerationFailed(e)
          }
          DeviceIdentityResult.Success(info(generated.encoded))
        }
      }
    } catch (e: Exception) {
      DeviceIdentityResult.KeystoreUnavailable(e)
    }
  }

  private fun info(encodedPublicKey: ByteArray): DeviceIdentityInfo {
    val publicKey = encodeBase64(encodedPublicKey)
    val id = MessageDigest.getInstance("SHA-256").digest(encodedPublicKey).joinToString("") { "%02x".format(it) }
    return DeviceIdentityInfo(id, publicKey)
  }

  private fun encodeBase64(bytes: ByteArray): String {
    return try {
      java.util.Base64.getEncoder().encodeToString(bytes)
    } catch (_: Throwable) {
      Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
  }

  companion object {
    const val ALIAS = "peykr.device.identity.v1"
  }
}
