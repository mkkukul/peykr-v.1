package com.peykr.app.core.identity

import java.security.PublicKey
import kotlinx.coroutines.flow.StateFlow

enum class KeySecurityLevel {
  STRONGBOX,
  TRUSTED_ENVIRONMENT,
  SOFTWARE,
  UNKNOWN,
}

data class DeviceIdentityInfo(
  val id: String,
  val publicKey: String,
  val securityLevel: KeySecurityLevel = KeySecurityLevel.UNKNOWN,
)

enum class IdentityState {
  INITIALIZING,
  READY,
  KEYSTORE_UNAVAILABLE,
  IDENTITY_INVALIDATED,
  GENERATION_FAILED
}

sealed interface DeviceIdentityState {
  val state: IdentityState

  data object Initializing : DeviceIdentityState {
    override val state: IdentityState = IdentityState.INITIALIZING
  }

  data class Ready(val identity: DeviceIdentityInfo) : DeviceIdentityState {
    override val state: IdentityState = IdentityState.READY
  }

  data class KeystoreUnavailable(val cause: Throwable) : DeviceIdentityState {
    override val state: IdentityState = IdentityState.KEYSTORE_UNAVAILABLE
  }

  data class IdentityInvalidated(val message: String, val cause: Throwable? = null) : DeviceIdentityState {
    override val state: IdentityState = IdentityState.IDENTITY_INVALIDATED
  }

  data class GenerationFailed(val cause: Throwable) : DeviceIdentityState {
    override val state: IdentityState = IdentityState.GENERATION_FAILED
  }
}

sealed interface DeviceIdentityResult {
  data class Success(val identity: DeviceIdentityInfo) : DeviceIdentityResult
  data class KeystoreUnavailable(val cause: Throwable) : DeviceIdentityResult
  data class IdentityInvalidated(val message: String, val cause: Throwable? = null) : DeviceIdentityResult
  data class GenerationFailed(val cause: Throwable) : DeviceIdentityResult
}

fun DeviceIdentityResult.toState(): DeviceIdentityState = when (this) {
  is DeviceIdentityResult.Success -> DeviceIdentityState.Ready(identity)
  is DeviceIdentityResult.KeystoreUnavailable -> DeviceIdentityState.KeystoreUnavailable(cause)
  is DeviceIdentityResult.IdentityInvalidated -> DeviceIdentityState.IdentityInvalidated(message, cause)
  is DeviceIdentityResult.GenerationFailed -> DeviceIdentityState.GenerationFailed(cause)
}

fun DeviceIdentityResult.getOrNull(): DeviceIdentityInfo? = (this as? DeviceIdentityResult.Success)?.identity

fun DeviceIdentityResult.getOrThrow(): DeviceIdentityInfo = when (this) {
  is DeviceIdentityResult.Success -> identity
  is DeviceIdentityResult.KeystoreUnavailable -> throw IllegalStateException("Keystore unavailable", cause)
  is DeviceIdentityResult.IdentityInvalidated -> throw IllegalStateException("Identity invalidated: $message", cause)
  is DeviceIdentityResult.GenerationFailed -> throw IllegalStateException("Identity generation failed", cause)
}

fun DeviceIdentityState.identityOrNull(): DeviceIdentityInfo? = (this as? DeviceIdentityState.Ready)?.identity

sealed class DeviceIdentityException(message: String, cause: Throwable? = null) : Exception(message, cause) {
  class KeyNotReady(message: String) : DeviceIdentityException(message)
  class KeystoreUnavailable(message: String, cause: Throwable? = null) : DeviceIdentityException(message, cause)
  class IdentityInvalidated(message: String, cause: Throwable? = null) : DeviceIdentityException(message, cause)
}

interface DeviceIdentity {
  val identityState: StateFlow<DeviceIdentityState>
  fun initialize()
  fun retry()
  fun getOrCreate(): DeviceIdentityResult
  fun sign(transcript: ByteArray): Result<ByteArray>
  fun verify(publicKeyBase64: String, transcript: ByteArray, signature: ByteArray): Boolean
  fun verify(publicKey: PublicKey, transcript: ByteArray, signature: ByteArray): Boolean
  fun close() {}
}
