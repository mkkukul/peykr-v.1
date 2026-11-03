package com.peykr.app.core.auth

/**
 * Architectural specification and KeyStore provider for biometric-bound cryptographic operations.
 *
 * CRITICAL CRYPTOGRAPHIC ISOLATION PRINCIPLES:
 * 1. The persistent device identity key (alias: "peykr.device.identity.v1") MUST NEVER be
 *    biometric-bound (setUserAuthenticationRequired = true). Biometric changes on Android
 *    (e.g., enrolling a new finger or changing lock screen credentials) permanently invalidate
 *    biometric-bound keys. If the device identity key were invalidated, all peer pairings
 *    and cryptographic identity signatures would be irreparably lost.
 * 2. The SQLCipher database wrapping key (alias: "peykr.database.wrap.v1") MUST NEVER be
 *    biometric-bound, ensuring background maintenance and secure persistence without UI prompts.
 * 3. When cryptographic biometric gating is required for high-sensitivity operations (such as
 *    authorizing device pairing approvals or exporting sensitive audit material), an independent
 *    biometric-bound key (alias: [BIOMETRIC_GATE_ALIAS]) is used to unlock a [BiometricPrompt.CryptoObject].
 * 4. If the biometric gate key is invalidated due to new biometric enrollment, it can be safely
 *    regenerated without affecting the device identity or existing database contents.
 */
object BiometricGateKeyStore {
  const val BIOMETRIC_GATE_ALIAS = "peykr.biometric.gate.v1"
  const val GATE_KEY_ALGORITHM = "EC"
  const val GATE_CURVE = "secp256r1"
}
