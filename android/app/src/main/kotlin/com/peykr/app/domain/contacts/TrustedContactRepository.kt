package com.peykr.app.domain.contacts

import kotlinx.coroutines.flow.Flow

interface TrustedContactRepository {
  val contacts: Flow<List<TrustedContact>>
  suspend fun getById(id: String): TrustedContact?
  suspend fun add(contact: TrustedContact)
  suspend fun update(contact: TrustedContact)
  suspend fun delete(id: String)
}
