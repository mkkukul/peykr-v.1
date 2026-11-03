package com.peykr.app.core.identity

data class DeviceIdentityInfo(val id: String, val publicKey: String)

sealed interface DeviceIdentityResult {
  data class Success(val identity: DeviceIdentityInfo) : DeviceIdentityResult
  data class KeystoreUnavailable(val cause: Throwable) : DeviceIdentityResult
  data class IdentityInvalidated(val message: String, val cause: Throwable? = null) : DeviceIdentityResult
  data class GenerationFailed(val cause: Throwable) : DeviceIdentityResult
}

fun DeviceIdentityResult.getOrNull(): DeviceIdentityInfo? = (this as? DeviceIdentityResult.Success)?.identity

fun DeviceIdentityResult.getOrThrow(): DeviceIdentityInfo = when (this) {
  is DeviceIdentityResult.Success -> identity
  is DeviceIdentityResult.KeystoreUnavailable -> throw IllegalStateException("Keystore unavailable", cause)
  is DeviceIdentityResult.IdentityInvalidated -> throw IllegalStateException("Identity invalidated: $message", cause)
  is DeviceIdentityResult.GenerationFailed -> throw IllegalStateException("Identity generation failed", cause)
}

interface DeviceIdentity {
  fun getOrCreate(): DeviceIdentityResult
}
