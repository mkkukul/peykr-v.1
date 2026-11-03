package com.peykr.app.core.auth

import java.util.concurrent.ConcurrentHashMap

class DefaultSecurityGate(
  private val authenticator: BiometricAuthenticator,
  private val tokenValidityDurationMillis: Long = AuthorizationToken.DEFAULT_VALIDITY_DURATION_MILLIS,
  private val timeProvider: () -> Long = { System.nanoTime() / 1_000_000L },
) : SecurityGate {

  private val activeTokens = ConcurrentHashMap<String, AuthorizationToken>()

  override fun canAuthenticate(allowDeviceCredential: Boolean): BiometricAvailability {
    return authenticator.checkAvailability(allowDeviceCredential)
  }

  override suspend fun authorize(scope: SecurityGateScope, config: AuthPromptConfig): Result<AuthorizationToken> {
    return when (val authResult = authenticator.authenticate(config)) {
      is AuthenticationResult.Success -> {
        val token = AuthorizationToken(
          scope = scope,
          grantedAtEpochMillis = timeProvider(),
          validDurationMillis = tokenValidityDurationMillis,
        )
        activeTokens[token.tokenValue] = token
        Result.success(token)
      }
      else -> {
        Result.failure(SecurityGateException.AuthenticationDenied(authResult))
      }
    }
  }

  override fun verifyAuthorization(token: AuthorizationToken?, requiredScope: SecurityGateScope): Boolean {
    if (token == null) return false
    val registered = activeTokens[token.tokenValue] ?: return false
    val now = timeProvider()
    if (registered.isExpired(now)) {
      activeTokens.remove(token.tokenValue)
      return false
    }
    return registered.isValidFor(requiredScope, now)
  }

  override fun consumeAuthorization(token: AuthorizationToken?): Boolean {
    if (token == null) return false
    return activeTokens.remove(token.tokenValue) != null
  }

  override suspend fun <T> withAuthorization(
    scope: SecurityGateScope,
    config: AuthPromptConfig,
    action: suspend (AuthorizationToken) -> T,
  ): Result<T> {
    val authResult = authorize(scope, config)
    val token = authResult.getOrElse { return Result.failure(it) }

    return runCatching {
      if (!verifyAuthorization(token, scope)) {
        throw SecurityGateException.ExpiredToken(scope)
      }
      try {
        action(token)
      } finally {
        consumeAuthorization(token)
      }
    }
  }
}
