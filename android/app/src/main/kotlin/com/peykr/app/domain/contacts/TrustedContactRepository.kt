package com.peykr.app.domain.contacts

import com.peykr.app.data.contacts.DatabaseState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface TrustedContactRepository {
  val contacts: Flow<List<TrustedContact>>
  val initializationState: Flow<DatabaseState> get() = flowOf(DatabaseState.READY)
  val diagnosticDetails: Flow<String?> get() = flowOf(null)
  fun retry() {}
  suspend fun getById(id: String): TrustedContact?
  suspend fun add(contact: TrustedContact)
  suspend fun update(contact: TrustedContact)
  suspend fun delete(id: String)
  suspend fun block(id: String) {
    val existing = getById(id) ?: return
    update(existing.copy(pairingStatus = PairingStatus.BLOCKED))
  }
  suspend fun revoke(id: String) {
    val existing = getById(id) ?: return
    update(existing.copy(pairingStatus = PairingStatus.REVOKED, revokedAtEpochMillis = System.currentTimeMillis()))
  }
}
