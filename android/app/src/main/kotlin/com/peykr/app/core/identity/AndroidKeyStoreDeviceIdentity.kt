package com.peykr.app.core.identity

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal interface KeyStoreGateway {
  fun loadKeyStore()
  fun containsAlias(alias: String): Boolean
  fun getPublicKey(alias: String): PublicKey?
  fun getPrivateKey(alias: String): PrivateKey?
  fun deleteEntry(alias: String)
  fun generateKeyPair(alias: String): PublicKey
  fun getKeySecurityLevel(alias: String): KeySecurityLevel = KeySecurityLevel.UNKNOWN
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

  override fun getPrivateKey(alias: String): PrivateKey? = keyStore.getKey(alias, null) as? PrivateKey

  override fun deleteEntry(alias: String) {
    keyStore.deleteEntry(alias)
  }

  override fun getKeySecurityLevel(alias: String): KeySecurityLevel {
    return try {
      val privateKey = keyStore.getKey(alias, null) as? PrivateKey ?: return KeySecurityLevel.UNKNOWN
      val factory = KeyFactory.getInstance(privateKey.algorithm, ANDROID_KEY_STORE)
      val keyInfo = factory.getKeySpec(privateKey, android.security.keystore.KeyInfo::class.java)
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        when (keyInfo.securityLevel) {
          KeyProperties.SECURITY_LEVEL_STRONGBOX -> KeySecurityLevel.STRONGBOX
          KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> KeySecurityLevel.TRUSTED_ENVIRONMENT
          KeyProperties.SECURITY_LEVEL_SOFTWARE -> KeySecurityLevel.SOFTWARE
          else -> KeySecurityLevel.UNKNOWN
        }
      } else {
        @Suppress("DEPRECATION")
        if (keyInfo.isInsideSecureHardware) {
          KeySecurityLevel.TRUSTED_ENVIRONMENT
        } else {
          KeySecurityLevel.SOFTWARE
        }
      }
    } catch (_: Throwable) {
      KeySecurityLevel.UNKNOWN
    }
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
  private val keyStoreGateway: KeyStoreGateway = AndroidKeyStoreGateway(),
  private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : DeviceIdentity {
  constructor() : this(AndroidKeyStoreGateway(), CoroutineScope(SupervisorJob() + Dispatchers.IO))

  private val lock = Any()
  private val _identityState = MutableStateFlow<DeviceIdentityState>(DeviceIdentityState.Initializing)
  override val identityState: StateFlow<DeviceIdentityState> = _identityState.asStateFlow()

  override fun initialize() {
    synchronized(lock) {
      if (_identityState.value is DeviceIdentityState.Ready || _identityState.value is DeviceIdentityState.IdentityInvalidated) return
    }
    coroutineScope.launch {
      val result = getOrCreate()
      _identityState.value = result.toState()
    }
  }

  override fun retry() {
    val current = _identityState.value
    if (current is DeviceIdentityState.KeystoreUnavailable) {
      coroutineScope.launch {
        _identityState.value = DeviceIdentityState.Initializing
        val result = getOrCreate()
        _identityState.value = result.toState()
      }
    }
  }

  override fun getOrCreate(): DeviceIdentityResult {
    synchronized(lock) {
      val result = try {
        keyStoreGateway.loadKeyStore()
        val entryExists = try {
          keyStoreGateway.containsAlias(ALIAS)
        } catch (e: Exception) {
          return DeviceIdentityResult.KeystoreUnavailable(e).also { _identityState.value = it.toState() }
        }

        if (entryExists) {
          val existing = try {
            keyStoreGateway.getPublicKey(ALIAS)
          } catch (e: Exception) {
            return DeviceIdentityResult.KeystoreUnavailable(e).also { _identityState.value = it.toState() }
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
              return DeviceIdentityResult.GenerationFailed(e).also { _identityState.value = it.toState() }
            }
            DeviceIdentityResult.Success(info(generated.encoded))
          }
        }
      } catch (e: Exception) {
        DeviceIdentityResult.KeystoreUnavailable(e)
      }
      _identityState.value = result.toState()
      return result
    }
  }

  override fun sign(transcript: ByteArray): Result<ByteArray> {
    return runCatching {
      when (val currentState = _identityState.value) {
        is DeviceIdentityState.Initializing -> throw DeviceIdentityException.KeyNotReady("Device identity is not ready (initializing)")
        is DeviceIdentityState.KeystoreUnavailable -> throw DeviceIdentityException.KeystoreUnavailable("Keystore unavailable", currentState.cause)
        is DeviceIdentityState.IdentityInvalidated -> throw DeviceIdentityException.IdentityInvalidated(currentState.message, currentState.cause)
        is DeviceIdentityState.GenerationFailed -> throw DeviceIdentityException.KeyNotReady("Device identity generation failed")
        is DeviceIdentityState.Ready -> { /* ready to sign */ }
      }

      val privateKey = try {
        keyStoreGateway.getPrivateKey(ALIAS)
      } catch (e: Exception) {
        throw DeviceIdentityException.KeystoreUnavailable("Failed to retrieve private key from Keystore", e)
      } ?: throw DeviceIdentityException.KeyNotReady("Device identity private key is missing or not accessible")

      val signer = Signature.getInstance(SIGNATURE_ALGORITHM)
      signer.initSign(privateKey)
      signer.update(transcript)
      signer.sign()
    }
  }

  override fun verify(publicKeyBase64: String, transcript: ByteArray, signature: ByteArray): Boolean {
    return try {
      val decodedKey = decodeBase64(publicKeyBase64)
      val keySpec = X509EncodedKeySpec(decodedKey)
      val keyFactory = KeyFactory.getInstance("EC")
      val publicKey = keyFactory.generatePublic(keySpec)
      verify(publicKey, transcript, signature)
    } catch (_: Throwable) {
      false
    }
  }

  override fun verify(publicKey: PublicKey, transcript: ByteArray, signature: ByteArray): Boolean {
    return try {
      val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
      verifier.initVerify(publicKey)
      verifier.update(transcript)
      verifier.verify(signature)
    } catch (_: Throwable) {
      false
    }
  }

  override fun close() {
    coroutineScope.cancel()
  }

  private fun info(encodedPublicKey: ByteArray): DeviceIdentityInfo {
    val publicKey = encodeBase64(encodedPublicKey)
    val id = MessageDigest.getInstance("SHA-256").digest(encodedPublicKey).joinToString("") {
      "%02x".format(Locale.US, it)
    }
    val secLevel = runCatching { keyStoreGateway.getKeySecurityLevel(ALIAS) }.getOrDefault(KeySecurityLevel.UNKNOWN)
    return DeviceIdentityInfo(id, publicKey, secLevel)
  }

  private fun encodeBase64(bytes: ByteArray): String {
    return try {
      java.util.Base64.getEncoder().encodeToString(bytes)
    } catch (_: Throwable) {
      Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
  }

  private fun decodeBase64(input: String): ByteArray {
    return try {
      java.util.Base64.getDecoder().decode(input)
    } catch (_: Throwable) {
      Base64.decode(input, Base64.NO_WRAP)
    }
  }

  companion object {
    const val ALIAS = "peykr.device.identity.v1"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
  }
}
