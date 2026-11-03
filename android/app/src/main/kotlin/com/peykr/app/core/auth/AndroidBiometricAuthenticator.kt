package com.peykr.app.core.auth

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class AndroidBiometricAuthenticator(
  private val context: Context,
  private val activityProvider: () -> FragmentActivity?,
) : BiometricAuthenticator {

  override fun checkAvailability(allowDeviceCredential: Boolean): BiometricAvailability {
    val biometricManager = BiometricManager.from(context)
    val authenticators = resolveAuthenticators(allowDeviceCredential)

    return when (biometricManager.canAuthenticate(authenticators)) {
      BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
      BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
      BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.HARDWARE_UNAVAILABLE
      BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
      BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricAvailability.SECURITY_UPDATE_REQUIRED
      BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> BiometricAvailability.UNSUPPORTED
      else -> BiometricAvailability.UNSUPPORTED
    }
  }

  override suspend fun authenticate(config: AuthPromptConfig): AuthenticationResult =
    withContext<AuthenticationResult>(Dispatchers.Main) {
      val activity = activityProvider()
      if (activity == null || activity.isFinishing || activity.isDestroyed) {
        return@withContext AuthenticationResult.TemporarySystemError(
          IllegalStateException("No active valid FragmentActivity available to display BiometricPrompt")
        )
      }

      val executor = ContextCompat.getMainExecutor(activity)

      suspendCancellableCoroutine<AuthenticationResult> { continuation ->
        val prompt = BiometricPrompt(
          activity,
          executor,
          object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
              if (continuation.isActive) {
                continuation.resume(AuthenticationResult.Success(System.currentTimeMillis()))
              }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
              if (continuation.isActive) {
                val typedResult = when (errorCode) {
                  BiometricPrompt.ERROR_USER_CANCELED,
                  BiometricPrompt.ERROR_NEGATIVE_BUTTON -> AuthenticationResult.UserCancellation
                  BiometricPrompt.ERROR_NO_BIOMETRICS -> AuthenticationResult.NoBiometricEnrollment
                  BiometricPrompt.ERROR_HW_NOT_PRESENT,
                  BiometricPrompt.ERROR_HW_UNAVAILABLE -> AuthenticationResult.HardwareUnavailable
                  BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> AuthenticationResult.DeviceCredentialUnavailable
                  else -> AuthenticationResult.Failure(errorCode, errString.toString())
                }
                continuation.resume(typedResult)
              }
            }

            override fun onAuthenticationFailed() {
              // Authentication attempt was unrecognized; prompt remains active for retry.
            }
          }
        )

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
          .setTitle(config.title)

        config.subtitle?.let { promptInfoBuilder.setSubtitle(it) }
        config.description?.let { promptInfoBuilder.setDescription(it) }

        if (config.allowDeviceCredential) {
          promptInfoBuilder.setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
          )
        } else {
          promptInfoBuilder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
          promptInfoBuilder.setNegativeButtonText(config.negativeButtonText ?: "İptal")
        }

        val promptInfo = promptInfoBuilder.build()
        prompt.authenticate(promptInfo)

        continuation.invokeOnCancellation {
          prompt.cancelAuthentication()
        }
      }
    }

  private fun resolveAuthenticators(allowDeviceCredential: Boolean): Int =
    if (allowDeviceCredential) {
      BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    } else {
      BiometricManager.Authenticators.BIOMETRIC_STRONG
    }
}
