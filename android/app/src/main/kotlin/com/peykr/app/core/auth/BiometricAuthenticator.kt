package com.peykr.app.core.auth

interface BiometricAuthenticator {
  fun checkAvailability(allowDeviceCredential: Boolean = true): BiometricAvailability
  suspend fun authenticate(config: AuthPromptConfig): AuthenticationResult
}
