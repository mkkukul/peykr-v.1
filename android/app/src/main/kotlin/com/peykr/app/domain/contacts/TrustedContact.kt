package com.peykr.app.domain.contacts

enum class PairingStatus {
  NOT_PAIRED,
  PAIRING,
  PAIRED,
  FAILED,
  REVOKED,
  BLOCKED;

  companion object {
    val PENDING = PAIRING
  }
}

data class TrustedContact(
  val id: String,
  val displayName: String,
  val publicIdentity: String? = null,
  val pairingStatus: PairingStatus = PairingStatus.NOT_PAIRED,
  val createdAtEpochMillis: Long,
  val revokedAtEpochMillis: Long? = null,
)
