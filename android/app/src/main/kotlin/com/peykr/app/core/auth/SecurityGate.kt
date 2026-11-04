package com.peykr.app.core.auth

sealed class SecurityGateException(message: String, cause: Throwable? = null) : Exception(message, cause) {
  class AuthenticationDenied(val result: AuthenticationResult) :
    SecurityGateException("Authentication was not granted: $result")
  class PinNotConfigured :
    SecurityGateException("Application PIN is not configured. Setup must be completed.")
  class PinVerificationFailed(val result: PinVerificationResult) :
    SecurityGateException("PIN verification failed: $result")
  class InvalidToken(val messageText: String) : SecurityGateException(messageText)
  class ExpiredToken(val scope: SecurityGateScope) :
    SecurityGateException("Authorization token for scope $scope has expired")
  class ScopeMismatch(val expected: SecurityGateScope, val actual: SecurityGateScope) :
    SecurityGateException("Authorization token scope mismatch: expected $expected but got $actual")
}

interface SecurityGate {
  fun canAuthenticate(allowDeviceCredential: Boolean = true): BiometricAvailability
  fun isPinConfigured(): Boolean
  fun getRequiredAuthMethod(): RequiredAuthMethod
  suspend fun authorize(scope: SecurityGateScope, config: AuthPromptConfig): Result<AuthorizationToken>
  suspend fun authorizeWithPin(scope: SecurityGateScope, pin: String): Result<AuthorizationToken>
  fun verifyAuthorization(token: AuthorizationToken?, requiredScope: SecurityGateScope): Boolean
  fun consumeAuthorization(token: AuthorizationToken?): Boolean
  suspend fun <T> withAuthorization(
    scope: SecurityGateScope,
    config: AuthPromptConfig,
    action: suspend (AuthorizationToken) -> T,
  ): Result<T>
}
