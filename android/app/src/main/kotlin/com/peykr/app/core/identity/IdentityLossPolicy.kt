package com.peykr.app.core.identity

/** Installation-specific identity; clearing data or uninstalling requires pairing again. */
object IdentityLossPolicy {
  /**
   * Explains that identities are installation-specific and backed by the platform Keystore.
   * If an application is uninstalled or user data is cleared, existing pairings are void
   * and a fresh identity must be generated.
   * However, if an identity key entry is present in Keystore but inaccessible or invalidated,
   * it must NOT be silently replaced or deleted.
   */
  const val POLICY_NAME = "INSTALLATION_BOUND_IDENTITY_V1"

  fun canGenerateNewIdentity(keyExistsInStore: Boolean): Boolean {
    return !keyExistsInStore
  }
}
