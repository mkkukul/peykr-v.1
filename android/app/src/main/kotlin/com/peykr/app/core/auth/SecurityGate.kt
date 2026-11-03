package com.peykr.app.core.auth

sealed class SecurityGateException(message: String, cause: Throwable? = null) : Exception(message, cause) {
  class AuthenticationDenied(val result: AuthenticationResult) :
    SecurityGateException("Authentication was not granted: $result")
  class InvalidToken(val messageText: String) : SecurityGateException(messageText)
  class ExpiredToken(val scope: SecurityGateScope) :
    SecurityGateException("Authorization token for scope $scope has expired")
  class ScopeMismatch(val expected: SecurityGateScope, val actual: SecurityGateScope) :
    SecurityGateException("Authorization token scope mismatch: expected $expected but got $actual")
}

interface SecurityGate {
  fun canAuthenticate(allowDeviceCredential: Boolean = true): BiometricAvailability
  suspend fun authorize(scope: SecurityGateScope, config: AuthPromptConfig): Result<AuthorizationToken>
  fun verifyAuthorization(token: AuthorizationToken?, requiredScope: SecurityGateScope): Boolean
  fun consumeAuthorization(token: AuthorizationToken?): Boolean
  suspend fun <T> withAuthorization(
    scope: SecurityGateScope,
    config: AuthPromptConfig,
    action: suspend (AuthorizationToken) -> T,
  ): Result<T>
}
