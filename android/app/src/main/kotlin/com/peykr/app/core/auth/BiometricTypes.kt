package com.peykr.app.core.auth

import java.util.UUID

enum class BiometricAvailability {
  AVAILABLE,
  NO_HARDWARE,
  HARDWARE_UNAVAILABLE,
  NONE_ENROLLED,
  SECURITY_UPDATE_REQUIRED,
  UNSUPPORTED
}

enum class RequiredAuthMethod {
  BIOMETRIC_OR_PIN,
  PIN_REQUIRED,
  SETUP_PIN_REQUIRED
}

sealed interface AuthenticationResult {
  data class Success(val timestampEpochMillis: Long = System.currentTimeMillis()) : AuthenticationResult
  data class Failure(val errorCode: Int? = null, val message: String) : AuthenticationResult
  data object UserCancellation : AuthenticationResult
  data object NoBiometricEnrollment : AuthenticationResult
  data object HardwareUnavailable : AuthenticationResult
  data object DeviceCredentialUnavailable : AuthenticationResult
  data object UnsupportedAuthentication : AuthenticationResult
  data class TemporarySystemError(val cause: Throwable? = null) : AuthenticationResult
  data class PinResult(val verification: PinVerificationResult) : AuthenticationResult
}

data class AuthPromptConfig(
  val title: String,
  val subtitle: String? = null,
  val description: String? = null,
  val negativeButtonText: String? = null,
  val allowDeviceCredential: Boolean = true,
)

enum class SecurityGateScope {
  APPROVE_TRUSTED_DEVICE,
  CONFIRM_PAIRING_REQUEST,
  REVOKE_TRUSTED_CONTACT,
  START_LOCATION_SHARING,
  VIEW_SECURITY_DETAILS,
  EXPORT_SECURITY_INFO
}

@ConsistentCopyVisibility
data class AuthorizationToken internal constructor(
  val scope: SecurityGateScope,
  val grantedAtEpochMillis: Long,
  val validDurationMillis: Long = DEFAULT_VALIDITY_DURATION_MILLIS,
  val tokenValue: String = UUID.randomUUID().toString(),
) {
  fun isExpired(nowMillis: Long): Boolean =
    (nowMillis - grantedAtEpochMillis) > validDurationMillis

  fun isValidFor(requiredScope: SecurityGateScope, nowMillis: Long): Boolean =
    scope == requiredScope && !isExpired(nowMillis)

  companion object {
    const val DEFAULT_VALIDITY_DURATION_MILLIS = 30_000L
  }
}
