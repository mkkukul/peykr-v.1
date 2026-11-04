package com.peykr.app.core.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSecurityGateTest {

  private val promptConfig = AuthPromptConfig(
    title = "Test Auth Prompt",
    description = "Test Authentication Description",
  )

  @Test
  fun successfulAuthentication_returnsSuccessToken() = runTest {
    val fakeAuthenticator = FakeBiometricAuthenticator(
      authResult = AuthenticationResult.Success(1000L)
    )
    val securityGate = DefaultSecurityGate(fakeAuthenticator)

    val tokenResult = securityGate.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig)

    assertTrue(tokenResult.isSuccess)
    val token = tokenResult.getOrThrow()
    assertEquals(SecurityGateScope.APPROVE_TRUSTED_DEVICE, token.scope)
    assertTrue(securityGate.verifyAuthorization(token, SecurityGateScope.APPROVE_TRUSTED_DEVICE))
  }

  @Test
  fun authenticationFailure_returnsFailureResult() = runTest {
    val fakeAuthenticator = FakeBiometricAuthenticator(
      authResult = AuthenticationResult.Failure(100, "Sensor timeout")
    )
    val securityGate = DefaultSecurityGate(fakeAuthenticator)

    val tokenResult = securityGate.authorize(SecurityGateScope.CONFIRM_PAIRING_REQUEST, promptConfig)

    assertTrue(tokenResult.isFailure)
    val exception = tokenResult.exceptionOrNull() as SecurityGateException.AuthenticationDenied
    val failure = exception.result as AuthenticationResult.Failure
    assertEquals("Sensor timeout", failure.message)
    assertEquals(100, failure.errorCode)
  }

  @Test
  fun userCancellation_returnsFailureWithUserCancellation() = runTest {
    val fakeAuthenticator = FakeBiometricAuthenticator(
      authResult = AuthenticationResult.UserCancellation
    )
    val securityGate = DefaultSecurityGate(fakeAuthenticator)

    val tokenResult = securityGate.authorize(SecurityGateScope.REVOKE_TRUSTED_CONTACT, promptConfig)

    assertTrue(tokenResult.isFailure)
    val exception = tokenResult.exceptionOrNull() as SecurityGateException.AuthenticationDenied
    assertEquals(AuthenticationResult.UserCancellation, exception.result)
  }

  @Test
  fun missingBiometricEnrollment_authenticatorReportsNoneEnrolled() = runTest {
    val fakeAuthenticator = FakeBiometricAuthenticator(
      availability = BiometricAvailability.NONE_ENROLLED,
      authResult = AuthenticationResult.NoBiometricEnrollment
    )
    val securityGate = DefaultSecurityGate(fakeAuthenticator)

    assertEquals(BiometricAvailability.NONE_ENROLLED, securityGate.canAuthenticate())

    val result = securityGate.authorize(SecurityGateScope.START_LOCATION_SHARING, promptConfig)
    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull()
    assertTrue(exception is SecurityGateException)
  }

  @Test
  fun unsupportedHardware_reportsNoHardwareAndFailsAuth() = runTest {
    val fakeAuthenticator = FakeBiometricAuthenticator(
      availability = BiometricAvailability.NO_HARDWARE,
      authResult = AuthenticationResult.HardwareUnavailable
    )
    val securityGate = DefaultSecurityGate(fakeAuthenticator)

    assertEquals(BiometricAvailability.NO_HARDWARE, securityGate.canAuthenticate())

    val result = securityGate.authorize(SecurityGateScope.VIEW_SECURITY_DETAILS, promptConfig)
    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull()
    assertTrue(exception is SecurityGateException)
  }

  @Test
  fun deviceCredentialFallback_evaluatesAvailabilityWithDeviceCredential() {
    val fakeAuthenticator = FakeBiometricAuthenticator()
    val securityGate = DefaultSecurityGate(fakeAuthenticator)

    securityGate.canAuthenticate(allowDeviceCredential = true)
    assertEquals(true, fakeAuthenticator.lastCheckAllowedCredential)

    securityGate.canAuthenticate(allowDeviceCredential = false)
    assertEquals(false, fakeAuthenticator.lastCheckAllowedCredential)
  }

  @Test
  fun securityGateDenial_withoutAuthorizationToken_rejectsVerification() {
    val fakeAuthenticator = FakeBiometricAuthenticator()
    val securityGate = DefaultSecurityGate(fakeAuthenticator)

    assertFalse(securityGate.verifyAuthorization(null, SecurityGateScope.APPROVE_TRUSTED_DEVICE))
  }

  @Test
  fun securityGateAcceptance_afterSuccessfulAuthorization_allowsActionExecution() = runTest {
    val fakeAuthenticator = FakeBiometricAuthenticator(
      authResult = AuthenticationResult.Success()
    )
    val securityGate = DefaultSecurityGate(fakeAuthenticator)

    var actionExecuted = false
    val result = securityGate.withAuthorization(
      scope = SecurityGateScope.APPROVE_TRUSTED_DEVICE,
      config = promptConfig,
    ) { token ->
      actionExecuted = true
      assertEquals(SecurityGateScope.APPROVE_TRUSTED_DEVICE, token.scope)
      "action-success"
    }

    assertTrue(result.isSuccess)
    assertTrue(actionExecuted)
    assertEquals("action-success", result.getOrThrow())
  }

  @Test
  fun authorizationExpiration_deniesVerificationAfterValidDuration() = runTest {
    var currentTime = 10_000L
    val fakeAuthenticator = FakeBiometricAuthenticator(
      authResult = AuthenticationResult.Success()
    )
    val securityGate = DefaultSecurityGate(
      authenticator = fakeAuthenticator,
      tokenValidityDurationMillis = 5000L,
      timeProvider = { currentTime },
    )

    val token = securityGate.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig).getOrThrow()

    // Valid immediately after creation
    assertTrue(securityGate.verifyAuthorization(token, SecurityGateScope.APPROVE_TRUSTED_DEVICE))

    // Valid within window (4999ms later)
    currentTime += 4999L
    assertTrue(securityGate.verifyAuthorization(token, SecurityGateScope.APPROVE_TRUSTED_DEVICE))

    // Expired past window (total +5001ms)
    currentTime += 2L
    assertFalse("Token must expire after configured duration", securityGate.verifyAuthorization(token, SecurityGateScope.APPROVE_TRUSTED_DEVICE))
  }

  @Test
  fun authorizationScopeRestriction_rejectsTokenWithDifferentScope() = runTest {
    val fakeAuthenticator = FakeBiometricAuthenticator(
      authResult = AuthenticationResult.Success()
    )
    val securityGate = DefaultSecurityGate(fakeAuthenticator)

    val token = securityGate.authorize(SecurityGateScope.VIEW_SECURITY_DETAILS, promptConfig).getOrThrow()

    // Valid for approved scope
    assertTrue(securityGate.verifyAuthorization(token, SecurityGateScope.VIEW_SECURITY_DETAILS))

    // Must be rejected for all other scopes
    assertFalse(securityGate.verifyAuthorization(token, SecurityGateScope.REVOKE_TRUSTED_CONTACT))
    assertFalse(securityGate.verifyAuthorization(token, SecurityGateScope.APPROVE_TRUSTED_DEVICE))
    assertFalse(securityGate.verifyAuthorization(token, SecurityGateScope.CONFIRM_PAIRING_REQUEST))
    assertFalse(securityGate.verifyAuthorization(token, SecurityGateScope.START_LOCATION_SHARING))
    assertFalse(securityGate.verifyAuthorization(token, SecurityGateScope.EXPORT_SECURITY_INFO))
  }

  @Test
  fun forgedToken_createdOutsideGate_isRejected() = runTest {
    val fakeAuthenticator = FakeBiometricAuthenticator()
    val securityGate = DefaultSecurityGate(fakeAuthenticator)

    val forgedToken = AuthorizationToken(
      scope = SecurityGateScope.APPROVE_TRUSTED_DEVICE,
      grantedAtEpochMillis = System.currentTimeMillis(),
      validDurationMillis = 60_000L,
    )

    assertFalse(
      "SecurityGate must reject forged tokens not issued by authorize()",
      securityGate.verifyAuthorization(forgedToken, SecurityGateScope.APPROVE_TRUSTED_DEVICE)
    )
  }

  @Test
  fun tokenConsumption_preventsTokenReplayAfterActionExecution() = runTest {
    val fakeAuthenticator = FakeBiometricAuthenticator(
      authResult = AuthenticationResult.Success()
    )
    val securityGate = DefaultSecurityGate(fakeAuthenticator)

    var capturedToken: AuthorizationToken? = null
    val result = securityGate.withAuthorization(
      scope = SecurityGateScope.APPROVE_TRUSTED_DEVICE,
      config = promptConfig,
    ) { token ->
      capturedToken = token
      "success"
    }

    assertTrue(result.isSuccess)
    assertFalse(
      "Consumed token must not be verifiable after operation completes",
      securityGate.verifyAuthorization(capturedToken, SecurityGateScope.APPROVE_TRUSTED_DEVICE)
    )
  }

  @Test
  fun manualTokenConsumption_invalidatesToken() = runTest {
    val fakeAuthenticator = FakeBiometricAuthenticator(
      authResult = AuthenticationResult.Success()
    )
    val securityGate = DefaultSecurityGate(fakeAuthenticator)

    val token = securityGate.authorize(SecurityGateScope.REVOKE_TRUSTED_CONTACT, promptConfig).getOrThrow()
    assertTrue(securityGate.verifyAuthorization(token, SecurityGateScope.REVOKE_TRUSTED_CONTACT))

    val consumed = securityGate.consumeAuthorization(token)
    assertTrue(consumed)
    assertFalse(
      "Manually consumed token must no longer be valid",
      securityGate.verifyAuthorization(token, SecurityGateScope.REVOKE_TRUSTED_CONTACT)
    )
  }

  @Test
  fun noHardware_withoutPinConfigured_requiresSetupAndDeniesAuth() = runTest {
    val fakeAuthenticator = FakeBiometricAuthenticator(
      availability = BiometricAvailability.NO_HARDWARE,
      authResult = AuthenticationResult.HardwareUnavailable
    )
    val securityGate = DefaultSecurityGate(fakeAuthenticator)

    assertEquals(RequiredAuthMethod.SETUP_PIN_REQUIRED, securityGate.getRequiredAuthMethod())

    val result = securityGate.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig)
    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is SecurityGateException.PinNotConfigured)
  }

  @Test
  fun noHardware_withPinConfigured_requiresPin_andAuthorizesSuccessfully() = runTest {
    val fakeAuthenticator = FakeBiometricAuthenticator(
      availability = BiometricAvailability.NO_HARDWARE,
      authResult = AuthenticationResult.HardwareUnavailable
    )
    val pinStorage = InMemoryPinStorage()
    val pinManager = PinSecurityManager(pinStorage)
    pinManager.setupPin("1234").getOrThrow()

    val securityGate = DefaultSecurityGate(fakeAuthenticator, pinManager)

    assertEquals(RequiredAuthMethod.PIN_REQUIRED, securityGate.getRequiredAuthMethod())

    val tokenResult = securityGate.authorizeWithPin(SecurityGateScope.APPROVE_TRUSTED_DEVICE, "1234")
    assertTrue(tokenResult.isSuccess)
    val token = tokenResult.getOrThrow()
    assertTrue(securityGate.verifyAuthorization(token, SecurityGateScope.APPROVE_TRUSTED_DEVICE))
  }

  @Test
  fun noHardware_withWrongPin_failsAuthorization() = runTest {
    val fakeAuthenticator = FakeBiometricAuthenticator(
      availability = BiometricAvailability.NO_HARDWARE,
      authResult = AuthenticationResult.HardwareUnavailable
    )
    val pinStorage = InMemoryPinStorage()
    val pinManager = PinSecurityManager(pinStorage)
    pinManager.setupPin("1234").getOrThrow()

    val securityGate = DefaultSecurityGate(fakeAuthenticator, pinManager)

    val tokenResult = securityGate.authorizeWithPin(SecurityGateScope.APPROVE_TRUSTED_DEVICE, "9999")
    assertTrue(tokenResult.isFailure)
    val ex = tokenResult.exceptionOrNull()
    assertTrue(ex is SecurityGateException.PinVerificationFailed)
  }

  @Test
  fun noneEnrolled_withoutPinConfigured_requiresSetup() = runTest {
    val fakeAuthenticator = FakeBiometricAuthenticator(
      availability = BiometricAvailability.NONE_ENROLLED,
      authResult = AuthenticationResult.NoBiometricEnrollment
    )
    val securityGate = DefaultSecurityGate(fakeAuthenticator)

    assertEquals(RequiredAuthMethod.SETUP_PIN_REQUIRED, securityGate.getRequiredAuthMethod())
    val result = securityGate.authorize(SecurityGateScope.APPROVE_TRUSTED_DEVICE, promptConfig)
    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is SecurityGateException.PinNotConfigured)
  }

  @Test
  fun noneEnrolled_withPinConfigured_authorizesWithPin() = runTest {
    val fakeAuthenticator = FakeBiometricAuthenticator(
      availability = BiometricAvailability.NONE_ENROLLED,
      authResult = AuthenticationResult.NoBiometricEnrollment
    )
    val pinStorage = InMemoryPinStorage()
    val pinManager = PinSecurityManager(pinStorage)
    pinManager.setupPin("5678").getOrThrow()

    val securityGate = DefaultSecurityGate(fakeAuthenticator, pinManager)

    assertEquals(RequiredAuthMethod.PIN_REQUIRED, securityGate.getRequiredAuthMethod())
    val tokenResult = securityGate.authorizeWithPin(SecurityGateScope.APPROVE_TRUSTED_DEVICE, "5678")
    assertTrue(tokenResult.isSuccess)
    assertTrue(securityGate.verifyAuthorization(tokenResult.getOrThrow(), SecurityGateScope.APPROVE_TRUSTED_DEVICE))
  }
}

private class FakeBiometricAuthenticator(
  var availability: BiometricAvailability = BiometricAvailability.AVAILABLE,
  var authResult: AuthenticationResult = AuthenticationResult.Success(),
) : BiometricAuthenticator {

  var lastCheckAllowedCredential: Boolean? = null
  var lastPromptConfig: AuthPromptConfig? = null

  override fun checkAvailability(allowDeviceCredential: Boolean): BiometricAvailability {
    lastCheckAllowedCredential = allowDeviceCredential
    return availability
  }

  override suspend fun authenticate(config: AuthPromptConfig): AuthenticationResult {
    lastPromptConfig = config
    return authResult
  }
}
