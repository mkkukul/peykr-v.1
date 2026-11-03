package com.peykr.app.domain.contacts

enum class PairingStatus { NOT_PAIRED, PAIRED, REVOKED }

data class TrustedContact(
  val id: String,
  val displayName: String,
  val publicIdentity: String? = null,
  val pairingStatus: PairingStatus = PairingStatus.NOT_PAIRED,
  val createdAtEpochMillis: Long,
  val revokedAtEpochMillis: Long? = null,
)
