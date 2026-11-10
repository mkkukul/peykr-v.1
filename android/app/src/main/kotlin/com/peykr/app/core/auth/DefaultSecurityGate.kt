package com.peykr.app.core.auth

import java.util.concurrent.ConcurrentHashMap

class DefaultSecurityGate(
  private val authenticator: BiometricAuthenticator,
  private val pinManager: PinSecurityManager? = null,
  private val tokenValidityDurationMillis: Long = AuthorizationToken.DEFAULT_VALIDITY_DURATION_MILLIS,
  private val timeProvider: () -> Long = { System.nanoTime() / 1_000_000L },
) : SecurityGate {

  private val activeTokens = ConcurrentHashMap<String, AuthorizationToken>()

  override fun canAuthenticate(allowDeviceCredential: Boolean): BiometricAvailability {
    return authenticator.checkAvailability(allowDeviceCredential)
  }

  override fun isPinConfigured(): Boolean {
    return pinManager?.isPinConfigured() == true
  }

  override fun setupPin(pin: String): Result<Unit> {
    val manager = pinManager ?: return Result.failure(SecurityGateException.PinNotConfigured())
    return manager.setupPin(pin)
  }

  override fun getRequiredAuthMethod(): RequiredAuthMethod {
    val bioAvailability = authenticator.checkAvailability(allowDeviceCredential = false)
    val pinConfigured = isPinConfigured()

    return when {
      bioAvailability == BiometricAvailability.AVAILABLE -> RequiredAuthMethod.BIOMETRIC_OR_PIN
      pinConfigured -> RequiredAuthMethod.PIN_REQUIRED
      else -> RequiredAuthMethod.SETUP_PIN_REQUIRED
    }
  }

  override suspend fun authorize(scope: SecurityGateScope, config: AuthPromptConfig): Result<AuthorizationToken> {
    val bioAvailability = authenticator.checkAvailability(allowDeviceCredential = config.allowDeviceCredential)
    if (bioAvailability == BiometricAvailability.NO_HARDWARE || bioAvailability == BiometricAvailability.NONE_ENROLLED) {
      if (!isPinConfigured()) {
        return Result.failure(SecurityGateException.PinNotConfigured())
      }
    }

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

  override suspend fun authorizeWithPin(scope: SecurityGateScope, pin: String): Result<AuthorizationToken> {
    val manager = pinManager ?: return Result.failure(SecurityGateException.PinNotConfigured())
    if (!manager.isPinConfigured()) {
      return Result.failure(SecurityGateException.PinNotConfigured())
    }

    return when (val verification = manager.verifyPin(pin)) {
      is PinVerificationResult.Success -> {
        val token = AuthorizationToken(
          scope = scope,
          grantedAtEpochMillis = timeProvider(),
          validDurationMillis = tokenValidityDurationMillis,
        )
        activeTokens[token.tokenValue] = token
        Result.success(token)
      }
      else -> {
        Result.failure(SecurityGateException.PinVerificationFailed(verification))
      }
    }
  }

  override suspend fun authorizeDirect(scope: SecurityGateScope): Result<AuthorizationToken> {
    val token = AuthorizationToken(
      scope = scope,
      grantedAtEpochMillis = timeProvider(),
      validDurationMillis = tokenValidityDurationMillis,
    )
    activeTokens[token.tokenValue] = token
    return Result.success(token)
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
